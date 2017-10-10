#!/usr/bin/env bash

# usage: ./downloadAndAnalyzeCode.sh {max-archive-size-bytes} {with-cleanup}
# example: ./downloadAndAnalyzeCode.sh 2048 true
# (stdin) input format: "repository_id;repository_name;login;archive_uri;language1,language2,..."
# (stdout) output format: "repository_id;language;message_type;message"

function analyze () {
    local repository_id
    local repository_name
    local login
    local archive_url
    local languages
    local cleanup
    local max_archive_size_bytes
    repository_id=$(echo "$1" | cut -d ";" -f1)
    repository_name=$(echo "$1" | cut -d ";" -f2)
    login=$(echo "$1" | cut -d ";" -f3)
    archive_url=$(echo "$1" | cut -d ";" -f4)
    languages=$(echo "$1" | cut -d ";" -f5)
    max_archive_size_bytes="$2"
    cleanup="$3"

    local archive_output_dir
    local archive_path
    archive_output_dir="${current_dir}/data/${repository_id}"
    archive_path="${archive_output_dir}.tar.gz"

    if curl --silent --location "${archive_url}" --output "${archive_path}" --max-filesize "${max_archive_size_bytes}"; then
        rm -rf "${archive_output_dir}"
        mkdir -p "${archive_output_dir}"
        tar -xzf "${archive_path}" -C "${archive_output_dir}"

        local valid_filename_pattern
        local invalid_filenames
        valid_filename_pattern="^[a-zA-Z0-9/._-]*$"
        invalid_filenames=$(find "${archive_output_dir}" -type f | \
            sed "s!.*${repository_id}!!" | \
            grep --count --invert-match --extended-regexp "${valid_filename_pattern}")

        if [ "${invalid_filenames}" = 0 ]; then
            for language in $(echo "${languages}" | tr "," "\\n"); do
                case "${language}" in
                    "JavaScript" )
                        analyze_javascript "${repository_id}" "${repository_name}" "${login}" "${archive_output_dir}"
                        ;;
                    "C" | "C++" )
                        analyze_cpp "${repository_id}" "${archive_output_dir}"
                        ;;
                    * )
                        ;;
                esac
            done
        fi
    fi

    if [ "${cleanup}" = true ]; then
        rm -rf "${archive_output_dir}"
        rm -f "${archive_path}"
    fi
}

function main () {
    local max_archive_size_bytes
    local cleanup
    max_archive_size_bytes="$1"
    cleanup="$2"

    set -uo pipefail

    # redirect stderr to null
    exec 2>/dev/null

    for i in languages/*.sh; do
        source "$i"
    done

    while read -r line; do
        analyze "${line}" "${max_archive_size_bytes}" "${cleanup}"
    done
}

current_dir=$(dirname "$0")
cd "${current_dir}" || exit 1

main $@
