import subprocess


def run(*args):
    args_as_strings = list(map(str, args))
    pipe = subprocess.Popen(
        args=args_as_strings,
        stderr=subprocess.DEVNULL)
    return pipe.wait()


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
