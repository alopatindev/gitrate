#!/bin/sh

set -e

FILE_PATTERN='\.scala$'
MAX_DEPTH=100

OWNER="$1"
REPO_NAME="$2"
REPO_URI="git://github.com/${OWNER}/${REPO_NAME}.git"

TMP_TEMPLATE='/tmp/tmp.git-download.XXXXXXXXXX'

TMP_DIR=$(mktemp -d "${TMP_TEMPLATE}")
cd "${TMP_DIR}"

git clone --quiet --no-checkout --depth=${MAX_DEPTH} "${REPO_URI}" "${REPO_NAME}"
cd "${REPO_NAME}"

BRANCH=$(git branch --no-color | cut -c 3-)
git checkout --quiet ${BRANCH} -- $(git ls-tree ${BRANCH} -r --name-only | egrep "${FILE_PATTERN}")

echo "${TMP_DIR}"
