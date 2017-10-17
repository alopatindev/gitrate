#!/bin/env bash

set -euo pipefail

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

CONFIG_PATH="src/main/resources/application.conf"
APP_NAME="$(grep 'app\.name' "${CONFIG_PATH}" | cut -d ' ' -f3)"
SCRIPTS_DIR="$(grep 'app\.scriptsDir' "${CONFIG_PATH}" | cut -d ' ' -f3)"
MAIN_CLASS="gitrate.analysis.Main"
OUT_JAR="${SCRIPTS_DIR}/out.jar"

rm -rvf "${SCRIPTS_DIR}"
cp -rv scripts "${SCRIPTS_DIR}"
mkdir -p "${SCRIPTS_DIR}/data"

sbt assembly
cp -v ./target/scala-*/*-assembly-*.jar "${OUT_JAR}"
cp -v ./conf/spark-defaults.conf "${SCRIPTS_DIR}/"

cd "${SCRIPTS_DIR}"

# TODO: versions
npm install eslint eslint-plugin-better eslint-plugin-mocha eslint-plugin-private-props eslint-plugin-promise decomment

spark-submit \
    --deploy-mode cluster \
    --class "${MAIN_CLASS}" \
    --name "${APP_NAME}" \
    --properties-file spark-defaults.conf \
    "${OUT_JAR}"
