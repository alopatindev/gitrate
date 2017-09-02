#!/bin/sh

set -e

OUT_DIR="/tmp/spark-app"
OUT_JAR="${OUT_DIR}/out.jar"

rm -rfv "${OUT_DIR}"
mkdir -p "${OUT_DIR}"
sbt assembly
cp -fv ./target/scala-*/hiregooddevs-analysis-assembly-0.1-SNAPSHOT.jar "${OUT_JAR}"
cp -fv ./conf/spark-defaults.conf "${OUT_DIR}/"

cd "${OUT_DIR}"
spark-submit --deploy-mode cluster --class hiregooddevs.analysis.Main --properties-file spark-defaults.conf "${OUT_JAR}"
