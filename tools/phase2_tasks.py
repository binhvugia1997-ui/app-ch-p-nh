"""Project-owned activity-task inventory and cleanup; never launches an activity.

`am force-stop` ends a process but does not delete the package's task record, so the platform can
bring that task back later ("Start proc ... for next-top-activity"). That relaunch is what tripped the
camera-off preflight on 2026-10-29. Cleanup here is restricted to tasks whose *root* package is one of
the explicitly passed project packages, uses an `am` task command only after this build's own help
text documents it, and verifies every removal. A task or process reappearing after cleanup is never
permission to continue: callers must fail.
"""
import json
import re
import time

TASK_ID = re.compile(r'Task\{[^}#]*#(\d+)')
ROOT_PACKAGE = re.compile(r'A=\d+:([\w.]+)')
REAL_ACTIVITY = re.compile(r'realActivity=([\w.]+)/')
DOCUMENTED_REMOVE = re.compile(r'^\s*(?:task\s+)?remove\b', re.M)


def _blocks(text):
    """Split a `dumpsys activity` dump into one block per task, in dump order."""
    starts = [match.start() for match in TASK_ID.finditer(text)]
    for index, start in enumerate(starts):
        end = starts[index + 1] if index + 1 < len(starts) else len(text)
        yield text[start:end]


def project_tasks(text, packages):
    """Return `[{'taskId', 'package'}]` for tasks rooted in `packages`; `[]` when none.

    Attribution uses only the task header and its first few lines, so an unrelated task that merely
    embeds one project activity is not claimed. A block whose root package cannot be read is skipped,
    never guessed.
    """
    owned = set(packages)
    found = {}
    for block in _blocks(text):
        task_id = TASK_ID.match(block)
        header, _, rest = block.partition('\n')
        candidates = ROOT_PACKAGE.findall(header) + REAL_ACTIVITY.findall(header + '\n' + rest[:400])
        package = next((name for name in candidates if name in owned), None)
        if package:
            found.setdefault(int(task_id.group(1)), package)
    return [{'taskId': task_id, 'package': found[task_id]} for task_id in sorted(found)]


def await_quiescence(check, tasks, read_pid, sleep=time.sleep, interval=2, samples=2):
    """Require separated camera-off samples with no project task and no project process.

    The first sample showing either one raises immediately; slowness is never treated as cleanliness.
    """
    if samples < 2 or interval <= 0:
        raise ValueError('Quiescence requires at least two separated samples.')
    state = None
    for index in range(samples):
        check()
        state = {'projectPid': read_pid().strip(), 'tasks': tasks()}
        if state['projectPid'] or state['tasks']:
            raise RuntimeError('Project activity reappeared after task cleanup: ' + json.dumps(state))
        if index + 1 < samples:
            sleep(interval)
    return state


class ProjectTasks:
    """Idempotent, verifiable cleanup of project-owned activity tasks."""

    def __init__(self, read_dump, run_am, output, packages, sleep=time.sleep):
        self.read_dump, self.run_am, self.output = read_dump, run_am, output
        self.packages, self.sleep = tuple(packages), sleep
        self.verb = None

    def inventory(self):
        tasks = project_tasks(self.read_dump(), self.packages)
        with self.output.open('a', encoding='utf-8') as journal:
            journal.write(json.dumps({'time': time.time(), 'tasks': tasks}) + '\n')
        return tasks

    def removal(self):
        """Confirm this build's own help text before any task command is used."""
        if self.verb is None:
            try:
                help_text = self.run_am('task')
            except Exception as error:
                raise RuntimeError('Task command telemetry unavailable; refusing to guess: ' + str(error))
            if not DOCUMENTED_REMOVE.search(help_text or ''):
                raise RuntimeError('This build documents no `am task remove`; project activity tasks '
                                   'cannot be cleaned safely and the session must not start.')
            self.verb = ('task', 'remove')
        return self.verb

    def clean(self):
        """Remove every attributable project task and verify none survived.

        Returns the removed task ids; `[]` when the device is already clean (no command is issued).
        """
        tasks = self.inventory()
        if not tasks:
            return []
        verb = self.removal()
        ids = sorted({task['taskId'] for task in tasks})
        for task_id in ids:
            self.run_am(*verb, str(task_id))
        remaining = self.inventory()
        if remaining:
            raise RuntimeError('Project activity task survived cleanup: ' + json.dumps(remaining))
        return ids

    def require_clean(self):
        """Fail when any project task still exists while the camera is meant to be off."""
        tasks = self.inventory()
        if tasks:
            raise RuntimeError('Project activity task present with camera off: ' + json.dumps(tasks))
        return []
