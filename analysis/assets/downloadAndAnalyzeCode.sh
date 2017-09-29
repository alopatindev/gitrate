#!/usr/bin/env bash

# (stdin) input format: "repository_id;archive_uri;language1,language2,..."
# (stdout) output format: "repository_id;language;message_type;message"

current_dir=$(dirname "$0")

function analyze_javascript () {
    archive_output_dir="$1"
    repository_id="$2"

    function output () {
        language="JavaScript"
        message_type="$1"
        message="$2"
        echo "${repository_id};${language};${message_type};${message}"
    }

    for i in $(find "${archive_output_dir}" -type d -name "node_modules" | sort); do
        rm -r "$i"
    done

    packagejson_dir=
    for packagejson in $(find "${archive_output_dir}" -type f -regextype posix-extended \
        -regex '.*/(package|bower)\.json$' | sort); do
        dir=$(dirname "${packagejson}")
        if [ "${packagejson_dir}" = "" ] || [ ${packagejson_dir} = "${dir}" ]; then
            packagejson_dir="${dir}"
            for key in "dependencies" "devDependencies" ; do
                for dep in $(jq --monochrome-output --raw-output ".${key} | keys[]" "${packagejson}"); do
                    output dependence "${dep}"
                done
            done
        else
            # that's probably a third-party library that was copy-pasted to the repository
            rm -r "${dir}"
        fi
        rm "${packagejson}"
    done

    find "${archive_output_dir}" -type f -regextype posix-extended -regex '.*/(\.eslint.*|yarn\.lock)$' -delete

    for message in $(node_modules/eslint/bin/eslint.js --format json --no-color "${archive_output_dir}" | \
        grep --extended-regexp '^\[' | \
        jq --monochrome-output --raw-output '.[].messages[] | "\(.ruleId)"'); do
        output warning "${message}"
    done

    lines_of_code=$(find "${archive_output_dir}" -type f -name "*.js" -print0 | \
        xargs -0 grep --invert-match --regexp='^\s*$' | wc -l)
    output lines_of_code "${lines_of_code}"
}

function analyze () {
    repository_id=$(echo "$1" | cut -d ";" -f1)
    archive_url=$(echo "$1" | cut -d ";" -f2)
    languages=$(echo "$1" | cut -d ";" -f3)

    archive_output_dir="${current_dir}/data/${repository_id}"
    archive_path="${archive_output_dir}.tar.gz"
    wget --quiet "${archive_url}" -O "${archive_path}"
    mkdir -p "${archive_output_dir}"
    tar -xzf "${archive_path}" -C "${archive_output_dir}"
    rm "${archive_path}"

    good_filename_pattern="^[a-zA-Z0-9/._-]*$"
    bad_filenames=$(find "${archive_output_dir}" -type f | \
        sed "s!.*${repository_id}!!" | \
        grep --count --invert-match --extended-regexp "${good_filename_pattern}")

    if [ "${bad_filenames}" -eq 0 ]; then
        for language in $(echo "${languages}" | tr "," "\\n"); do
            if [[ "${language}" -eq "JavaScript" ]]; then
                analyze_javascript "${archive_output_dir}" "${repository_id}"
            fi
        done
    fi

    rm -r "${archive_output_dir}"
}

cd "${current_dir}" || exit 1

while read -r line; do
    analyze "${line}"
done
