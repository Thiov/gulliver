#!/usr/bin/env bash
# Runtime self-test for one target: boots a dedicated server (and then a
# client that joins the generated world) with -Dgulliver.selftest=true and
# waits for the PASS/FAIL line. Usage:
#   scripts/selftest.sh <mc-version> <loader> [server|client|both]
set -u
cd "$(dirname "$0")/.."
MC="$1"; LOADER="$2"; WHAT="${3:-both}"
DIR="versions/$MC/$LOADER"
RUN="$DIR/run"
LOG_DIR="build/selftest"
mkdir -p "$RUN" "$LOG_DIR"
echo "eula=true" > "$RUN/eula.txt"
{
    echo "online-mode=false"
    echo "spawn-protection=0"
    echo "server-port=$((20000 + RANDOM % 20000))"
    # A normal world: flat/custom presets count as "experimental" on Forge
    # and park the client on a confirmation screen.
    echo "level-seed=gulliver"
} > "$RUN/server.properties"
printf 'narrator:0\nonboardAccessibility:false\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\nsoundCategory_master:0.0\npauseOnLostFocus:false\n' > "$RUN/options.txt"

run_one() { # side timeout-seconds
    local side="$1" limit="$2" out="$LOG_DIR/$MC-$LOADER-$1.log"
    local task=runServer; [ "$side" = client ] && task=runClient
    local before
    before=" $(pgrep -f java | tr '\n' ' ') "
    ./gradlew -p "$DIR" "$task" -Pselftest --console=plain > "$out" 2>&1 &
    local pid=$! waited=0
    local tag="GULLIVER SELFTEST $(echo "$side" | tr a-z A-Z)"
    while kill -0 $pid 2>/dev/null; do
        if grep -q "$tag \(PASS\|FAIL\)" "$out"; then sleep 5; break; fi
        sleep 2; waited=$((waited + 2))
        if [ $waited -ge "$limit" ]; then echo "[$MC/$LOADER/$side] TIMEOUT after ${limit}s"; break; fi
    done
    # Stop every JVM this run started (the game), but never a Gradle daemon.
    for p in $(pgrep -f java); do
        case "$before" in *" $p "*) continue ;; esac
        ps -o command= -p "$p" | grep -q "GradleDaemon" && continue
        kill "$p" >/dev/null 2>&1
    done
    kill $pid >/dev/null 2>&1; wait $pid 2>/dev/null
    local result
    result=$(grep -o "$tag \(PASS\|FAIL.*\)" "$out" | tail -1)
    [ -z "$result" ] && result="$tag NO RESULT (see $out)"
    grep -E "selftest FAIL|Mixin apply failed|InvalidInjectionException|InvalidMixinException|Critical injection failure" "$out" | head -5
    echo "[$MC/$LOADER] $result"
}

if [ "$WHAT" = server ] || [ "$WHAT" = both ]; then
    rm -rf "$RUN/world"
    run_one server 900
fi
if [ "$WHAT" = client ] || [ "$WHAT" = both ]; then
    mkdir -p "$RUN/saves"
    rm -rf "$RUN/saves/selftest"
    if [ -d "$RUN/world/region" ] || [ -f "$RUN/world/level.dat" ]; then
        cp -r "$RUN/world" "$RUN/saves/selftest"; rm -f "$RUN/saves/selftest/session.lock"
        run_one client 420
    else
        echo "[$MC/$LOADER] client skipped: no server world to join"
    fi
fi
