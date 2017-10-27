#!/bin/env bash

set -euo pipefail

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

CONFIG_PATH="src/main/resources/application.conf"
APP_NAME="$(grep '^app\.name = ' "${CONFIG_PATH}" | cut -d ' ' -f3)"
SCRIPTS_DIR="/tmp/${APP_NAME}"
CHECKPOINT_DIR="/var/tmp/${APP_NAME}"
LOG_FILE="/var/tmp/${APP_NAME}.log"

MAIN_CLASS="analysis.Main"
OUT_JAR="${SCRIPTS_DIR}/out.jar"
SBT_OPTS="-server -Xms3000M -Xmx3000M -Xss1M -XX:+UseConcMarkSweepGC -XX:NewRatio=8"

sudo rm -rvf "${SCRIPTS_DIR}" "${CHECKPOINT_DIR}" "${LOG_FILE}"
cp -rv scripts "${SCRIPTS_DIR}"
mkdir -p "${SCRIPTS_DIR}/data"

LOG4G_PROPS="${SCRIPTS_DIR}/log4j-prod.properties"

sudo chown spark:hadoop "${SCRIPTS_DIR}/data"

sbt assembly
cp -vf ./target/scala-*/*-assembly-*.jar "${OUT_JAR}"
cp -vf ./conf/spark-defaults.conf "${SCRIPTS_DIR}/"

cd "${SCRIPTS_DIR}"

# TODO: versions
npm install eslint eslint-plugin-better eslint-plugin-mocha eslint-plugin-private-props eslint-plugin-promise decomment

spark-submit \
    --deploy-mode cluster \
    --class "${MAIN_CLASS}" \
    --name "${APP_NAME}" \
    --properties-file spark-defaults.conf \
    --driver-java-options "-Dlog4j.configuration=file:${LOG4G_PROPS}" \
    --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=file:${LOG4G_PROPS}" \
    "${OUT_JAR}"
