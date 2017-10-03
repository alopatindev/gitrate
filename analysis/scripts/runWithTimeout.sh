#!/bin/env sh

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

timeout $* || exit 0
