#!/bin/env bash

set -euo pipefail

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

CONFIG_PATH="analysis/src/main/resources/application.conf"
APP_NAME="$(grep '^app\.name = ' "${CONFIG_PATH}" | cut -d ' ' -f3)"
SCRIPTS_DIR="/tmp/${APP_NAME}"
CHECKPOINT_DIR="/var/tmp/${APP_NAME}"

LOG_FILE_DRIVER="/var/tmp/${APP_NAME}-driver.log"
LOG_FILE_EXECUTOR="/var/tmp/${APP_NAME}-executor.log"
LOG4G_PROPS_DRIVER="${SCRIPTS_DIR}/log4j-prod-driver.properties"
LOG4G_PROPS_EXECUTOR="${SCRIPTS_DIR}/log4j-prod-executor.properties"

MAIN_CLASS="analysis.Main"
OUT_JAR="${SCRIPTS_DIR}/out.jar"
SBT_OPTS="-server -Xms3000M -Xmx3000M -Xss1M -XX:+UseConcMarkSweepGC -XX:NewRatio=8"

WITH_CLEANUP=true

reset

if [ "${WITH_CLEANUP}" = true ] ; then
    sudo rm -rvf "${SCRIPTS_DIR}" "${CHECKPOINT_DIR}" "${LOG_FILE_DRIVER}" "${LOG_FILE_EXECUTOR}"
    cp -rv analysis/scripts "${SCRIPTS_DIR}"

    mkdir -p "${SCRIPTS_DIR}/data"
    chmod 1777 "${SCRIPTS_DIR}/data"

    sbt analysis/assembly
    cp -vf analysis/target/scala-*/*-assembly-*.jar "${OUT_JAR}"
    cp -vf analysis/conf/spark-defaults.conf "${SCRIPTS_DIR}/"

    cd "${SCRIPTS_DIR}"

    # TODO: versions
    npm install eslint eslint-plugin-better eslint-plugin-mocha eslint-plugin-private-props eslint-plugin-promise decomment
else
    cd "${SCRIPTS_DIR}"
fi

spark-submit \
    --deploy-mode cluster \
    --class "${MAIN_CLASS}" \
    --name "${APP_NAME}" \
    --properties-file spark-defaults.conf \
    --driver-java-options "-Dlog4j.configuration=file:${LOG4G_PROPS_DRIVER}" \
    --conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=file:${LOG4G_PROPS_EXECUTOR}" \
    --supervise \
    "${OUT_JAR}"
