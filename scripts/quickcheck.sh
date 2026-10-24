#!/usr/bin/env bash
# Fast verification across the version spread: builds a representative
# target per Minecraft version and runs the static mixin checker on each.
# Usage: scripts/quickcheck.sh [<mc>/<loader> ...]
# Default targets cover every preprocessor branch and all three loaders.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home}"
export JAVAP="$JAVA_HOME/bin/javap"
M="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft"
jar_for() {
    case "$1" in
        26.*) ls "$M/minecraft-merged-deobf/$1/"*.jar 2>/dev/null | grep -v sources | head -1 ;;
        *)    ls "$M/minecraft-merged/$1-loom.mappings."*/*.jar 2>/dev/null | grep -v sources | head -1 ;;
    esac
}
targets=("$@")
[ ${#targets[@]} -eq 0 ] && targets=(26.3/fabric 26.2/fabric 26.1.2/neoforge 1.21.11/forge 1.21.10/fabric 1.20.1/forge 1.20.1/neoforge)
fail=0
for t in "${targets[@]}"; do
    mc="${t%/*}"
    if ! out=$(./gradlew -p "versions/$t" build --console=plain -q 2>&1); then
        echo "BUILD FAIL $t"; echo "$out" | grep -E 'error:|\.java:[0-9]+' | head -40; fail=1; continue
    fi
    jar=$(jar_for "$mc")
    if [ -z "$jar" ]; then echo "SKIP mixin check $t (no mapped jar)"; continue; fi
    if ! res=$(python3 scripts/check_mixins.py "versions/$t/build/preprocessed/java" "$jar" 2>&1); then
        echo "MIXIN FAIL $t"; echo "$res" | tail -30; fail=1; continue
    fi
    echo "OK $t"
done
exit $fail
