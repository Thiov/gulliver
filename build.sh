#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

JAVA_HOME=/home/biker/cave_dweller_fabric_port/work/jdk-25.0.3+9
LIBS=/home/biker/cave_dweller_fabric_port/work/libs_compile
MC_JAR=/home/biker/witherstormmod_fabric_port/libs/mc-merged-allpublic.jar

VERSION="0.14.3-fabric"
MODID="gulliver"

CP="$MC_JAR:$LIBS/fabric-loader.jar:$LIBS/fabric-api.jar:$LIBS/sponge-mixin.jar"
for j in "$LIBS/fabric_api_inner"/*.jar; do CP="$CP:$j"; done
for j in "$LIBS/mc_libs"/*.jar; do CP="$CP:$j"; done

rm -rf build/classes build/jar
mkdir -p build/classes build/jar build/libs

SRC=$(mktemp)
find src/main/java -name '*.java' > "$SRC"
echo "Compiling $(wc -l < "$SRC") sources..."

"$JAVA_HOME/bin/javac" \
    --release 25 \
    -encoding UTF-8 \
    -cp "$CP" \
    -d build/classes \
    -Xlint:-options \
    @"$SRC"

cp -r build/classes/. build/jar/
if [ -d src/main/resources ]; then
    cp -r src/main/resources/. build/jar/
fi
sed -i "s/\${version}/$VERSION/" build/jar/fabric.mod.json

cd build/jar
"$JAVA_HOME/bin/jar" cf "../libs/${MODID}-${VERSION}.jar" .
cd ../..
echo "Built build/libs/${MODID}-${VERSION}.jar ($(du -sh build/libs/${MODID}-${VERSION}.jar | cut -f1))"
