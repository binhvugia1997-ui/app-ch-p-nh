"""Bounded ownership-scoped host process cleanup; never launches an app."""
import os
import signal
import subprocess
import time


def stop_logcat(process):
    if process.poll() is not None:
        return
    try:
        process.terminate()
        process.wait(timeout=5)
    except (OSError, subprocess.TimeoutExpired):
        process.kill()
        process.wait(timeout=5)


def kill_owned_tree(process):
    if process.poll() is not None:
        return
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                       capture_output=True, timeout=10, check=True)
    else:
        os.killpg(process.pid, signal.SIGKILL)
    process.wait(timeout=5)


def wait_collector(process, check, cancel, stop_project, seconds, clock=time.monotonic):
    deadline = clock() + seconds + 45
    try:
        while True:
            check()
            if clock() >= deadline:
                raise TimeoutError('Collector exceeded bounded collection deadline.')
            try:
                code = process.wait(timeout=.25)
            except subprocess.TimeoutExpired:
                continue
            check()
            if code:
                raise RuntimeError(f'Collector exited with status {code}; evidence incomplete.')
            return
    except BaseException:
        errors = []
        for action in (cancel, stop_project):
            try:
                action()
            except Exception as error:
                errors.append(str(error))
        try:
            process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            try:
                kill_owned_tree(process)
            except Exception as error:
                errors.append('owned collector tree cleanup: ' + str(error))
        if errors:
            raise RuntimeError('Collector cancellation/cleanup failed: ' + '; '.join(errors))
        raise


def stop_trace(adb, pid, capture_id, sleep=time.sleep):
    def state():
        return adb('shell', f"if [ ! -d /proc/{pid} ]; then echo GONE; "
                   f"elif [ ! -r /proc/{pid}/cmdline ]; then echo UNKNOWN; "
                   f"elif grep -a -q 'phase2-{capture_id}' /proc/{pid}/cmdline; then echo OWNED; "
                   "else echo FOREIGN; fi").strip()
    before = state()
    if before == 'GONE':
        return
    if before != 'OWNED':
        raise RuntimeError('Trace PID ownership unverified; no signal sent.')
    adb('shell', f"if [ -r /proc/{pid}/cmdline ] && "
        f"grep -a -q 'phase2-{capture_id}' /proc/{pid}/cmdline; then kill -INT {pid}; fi")
    for _ in range(5):
        current = state()
        if current == 'GONE':
            return
        if current != 'OWNED':
            raise RuntimeError('Trace PID ownership changed during shutdown.')
        sleep(.25)
    raise RuntimeError('Owned trace did not stop within bounded verification.')
