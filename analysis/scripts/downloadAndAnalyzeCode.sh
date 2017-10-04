#!/usr/bin/env bash

# (stdin) input format: "repository_id;repository_name;login;archive_uri;language1,language2,..."
# (stdout) output format: "repository_id;language;message_type;message"

function analyze () {
    local repository_id=$(echo "$1" | cut -d ";" -f1)
    local repository_name=$(echo "$1" | cut -d ";" -f2)
    local login=$(echo "$1" | cut -d ";" -f3)
    local archive_url=$(echo "$1" | cut -d ";" -f4)
    local languages=$(echo "$1" | cut -d ";" -f5)

    local archive_output_dir="${current_dir}/data/${repository_id}"
    local archive_path="${archive_output_dir}.tar.gz"
    wget --quiet "${archive_url}" -O "${archive_path}"
    rm -rf "${archive_output_dir}"
    mkdir -p "${archive_output_dir}"
    tar -xzf "${archive_path}" -C "${archive_output_dir}"
    rm "${archive_path}"

    local valid_filename_pattern="^[a-zA-Z0-9/._-]*$"
    local invalid_filenames=$(find "${archive_output_dir}" -type f | \
        sed "s!.*${repository_id}!!" | \
        grep --count --invert-match --extended-regexp "${valid_filename_pattern}")

    if [ "${invalid_filenames}" -eq 0 ]; then
        for language in $(echo "${languages}" | tr "," "\\n"); do
            if [[ "${language}" = "JavaScript" ]]; then
                analyze_javascript "${repository_id}" "${repository_name}" "${login}" "${archive_output_dir}"
            fi
        done
    fi

    if [ "${cleanup}" = true ]; then
        rm -r "${archive_output_dir}"
    fi
}

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

cleanup=false
while true; do
  case "$1" in
    --with-cleanup ) cleanup=true; shift ;;
    * ) break ;;
  esac
done

# redirect stderr to null
exec 2>/dev/null

source languages/*.sh

while read -r line; do
    analyze "${line}"
done
