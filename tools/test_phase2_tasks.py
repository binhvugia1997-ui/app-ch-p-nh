"""Host-only regressions for project task cleanup and post-cleanup quiescence; no ADB."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, call

from phase2_tasks import ProjectTasks, await_quiescence, project_tasks

APP = 'com.aiphotographer.app'
PACKAGES = (APP, APP + '.test', 'com.aiphotographer.perception.mediapipe.test')

CLEAN = '''ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
Display #0 (activities from top to bottom):
  * Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0 visible=true mode=fullscreen}
    * ActivityRecord{9f8e7b u0 com.android.launcher3/.uioverrides.QuickstepLauncher t120}
  Recent tasks:
  * Recent #0: Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0}
'''

PROJECT = '''ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
Display #0 (activities from top to bottom):
  * Task{8d9c1a2 #4782 type=standard A=10531:com.aiphotographer.app U=0 visible=true visibleRequested=false mode=fullscreen}
    userLeaving=false
    * ActivityRecord{1a2b3c4 u0 com.aiphotographer.app/.MainActivity t4782}
  * Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0 visible=false}
    * ActivityRecord{9f8e7b u0 com.android.launcher3/.uioverrides.QuickstepLauncher t120}
  Recent tasks:
  * Recent #0: Task{8d9c1a2 #4782 type=standard A=10531:com.aiphotographer.app U=0 visible=false}
  * Recent #1: Task{7c1b2aa #120 type=standard A=10432:com.android.launcher3 U=0}
'''

TWO_PROJECT = PROJECT.replace(
    '  Recent tasks:',
    '''  * Task{5e4f3a1 #4790 type=standard A=10531:com.aiphotographer.app U=0 visible=false}
    * ActivityRecord{2b3c4d5 u0 com.aiphotographer.app/.MainActivity t4790}
  Recent tasks:''')

HELP = '''Activity manager task commands:
  task lock <TASK_ID>: bring <TASK_ID> to the front and don't allow other tasks to run.
  task resizeable <TASK_ID> [0|1|2|3]: change the resizeable mode of the task.
  task focus <TASK_ID>: move the task with the given id to the top of the stack.
  task remove <TASK_ID>: remove the task with the given id from the recents.
'''


class InventoryTests(unittest.TestCase):
    def test_project_task_found_once_and_other_tasks_ignored(self):
        self.assertEqual([{'taskId': 4782, 'package': APP}], project_tasks(PROJECT, PACKAGES))

    def test_clean_device_reports_nothing(self):
        self.assertEqual([], project_tasks(CLEAN, PACKAGES))

    def test_unattributable_task_is_never_claimed(self):
        dump = '  * Task{fff #77 type=standard U=0 visible=false}\n    * ActivityRecord{x u0 t77}\n'
        self.assertEqual([], project_tasks(dump, PACKAGES))

    def test_unrelated_task_that_embeds_a_project_activity_is_not_claimed(self):
        dump = '''  * Task{a1b2c #300 type=standard A=10777:com.example.gallery U=0 visible=true}
    * ActivityRecord{ccc u0 com.example.gallery/.Picker t300}
    * ActivityRecord{ddd u0 com.aiphotographer.app/.MainActivity t300}
'''
        self.assertEqual([], project_tasks(dump, PACKAGES))

    def test_every_project_task_is_reported_in_id_order(self):
        self.assertEqual([{'taskId': 4782, 'package': APP}, {'taskId': 4790, 'package': APP}],
                         project_tasks(TWO_PROJECT, PACKAGES))

    def test_ownership_is_exact_package_membership_not_a_prefix(self):
        dump = PROJECT.replace('A=10531:com.aiphotographer.app', 'A=10531:com.aiphotographer.app.other')
        self.assertEqual([], project_tasks(dump, PACKAGES))
        self.assertEqual([{'taskId': 4782, 'package': APP + '.test'}],
                         project_tasks(PROJECT.replace('A=10531:com.aiphotographer.app',
                                                       'A=10531:com.aiphotographer.app.test'), PACKAGES))


class CleanupTests(unittest.TestCase):
    def make(self, dumps, help_text=HELP, run_error=None):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.journal = Path(directory.name)/'tasks.jsonl'
        self.dumps = list(dumps)
        self.reads = 0
        def read():
            self.reads += 1
            return self.dumps[min(self.reads - 1, len(self.dumps) - 1)]
        self.am = Mock(side_effect=run_error, return_value=help_text)
        return ProjectTasks(read, self.am, self.journal, PACKAGES, sleep=Mock())

    def removal_calls(self):
        return [c for c in self.am.call_args_list if len(c.args) > 1]

    def test_clean_device_issues_no_task_command(self):
        tasks = self.make([CLEAN])
        self.assertEqual([], tasks.clean())
        self.am.assert_not_called()
        recorded = [json.loads(line) for line in self.journal.read_text().splitlines()]
        self.assertEqual([[]], [entry['tasks'] for entry in recorded])
        self.assertTrue(all(isinstance(entry['time'], float) for entry in recorded))

    def test_project_task_removed_with_a_single_verifiable_id(self):
        tasks = self.make([PROJECT, CLEAN])
        self.assertEqual([4782], tasks.clean())
        self.assertEqual([call('task'), call('task', 'remove', '4782')], self.am.call_args_list)

    def test_multiple_project_tasks_removed_in_order(self):
        tasks = self.make([TWO_PROJECT, CLEAN])
        self.assertEqual([4782, 4790], tasks.clean())
        self.assertEqual(['4782', '4790'], [c.args[-1] for c in self.removal_calls()])

    def test_removal_never_passes_anything_but_an_integer_task_id(self):
        tasks = self.make([TWO_PROJECT, CLEAN])
        tasks.clean()
        for called in self.removal_calls():
            self.assertEqual(('task', 'remove'), called.args[:2])
            self.assertTrue(called.args[2].isdigit(), called.args[2])

    def test_surviving_task_fails_instead_of_being_accepted(self):
        tasks = self.make([PROJECT, PROJECT])
        with self.assertRaisesRegex(RuntimeError, 'survived cleanup'):
            tasks.clean()

    def test_undocumented_remove_subcommand_refuses_without_removing(self):
        tasks = self.make([PROJECT, CLEAN], help_text=HELP.replace('  task remove', '  task hide'))
        with self.assertRaisesRegex(RuntimeError, 'cannot be cleaned safely'):
            tasks.clean()
        self.assertEqual([call('task')], self.am.call_args_list)

    def test_unreadable_help_text_is_not_guessed(self):
        tasks = self.make([PROJECT, CLEAN], run_error=RuntimeError('Unknown command: task'))
        with self.assertRaisesRegex(RuntimeError, 'refusing to guess'):
            tasks.clean()
        self.assertEqual([call('task')], self.am.call_args_list)

    def test_require_clean_reports_the_stale_task(self):
        tasks = self.make([PROJECT])
        with self.assertRaisesRegex(RuntimeError, 'present with camera off.*4782'):
            tasks.require_clean()

    def test_journal_records_what_was_seen(self):
        tasks = self.make([PROJECT, CLEAN])
        tasks.clean()
        recorded = [json.loads(line) for line in self.journal.read_text().splitlines()]
        self.assertEqual([4782], [task['taskId'] for task in recorded[0]['tasks']])
        self.assertEqual([], recorded[1]['tasks'])


class QuiescenceTests(unittest.TestCase):
    def gate(self, tasks, pid='', samples=2, interval=2):
        self.sleeps = []
        self.checks = []
        return await_quiescence(lambda: self.checks.append(True), lambda: tasks,
                                lambda: pid, sleep=self.sleeps.append,
                                interval=interval, samples=samples)

    def test_two_separated_clean_samples_pass(self):
        state = self.gate([], samples=3)
        self.assertEqual({'projectPid': '', 'tasks': []}, state)
        self.assertEqual([2, 2], self.sleeps)
        self.assertEqual(3, len(self.checks))

    def test_process_recreated_after_cleanup_fails_on_the_first_sample(self):
        with self.assertRaisesRegex(RuntimeError, 'reappeared after task cleanup.*30236'):
            self.gate([], pid='30236')
        self.assertEqual([], self.sleeps)

    def test_task_recreated_after_cleanup_fails_on_the_first_sample(self):
        stale = [{'taskId': 4782, 'package': APP}]
        with self.assertRaisesRegex(RuntimeError, 'reappeared after task cleanup.*4782'):
            self.gate(stale)
        self.assertEqual([], self.sleeps)

    def test_second_sample_recreation_is_still_a_failure(self):
        states = iter([[], [{'taskId': 4782, 'package': APP}]])
        with self.assertRaises(RuntimeError):
            await_quiescence(lambda: None, lambda: next(states), lambda: '',
                             sleep=Mock(), interval=2, samples=2)

    def test_single_sample_is_not_evidence(self):
        with self.assertRaises(ValueError):
            await_quiescence(lambda: None, lambda: [], lambda: '', samples=1)


if __name__ == '__main__':
    unittest.main()
