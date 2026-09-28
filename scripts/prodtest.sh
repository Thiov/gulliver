#!/usr/bin/env bash
# Production self-test for a Fabric target: builds the release jar, installs
# a real Fabric server through Loom and runs the jar on it with
# -Dgulliver.selftest=true (the server halts itself after the test).
# Usage: scripts/prodtest.sh <mc-version> [minecraft-to-run] [fabric-api-version]
#   scripts/prodtest.sh 26.1.2 26.1 0.145.1+26.1   # the 26.1.2 jar on 26.1
set -u
cd "$(dirname "$0")/.."
MC="$1"; RUN_MC="${2:-}"; API="${3:-}"
DIR="versions/$MC/fabric"
RUN="$DIR/run-prod"
LOG="build/selftest/$MC-fabric-prod${RUN_MC:+-on-$RUN_MC}.log"
mkdir -p "$RUN" build/selftest
rm -rf "$RUN/world" "$RUN/mods"
echo "eula=true" > "$RUN/eula.txt"
printf 'online-mode=false\nspawn-protection=0\nserver-port=%s\nlevel-seed=gulliver\n' \
    "$((20000 + RANDOM % 20000))" > "$RUN/server.properties"
args=()
[ -n "$RUN_MC" ] && args+=("-PprodMc=$RUN_MC")
[ -n "$API" ] && args+=("-PprodFabricApi=$API")
./gradlew -p "$DIR" prodServer ${args[@]+"${args[@]}"} --console=plain > "$LOG" 2>&1
grep -E "selftest FAIL|Mixin apply|InvalidInjectionException|InvalidMixinException|Critical injection failure|Incompatible mods" "$LOG" | head -5
result=$(grep -o "GULLIVER SELFTEST SERVER \(PASS\|FAIL.*\)" "$LOG" | tail -1)
echo "[$MC fabric prod${RUN_MC:+ on $RUN_MC}] ${result:-NO RESULT (see $LOG)}"
