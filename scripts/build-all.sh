#!/usr/bin/env bash
# Build every versions/<mc>/<loader> target (or the ones given as
# "<mc>/<loader>" arguments) and collect the jars in dist/.
# Gradle must run on JDK 25: set JAVA_HOME accordingly.
set -u
cd "$(dirname "$0")/.."
mkdir -p dist
targets=("$@")
if [ ${#targets[@]} -eq 0 ]; then
    for d in versions/*/*/; do
        [ -f "$d/build.gradle" ] && targets+=("${d#versions/}")
    done
fi
failed=()
for t in "${targets[@]}"; do
    t="${t%/}"
    echo "==> $t"
    if ./gradlew -p "versions/$t" build --console=plain -q; then
        for jar in "versions/$t"/build/libs/gulliver-*.jar; do
            case "$jar" in *-sources.jar|*-dev.jar) continue ;; esac
            cp "$jar" dist/
        done
    else
        failed+=("$t")
    fi
done
ls -1 dist
if [ ${#failed[@]} -gt 0 ]; then
    echo "FAILED: ${failed[*]}"
    exit 1
fi
