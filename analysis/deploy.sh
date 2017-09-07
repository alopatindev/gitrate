#!/bin/sh

#cqlsh-3.11 -e "drop keyspace gitrate;"
cqlsh-3.11 -e "source 'conf/Schema.cql'; source 'conf/GithubSearchQueries.cql';"

set -e

MAIN_CLASS="gitrate.analysis.Main"
JVM_OPTS="$(tr '\n' ' ' < conf/jvm.options)"
OUT_DIR="/tmp/spark-app"
OUT_JAR="${OUT_DIR}/out.jar"

rm -rfv "${OUT_DIR}"
mkdir -p "${OUT_DIR}"
sbt assembly
cp -fv ./target/scala-*/*-assembly-*.jar "${OUT_JAR}"
cp -fv ./conf/spark-defaults.conf "${OUT_DIR}/"

cd "${OUT_DIR}"
spark-submit \
    --deploy-mode cluster \
    --class "${MAIN_CLASS}" \
    --name AnalyzeGithubUsers \
    --properties-file spark-defaults.conf \
    --driver-java-options "${JVM_OPTS}" \
    "${OUT_JAR}"
