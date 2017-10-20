#!/usr/bin/env python3

# usage: ./downloadAndAnalyzeCode.sh {max-archive-size-bytes} {with-cleanup}
# example: ./downloadAndAnalyzeCode.sh 2048 true
# (stdin) input format: "repository_id;repository_name;login;archive_uri;language1,language2,..."
# (stdout) output format: "repository_id;repository_name;language;message_type;message"

import os
import re
import signal
import shutil
import sys
import languages.subprocessUtils as subprocessUtils
from languages.analyzeCPP import analyze_cpp


valid_filename_pattern = re.compile(r'^[a-zA-Z0-9/._-]*$')


def analyze_javascript(repository_id, repository_name, login, archive_output_dir):
    script = os.path.join('languages', 'analyzeJavaScript.sh')
    subprocessUtils.run('bash', script, repository_id, repository_name, login, archive_output_dir)


analyzers = {
    'JavaScript': analyze_javascript,
    'C': analyze_cpp,
    'C++': analyze_cpp
}


def change_dir(argv):
    executable = argv[0]
    current_dir = os.path.abspath(os.path.dirname(executable))
    os.chdir(current_dir)


def remove_file(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass


def remove_dir(path):
    try:
        shutil.rmtree(path)
    except FileNotFoundError:
        pass


def recreate_dir(path):
    remove_dir(path)
    os.mkdir(path)


def list_dir_recursively(path):
    for root, _, files in os.walk(path):
        for f in files:
            yield os.path.join(root, f)


def analyze(input_line, max_archive_size_bytes, cleanup, temp_files, temp_dirs):
    tokens = input_line.split(';')
    repository_id = tokens[0]
    repository_name = tokens[1]
    login = tokens[2]
    archive_url = tokens[3]
    languages = tokens[4]

    archive_output_dir = os.path.join('data', repository_id)
    archive_path = archive_output_dir + '.tar.gz'
    temp_files.add(archive_path)

    curl_return_code = subprocessUtils.run(
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
        temp_dirs.add(archive_output_dir)

        subprocessUtils.run('tar', '-xzf', archive_path, '-C', archive_output_dir)

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
            remove_dir(archive_output_dir)
            temp_dirs.remove(archive_output_dir)

            remove_file(archive_path)
            temp_files.remove(archive_path)


def main(argv):
    # TODO: getopts
    max_archive_size_bytes = int(argv[1])
    cleanup = argv[2] == 'true'

    temp_files = set()
    temp_dirs = set()

    def on_exit(signo=None, stack_frame=None):
        if cleanup:
            for i in temp_files:
                remove_file(i)
            for i in temp_dirs:
                remove_dir(i)
            temp_files.clear()
            temp_dirs.clear()
        sys.exit(0)

    signal.signal(signal.SIGTERM, on_exit)
    signal.signal(signal.SIGINT, on_exit)

    change_dir(argv)

    try:
        while True:
            input_line = input()
            analyze(input_line, max_archive_size_bytes, cleanup, temp_files, temp_dirs)
    except EOFError:
        pass

    on_exit()

main(sys.argv)
