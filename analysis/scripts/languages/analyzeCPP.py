import os
import re
import shutil
import subprocess
import sys


whitespace_pattern = re.compile(r'^\s*$')


def analyze_cpp(repository_id, repository_name, login, archive_output_dir):
    run(repository_id, archive_output_dir)


def getoutput(*args):
    args_as_strings = list(map(str, args))
    try:
        pipe = subprocess.Popen(
            args=args_as_strings,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True)
        stdout, _ = pipe.communicate()
        return stdout
    except subprocess.TimeoutExpired:
        pipe.kill()
        return ''


def compute_lines_of_code_for_file(filename):
    lines = open(filename).readlines()
    result = 0
    for i in lines:
        if not whitespace_pattern.match(i):
            result += 1
    return result


def compute_lines_of_code(files):
    result = 0
    for i in files:
        result += compute_lines_of_code_for_file(i)
    return result


def extract_filename(text):
    root, raw_extension = os.path.splitext(text)
    extension = raw_extension.replace(':', '')
    return root + extension


def filename_to_language(filename):
    raw_extension = os.path.splitext(filename)[1]
    extension = raw_extension.replace(':', '')
    return 'C' if extension == '.c' else 'C++'


def run(repository_id, archive_output_dir):
    def output(language, message_type, message):
        text = ';'.join((repository_id, language, message_type, str(message)))
        print(text)

    c_files = set()
    cpp_files = set()

    prefix = "WARN"
    lines = getoutput(
        'cppcheck',
        '--enable=all',
        '--template=%s;{file};{id}' % prefix,
        archive_output_dir)

    for i in lines.split('\n'):
        if i.startswith(prefix):
            tokens = i.split(';')
            filename = tokens[1]
            message = tokens[2]
            if len(filename) > 0 and len(message) > 0:
                language = filename_to_language(filename)
                # TODO: strip comments?
                output(language, 'warning', message)
        elif i.startswith('Checking '):
            tokens = i.split(' ')
            filename = extract_filename(tokens[1])
            language = filename_to_language(filename)
            if language == 'C':
                c_files.add(filename)
            else:
                cpp_files.add(filename)

    output('C', 'lines_of_code', compute_lines_of_code(c_files))
    output('C++', 'lines_of_code', compute_lines_of_code(cpp_files))
