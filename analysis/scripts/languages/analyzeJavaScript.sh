#!/usr/bin/env bash

language="JavaScript"

function has_valid_git_url () {
    local -a packagejson_files="$1"
    local repository_name="$2"
    local login="$3"
    local valid_git_url=true

    for packagejson in ${packagejson_files[@]}; do
        local git_url
        git_url=$(jq --monochrome-output --raw-output ".repository.url" "${packagejson}")
        if [ "${git_url}" = "" ] || [ "${git_url}" = null ]; then
            continue
        fi

        local git_url_match
        git_url_match=$(echo "${git_url}" | grep -E "[:/]${login}/${repository_name}")
        if [ "${git_url_match}" != "${git_url}" ]; then
            valid_git_url=false
        fi

        break
    done

    echo "${valid_git_url}"
}

function detect_dependencies () {
    local packagejson="$1"
    local archive_output_dir="$2"
    local -a dependencies=()

    for key in "dependencies" "devDependencies" ; do
        for dep in $(jq --monochrome-output --raw-output ".${key} | keys[]" "${packagejson}"); do
            dependencies+=("${dep}")
        done
    done

    if (jq --monochrome-output --raw-output ".scripts | values[]" "${packagejson}" | grep -E "^node " || \
        grep -RE "^#!/usr/bin/(env |)node" "${archive_output_dir}") >> /dev/null; then
        dependencies+=("Node.js")
    fi

    for dev in "${dependencies[@]}" ; do
        echo "${dev}"
    done | grep -v -E '^\@'
}

function prepare_sources () {
    local archive_output_dir="$1"

    find "${archive_output_dir}" \
        -type f \
        -regextype posix-extended \
        -regex '.*/(\.eslint.*|yarn\.lock|.*\.min\.js|package-lock\.json|\.gitignore)$' \
        -delete

    find "${archive_output_dir}" -type f -name "*.js" -exec "./stripComments.js" "{}" ";"
}

function compute_lines_of_code_js () {
    local archive_output_dir="$1"
    find "${archive_output_dir}" -type f -name "*.js" -print0 | \
        xargs -0 grep --invert-match --regexp='^\s*$' | \
        wc -l
}

function sort_by_length () {
    cat | \
        awk '{ print length, $0 }' | \
        sort --numeric-sort --stable | \
        cut --delimiter=" " --fields="2-"
}

function has_invalid_files () {
    local archive_output_dir
    archive_output_dir="$1"

    local count
    count=$({
        find "${archive_output_dir}" -type f -regextype posix-extended -iregex '.*\.(so|exe|o)$'
        find "${archive_output_dir}" -type d -name node_modules
    } | wc -l)

    if [ "${count}" = 0 ]; then
        echo false
    else
        echo true
    fi
}

function analyze_javascript () {
    local repository_id="$1"
    local repository_name="$2"
    local login="$3"
    local archive_output_dir="$4"

    function output () {
        local message_type="$1"
        local message="$2"
        echo "${repository_id};${repository_name};${language};${message_type};${message}"
    }

    local -a unsorted_packagejson_files
    unsorted_packagejson_files=$(find "${archive_output_dir}" \
        -type f \
        -regextype posix-extended -regex '.*/(package|bower)\.json$')

    local -a packagejson_files
    packagejson_files=$(echo "${unsorted_packagejson_files[@]}" | sort_by_length)

    if [ "${packagejson_files[*]}" = "" ] ; then
        return
    fi

    if [ $(has_invalid_files "${archive_output_dir}") = false ] && [ $(has_valid_git_url "${packagejson_files[@]}" "${repository_name}" "${login}") = true ] ; then
        local packagejson_dir
        packagejson_dir=""
        for packagejson in ${packagejson_files}; do
            local dir
            dir=$(dirname "${packagejson}")
            if [ "${packagejson_dir}" = "" ] || [ "${packagejson_dir}" = "${dir}" ]; then
                packagejson_dir="${dir}"
                for dep in $(detect_dependencies "${packagejson}" "${archive_output_dir}"); do
                    output dependence "${dep}"
                done
            else
                # that's probably a third-party library that was copy-pasted to the repository
                rm -r "${dir}"
            fi
            rm "${packagejson}"
        done

        if [ "${packagejson_dir}" != "" ]; then
            prepare_sources "${archive_output_dir}"

            for message in $(node_modules/.bin/eslint --format json --no-color "${archive_output_dir}" | \
                grep --extended-regexp '^\[' | \
                jq --monochrome-output --raw-output '.[].messages[] | "\(.ruleId)"'); do
                output warning "${message}"
            done

            output lines_of_code "$(compute_lines_of_code_js "${archive_output_dir}")"
        fi
    fi
}

analyze_javascript $@
