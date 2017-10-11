import os
import re
import shutil
import languages.subprocessUtils as subprocessUtils
import sys


whitespace_pattern = re.compile(r'^\s*$')
include_pattern = re.compile('#include\s*<([a-zA-Z0-9/._-]*)>')


max_headers_to_check = 100


c_libc_library = [
    'assert.h',
    'complex.h',
    'ctype.h',
    'errno.h',
    'fenv.h',
    'float.h',
    'inttypes.h',
    'iso646.h',
    'limits.h',
    'locale.h',
    'math.h',
    'setjmp.h',
    'signal.h',
    'stdalign.h',
    'stdarg.h',
    'stdatomic.h',
    'stdbool.h',
    'stddef.h',
    'stdint.h',
    'stdio.h',
    'stdlib.h',
    'stdnoreturn.h',
    'string.h',
    'tgmath.h',
    'threads.h',
    'time.h',
    'uchar.h',
    'wchar.h',
    'wctype.h']


cpp_stl_library = [
    'algorithm',
    'array',
    'vector',
    'deque',
    'list',
    'forward_list',
    'set',
    'map',
    'unordered_set',
    'unordered_map',
    'stack',
    'queue']


cpp_qt_library = ['QApplication', 'QCoreApplication', 'QObject', 'QWidget']


libraries = {
    'C': [('libc', c_libc_library)],
    'C++': [('STL', cpp_stl_library), ('Qt', cpp_qt_library)],
}


header_prefixes = {
    'C': [],
    'C++': [('boost/', 'Boost'), ('QtCore/', 'Qt')]
}


def analyze_cpp(repository_id, repository_name, login, archive_output_dir):
    run(repository_id, archive_output_dir)


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


def compute_headers(files):
    result = set()
    for i in files:
        with open(i) as f:
            for line in f.readlines():
                match = include_pattern.search(line)
                if match is not None:
                    header = match.groups()[0]
                    result.add(header)
    return result


def compute_dependencies(files, language):
    headers = compute_headers(files)
    result = set()

    for library_name, library_headers in libraries[language]:
        for i in library_headers:
            if i in headers:
                result.add(library_name)
                break

    for i in list(headers)[:max_headers_to_check]:
        for prefix, library_name in header_prefixes[language]:
            if library_name not in result and i.startswith(prefix):
                result.add(library_name)

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

    files = {
        'C': set(),
        'C++': set()
    }

    prefix = "WARN"
    lines = subprocessUtils.getoutput(
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
                output(language, 'warning', message)
        elif i.startswith('Checking '):
            tokens = i.split(' ')
            filename = extract_filename(tokens[1])
            language = filename_to_language(filename)
            files[language].add(filename)

    for language in ['C', 'C++']:
        files_of_language = files[language]
        for i in files_of_language:
            subprocessUtils.run('node', 'stripComments.js', i)

        for dep in compute_dependencies(files_of_language, language):
            output(language, 'dependence', dep)

        lines_of_code = compute_lines_of_code(files_of_language)
        output(language, 'lines_of_code', lines_of_code)
