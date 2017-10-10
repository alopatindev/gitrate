#!/usr/bin/env bash

function sum_lines () {
    cat | awk '{ sum += $1 } END { print sum }'
}

function compute_lines_of_code_cpp () {
    local filename="$1"
    grep --count --invert-match --regexp='^\s*$' "${filename}"
}

function filename_to_language () {
    local filename="$1"
    local extension
    extension=$(echo "${filename}" | sed 's!.*\.!!')

    case "${extension}" in
        "c" ) echo "C" ;;
        * ) echo "C++" ;;
    esac
}

function analyze_cpp () {
    local repository_id="$1"
    local archive_output_dir="$2"

    function output () {
        local language
        local message_type
        local message
        language="$1"
        message_type="$2"
        message="$3"
        echo "${repository_id};${language};${message_type};${message}"
    }

    declare -A c_files=()
    declare -A cpp_files=()

    local prefix
    prefix="WARN"

    local -a lines
    lines=$(cppcheck --enable=all --template="${prefix};{file};{id}" "${archive_output_dir}" 2>>/dev/stdout | \
        grep -E "^(Checking|${prefix})" | \
        sed 's! !;!')

    for i in ${lines}; do
        if echo "$i" | grep -E "^${prefix};" >> /dev/null; then
            local filename
            filename=$(echo "$i" | cut --delimiter=";" --field=2)

            local message
            message=$(echo "$i" | cut --delimiter=";" --field=3)

            if [ "${filename}" != "" ] && [ "${message}" != "" ]; then
                local language
                language=$(filename_to_language "${filename}")
                # TODO: strip comments?
                output "${language}" warning "${message}"
            fi
        elif echo "$i" | grep -E '^Checking;' >> /dev/null; then
            local filename
            filename=$(echo "$i" | cut --delimiter=";" --field=2 | sed 's!:$!!')
            language=$(filename_to_language "${filename}")
            if [ "${language}" = "C" ]; then
                c_files["${filename}"]="1"
            else
                cpp_files["${filename}"]="1"
            fi
        fi
    done

    local lines_of_code

    if [ "${#c_files[@]}" != 0 ]; then
        lines_of_code=$(for i in ${!c_files[@]}; do
            compute_lines_of_code_cpp "$i"
        done | sum_lines)
    fi
    output "C" lines_of_code "${lines_of_code}"

    if [ "${#cpp_files[@]}" != 0 ]; then
        lines_of_code=$(for i in ${!cpp_files[@]}; do
            compute_lines_of_code_cpp "$i"
        done | sum_lines)
    fi
    output "C++" lines_of_code "${lines_of_code}"
}
