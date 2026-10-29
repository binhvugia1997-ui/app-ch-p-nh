"""Host-only runner regressions: task cleanup order and resurrection blocking; no ADB, no launch."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

APP = 'com.aiphotographer.app'
ROOT = Path(__file__).resolve().parents[1]

spec = importlib.util.spec_from_file_location('phase2_runner', Path(__file__).with_name('run-phase2-solo.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

CLEAN_DUMP = '''ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
  * Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0 visible=true}
    * ActivityRecord{9f8e7b u0 com.android.launcher3/.Launcher t120}
'''

STALE_DUMP = '''ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
  * Task{8d9c1a2 #4782 type=standard A=10531:com.aiphotographer.app U=0 visible=true}
    * ActivityRecord{1a2b3c4 u0 com.aiphotographer.app/.MainActivity t4782}
  * Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0 visible=false}
'''

IDLE_CAMERA = 'Active Camera Clients:\n[]\nAllowed user IDs: 0'
HELP_REMOVE = '  task remove <TASK_ID>: remove the task with the given id from the recents.\n'
HELP_NO_REMOVE = '  task focus <TASK_ID>: move the task with the given id to the top of the stack.\n'


class Guard:
    def __init__(self, *args, **kwargs):
        self.samples = []
        self.failure = None
    def start(self):
        self.samples.append({'status': 0, 'skinC': 35.0})
    def check(self):
        pass
    def close(self, **kwargs):
        pass


class RunnerTests(unittest.TestCase):
    def run_session(self, dumps, help_text=HELP_REMOVE, pid=''):
        """Run one `--idle-only` session against a fake device and return what was issued."""
        issued = []
        self.issued = issued
        tasks = list(dumps)
        state = {'removed': 0}
        def fake_run(command, **kwargs):
            args = tuple(command[3:]) if command[:3] == ['adb', '-s', 'R5CW40EE9QK'] else tuple(command[2:])
            issued.append(args)
            if args == ('get-state',):
                text = 'device'
            elif args[:2] == ('shell', 'pidof'):
                text = pid
            elif args[:3] == ('shell', 'dumpsys', 'package'):
                text = 'android.permission.CAMERA: granted=true'
            elif args[:2] == ('shell', 'getprop'):
                text = 'SM-S918B'
            elif args[:3] == ('shell', 'dumpsys', 'media.camera'):
                text = IDLE_CAMERA
            elif args[:3] == ('shell', 'dumpsys', 'activity'):
                text = tasks[0] if len(tasks) == 1 else tasks.pop(0)
            elif args[:2] == ('shell', 'am') and args[2:4] == ('task', 'remove'):
                state['removed'] += 1
                tasks[:] = [CLEAN_DUMP]
                text = ''
            elif args[:2] == ('shell', 'am') and args[2] == 'task':
                text = help_text
            else:
                text = ''
            return Mock(stdout=text, stderr='', returncode=0)
        directory = tempfile.TemporaryDirectory(dir=ROOT/'device-evidence')
        self.addCleanup(directory.cleanup)
        output = Path(directory.name)/'session'
        argv = ['runner', '--adb', 'adb', '--serial', 'R5CW40EE9QK', '--output', str(output),
                '--configuration', 'rear-portrait', '--idle-only']
        with patch.object(runner.subprocess, 'run', side_effect=fake_run), \
             patch.object(runner, 'ThermalGuard', Guard), \
             patch.object(runner.time, 'sleep', Mock()), \
             patch.object(sys, 'argv', argv):
            runner.main()
        return issued, json.loads((output/'session.json').read_text()), state['removed']

    def test_clean_session_removes_the_stale_task_before_observing_idle(self):
        issued, metadata, removed = self.run_session([STALE_DUMP])
        self.assertEqual('IDLE_VERIFIED_NO_CAMERA_LAUNCH', metadata['collectionStatus'])
        self.assertEqual(1, removed)
        removed_at = next(index for index, args in enumerate(issued)
                          if args[:4] == ('shell', 'am', 'task', 'remove'))
        observed_at = next(index for index, args in enumerate(issued)
                           if args[:3] == ('shell', 'dumpsys', 'media.camera'))
        self.assertLess(removed_at, observed_at, 'task cleanup must precede camera-idle observation')
        self.assertFalse(any(args[:3] == ('shell', 'am', 'start') for args in issued))

    def test_already_clean_device_issues_no_task_removal(self):
        issued, metadata, removed = self.run_session([CLEAN_DUMP])
        self.assertEqual('IDLE_VERIFIED_NO_CAMERA_LAUNCH', metadata['collectionStatus'])
        self.assertEqual(0, removed)
        self.assertFalse(any(args[2:4] == ('task', 'remove') for args in issued))

    def test_process_recreated_after_cleanup_blocks_the_session(self):
        with self.assertRaises(RuntimeError):
            self.run_session([STALE_DUMP], pid='30236\n')
        self.assertFalse(any(args[:3] == ('shell', 'am', 'start') for args in self.issued))
        self.assertFalse(any(args[:2] == ('shell', 'uiautomator') for args in self.issued))
        self.assertEqual(1, sum(args[2:4] == ('task', 'remove') for args in self.issued))

    def test_undocumented_task_removal_stops_before_any_device_observation(self):
        with self.assertRaisesRegex(RuntimeError, 'cannot be cleaned safely'):
            self.run_session([STALE_DUMP], help_text=HELP_NO_REMOVE)

    def test_session_records_failure_without_launching(self):
        directory = tempfile.TemporaryDirectory(dir=ROOT/'device-evidence')
        self.addCleanup(directory.cleanup)
        output = Path(directory.name)/'session'
        argv = ['runner', '--adb', 'adb', '--serial', 'R5CW40EE9QK', '--output', str(output),
                '--configuration', 'rear-portrait', '--idle-only']
        def fake_run(command, **kwargs):
            args = tuple(command[3:]) if command[:3] == ['adb', '-s', 'R5CW40EE9QK'] else tuple(command[2:])
            text = {'get-state': 'device', 'SM-S918B': 'SM-S918B'}.get(args[-1], '')
            if args[:2] == ('shell', 'pidof'):
                text = '30236'
            elif args[:3] == ('shell', 'dumpsys', 'package'):
                text = 'android.permission.CAMERA: granted=true'
            elif args[:2] == ('shell', 'getprop'):
                text = 'SM-S918B'
            elif args[:3] == ('shell', 'dumpsys', 'media.camera'):
                text = IDLE_CAMERA
            elif args[:3] == ('shell', 'dumpsys', 'activity'):
                text = CLEAN_DUMP
            return Mock(stdout=text, stderr='', returncode=0)
        with patch.object(runner.subprocess, 'run', side_effect=fake_run), \
             patch.object(runner, 'ThermalGuard', Guard), \
             patch.object(runner.time, 'sleep', Mock()), \
             patch.object(sys, 'argv', argv):
            with self.assertRaises(RuntimeError):
                runner.main()
        metadata = json.loads((output/'session.json').read_text())
        self.assertEqual('INCOMPLETE', metadata['collectionStatus'])
        self.assertIn('reappeared after task cleanup', metadata['failureReason'])


if __name__ == '__main__':
    unittest.main()
