"""Host-only cleanup regressions; subprocesses, ADB and clocks are mocked."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import phase2_process as owned
from phase2_thermal import ThermalGuard

spec = importlib.util.spec_from_file_location('cleanup_collector', Path(__file__).with_name('collect-phase2-device.py'))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class ProcessTests(unittest.TestCase):
    def test_success_waits_with_guard_checks_and_keeps_collector_evidence(self):
        process = Mock()
        process.wait.side_effect = [subprocess.TimeoutExpired('child', .25), 0]
        check, cancel, stop = Mock(), Mock(), Mock()
        owned.wait_collector(process, check, cancel, stop, 30)
        self.assertEqual(3, check.call_count)
        cancel.assert_not_called(); stop.assert_not_called()

    def test_parent_checks_guard_while_child_runs_then_cancels_and_stops_project(self):
        process = Mock()
        process.wait.side_effect = [subprocess.TimeoutExpired('child', .25), 0]
        check = Mock(side_effect=[None, RuntimeError('thermal stop')])
        cancel, stop = Mock(), Mock()
        with self.assertRaisesRegex(RuntimeError, 'thermal stop'):
            owned.wait_collector(process, check, cancel, stop, 30)
        cancel.assert_called_once(); stop.assert_called_once()
        self.assertEqual(90, process.wait.call_args.kwargs['timeout'])

    def test_collector_deadline_escalates_only_owned_tree(self):
        process = Mock()
        process.wait.side_effect = subprocess.TimeoutExpired('child', 90)
        cancel, stop = Mock(), Mock()
        with patch.object(owned, 'kill_owned_tree') as kill:
            with self.assertRaisesRegex(TimeoutError, 'bounded collection'):
                owned.wait_collector(process, Mock(), cancel, stop, 30, clock=Mock(side_effect=[0,76]))
        kill.assert_called_once_with(process)
        cancel.assert_called_once(); stop.assert_called_once()

    def test_stop_failure_does_not_skip_child_shutdown(self):
        process = Mock()
        process.wait.return_value = 0
        with self.assertRaisesRegex(RuntimeError, 'stop failed'):
            owned.wait_collector(process, Mock(side_effect=RuntimeError('abort')), Mock(),
                                 Mock(side_effect=RuntimeError('stop failed')), 30)
        process.wait.assert_called_once_with(timeout=90)

    def test_windows_forced_cleanup_targets_only_owned_pid_tree(self):
        process = Mock(pid=12345); process.poll.return_value = None
        with patch.object(owned.os, 'name', 'nt'), patch.object(owned.subprocess, 'run') as run:
            owned.kill_owned_tree(process)
        self.assertEqual(['taskkill','/PID','12345','/T','/F'], run.call_args.args[0])
        self.assertEqual(10, run.call_args.kwargs['timeout'])
        process.wait.assert_called_once_with(timeout=5)

    def test_logcat_terminate_timeout_escalates_to_kill_and_bounded_wait(self):
        process = Mock(); process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired('logcat',5),0]
        owned.stop_logcat(process)
        process.terminate.assert_called_once(); process.kill.assert_called_once()
        self.assertEqual([5,5], [x.kwargs['timeout'] for x in process.wait.call_args_list])

    def test_trace_ownership_and_bounded_release(self):
        adb = Mock(side_effect=['OWNED','', 'OWNED','GONE'])
        sleep = Mock()
        owned.stop_trace(adb,'123','abc',sleep)
        self.assertTrue(any('kill -INT 123' in c.args[-1] for c in adb.call_args_list))
        sleep.assert_called_once_with(.25)
        self.assertIn('! -d /proc/123', adb.call_args_list[0].args[-1])
        for state in ['FOREIGN', 'UNKNOWN']:
            adb = Mock(return_value=state)
            with self.assertRaisesRegex(RuntimeError,'ownership'):
                owned.stop_trace(adb,'123','abc',sleep)
            adb.assert_called_once()
        adb = Mock(return_value='OWNED')
        with self.assertRaisesRegex(RuntimeError,'did not stop'):
            owned.stop_trace(adb,'123','abc',Mock())
        self.assertEqual(7, adb.call_count)

    def test_guard_shutdown_is_not_silently_declared_complete_if_thread_alive(self):
        guard = ThermalGuard(Mock(),Mock(),Path('unused'))
        guard.thread = Mock(); guard.thread.is_alive.return_value = True
        with self.assertRaisesRegex(RuntimeError,'shutdown unverified'):
            guard.close()
        guard.thread.join.assert_called_once_with(timeout=15)
        with self.assertRaises(RuntimeError): guard.check()


class CollectorTests(unittest.TestCase):
    def run_collector(self, cancel=False, log_failure=False, trace=False, bad_duration=False, log_exit=False):
        now = [0.0]
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1]/'device-evidence') as folder:
            base = Path(folder); output = base/'new'; token = base/'cancel.json'
            calls = []
            def run(args, **kwargs):
                calls.append(args)
                if 'thermalservice' in args:
                    value = b'Thermal Status: 0\nTemperature{mValue=35, mType=3, mName=SKIN, mStatus=0}'
                elif 'media.camera' in args: value = b'Active Camera Clients: []'
                elif 'pidof' in args: value = b'' if len(args)>6 else b'123'
                elif 'perfetto' in args: value = b'123'
                else: value = b''
                return SimpleNamespace(stdout=value,stderr=b'',returncode=0)
            def sleep(seconds):
                now[0] += seconds
                if cancel and now[0] >= .5: token.write_text('cancel',encoding='utf-8')
            class Guard:
                def __init__(self,*args): pass
                def start(self): pass
                def check(self): pass
                def close(self,**kwargs): pass
            log = Mock(); log.poll.return_value = 1 if log_exit else None; log.wait.return_value = 0
            argv = ['collector','--adb','fake','--serial','fake','--output',str(output),
                    '--seconds','30','--cancel-file',str(token)]
            if trace:
                config = base/'trace.pbtxt'
                config.write_text('duration_ms: '+('0' if bad_duration else '30000'),encoding='utf-8')
                argv += ['--trace-config',str(config)]
            with patch.object(collector.sys if hasattr(collector,'sys') else __import__('sys'),'argv',argv), \
                 patch.object(collector.subprocess,'run',side_effect=run), \
                 patch.object(collector.subprocess,'Popen',return_value=log), \
                 patch.object(collector,'ThermalGuard',Guard), \
                 patch.object(collector.time,'monotonic',side_effect=lambda:now[0]), \
                 patch.object(collector.time,'sleep',side_effect=sleep), \
                 patch.object(collector,'stop_logcat',side_effect=RuntimeError('log cleanup failed') if log_failure else None) as stop_log, \
                 patch.object(collector,'stop_trace') as stop_trace:
                if cancel or log_failure or bad_duration or log_exit:
                    with self.assertRaises(RuntimeError): collector.main()
                else: collector.main()
            report = json.loads((output/'summary.json').read_text())
            self.assertEqual('INCOMPLETE' if cancel or log_failure or bad_duration or log_exit else 'COMPLETE',report['measurementStatus'])
            stop_log.assert_called_once_with(log)
            if cancel:
                self.assertLess(now[0],1)
                self.assertEqual(1,len(json.loads((output/'samples.json').read_text())))
            if log_failure:
                self.assertTrue(report['cleanupErrors'])
                stop_trace.assert_called_once()
            if bad_duration:
                self.assertFalse(any('push' in c for c in calls))
            if cancel or log_failure or bad_duration or log_exit:
                self.assertTrue(any('force-stop' in c for c in calls))
                self.assertTrue((output/'cleanup-camera-idle.jsonl').exists())
            return report

    def test_success_preserves_regular_diagnostic_samples(self): self.run_collector()
    def test_cooperative_cancellation_interrupts_sleep_and_preserves_partial_summary(self): self.run_collector(cancel=True)
    def test_logcat_cleanup_failure_does_not_skip_trace_or_summary(self): self.run_collector(log_failure=True,trace=True)
    def test_unbounded_trace_config_rejected_before_push_or_start(self): self.run_collector(trace=True,bad_duration=True)
    def test_early_logcat_exit_marks_window_incomplete_and_stops_project(self): self.run_collector(log_exit=True)


if __name__ == '__main__': unittest.main()
