#!/bin/sh

set -e

MAIN_CLASS="gitrate.analysis.Main"
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
    "${OUT_JAR}"
