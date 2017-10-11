#!/usr/bin/env python3

import os
import re
import shutil
import subprocess
import sys
from languages.analyzeCPP import analyze_cpp


def analyze_javascript(repository_id, repository_name, login, archive_output_dir):
    subprocess_run('bash', 'languages/analyzeJavaScript.sh', repository_id, repository_name, login, archive_output_dir)


analyzers = {
    'JavaScript': analyze_javascript,
    'C': analyze_cpp,
    'C++': analyze_cpp
}


valid_filename_pattern = re.compile(r'^[a-zA-Z0-9/._-]*$')


def subprocess_run(*args):
    args_as_strings = list(map(str, args))
    pipe = subprocess.Popen(args=args_as_strings, stderr=subprocess.DEVNULL)
    return pipe.wait()


def change_dir(argv):
    executable = argv[0]
    current_dir = os.path.abspath(os.path.dirname(executable))
    os.chdir(current_dir)


def recreate_dir(path):
    try:
        shutil.rmtree(path)
    except FileNotFoundError:
        pass
    os.mkdir(path)


def list_dir_recursively(path):
    for root, _, files in os.walk(path):
        for f in files:
            yield os.path.join(root, f)


def analyze(input_line, max_archive_size_bytes, cleanup):
    tokens = input_line.split(';')
    repository_id = tokens[0]
    repository_name = tokens[1]
    login = tokens[2]
    archive_url = tokens[3]
    languages = tokens[4]

    archive_output_dir = os.path.join('data', repository_id)
    archive_path = archive_output_dir + '.tar.gz'

    curl_return_code = subprocess_run(
        'curl',
        '--silent',
        '--location',
        archive_url,
        '--output',
        archive_path,
        '--max-filesize',
        max_archive_size_bytes)

    if curl_return_code == 0:
        recreate_dir(archive_output_dir)
        subprocess_run('tar', '-xzf', archive_path, '-C', archive_output_dir)

        filenames = (i.replace(repository_id, '') for i in list_dir_recursively(archive_output_dir))
        invalid_filenames = (i for i in filenames if not valid_filename_pattern.match(i))
        if not any(invalid_filenames):
            analyzers_to_apply = set()
            for language in languages.split(','):
                if language in analyzers:
                    analyzers_to_apply.add(analyzers[language])
            for run_analyzer in analyzers_to_apply:
                run_analyzer(repository_id, repository_name, login, archive_output_dir)

    if cleanup:
        shutil.rmtree(archive_output_dir)
        os.remove(archive_path)


def main(argv):
    # TODO: getopts
    max_archive_size_bytes = int(argv[1])
    cleanup = argv[2] == 'true'

    change_dir(argv)

    try:
        while True:
            input_line = input()
            analyze(input_line, max_archive_size_bytes, cleanup)
    except EOFError:
        pass


main(sys.argv)