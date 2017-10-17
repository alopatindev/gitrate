#!/usr/bin/env bash

set -x

username="gitrate_test"
database="${username}"

psql --username postgres \
    --command="CREATE ROLE ${username} NOSUPERUSER CREATEDB NOCREATEROLE INHERIT LOGIN" \
    --command="CREATE DATABASE ${database} OWNER ${username}"
