import tempfile
from pathlib import Path
import unittest
from phase2_camera_idle import CameraIdle, active_clients

EMPTY = 'Active Camera Clients:\n[]\nAllowed user IDs: 0'
ACTIVE = 'Active Camera Clients:\n[(Camera ID: 0, Cost: 66, PID: 6692, Score: 0, State: 2User Id: 0, Client Package Name: com.aiphotographer.app, Conflicting Client Devices: {2, })]\nAllowed user IDs: 0'

class IdleTests(unittest.TestCase):
    def test_snapshot_identifies_actual_owner_and_ignores_historical_connections(self):
        self.assertEqual([{'cameraId':'0','pid':6692,'package':'com.aiphotographer.app'}],active_clients(ACTIVE))
        self.assertEqual([],active_clients(EMPTY+'\nCONNECT device 0 client for package com.aiphotographer.app'))

    def test_missing_or_unknown_telemetry_is_not_idle(self):
        for text in ['', 'Permission denied', 'Active Camera Clients: [unrecognized owner]']:
            with self.assertRaises(RuntimeError):active_clients(text)

    def test_cached_process_or_active_camera_rejects_idle(self):
        for camera,pid in [(ACTIVE,''),(EMPTY,'6692'),(ACTIVE.replace('com.aiphotographer.app','other.app'),'')]:
            with tempfile.TemporaryDirectory() as d:
                idle=CameraIdle(lambda:camera,lambda:pid,lambda:None,Path(d)/'idle.jsonl')
                with self.assertRaises(RuntimeError):idle.require_idle()

    def test_stop_waits_boundedly_for_release_and_does_not_launch(self):
        with tempfile.TemporaryDirectory() as d:
            dumps=iter([ACTIVE,EMPTY]);stops=[];sleeps=[]
            idle=CameraIdle(lambda:next(dumps),lambda:'',lambda:stops.append(True),Path(d)/'idle.jsonl',sleeps.append)
            self.assertEqual([],idle.stop_and_verify()['clients'])
            self.assertEqual([True],stops);self.assertEqual([.25],sleeps)

    def test_unexpected_reactivation_after_success_is_not_sticky_idle(self):
        with tempfile.TemporaryDirectory() as d:
            dumps=iter([EMPTY,ACTIVE])
            idle=CameraIdle(lambda:next(dumps),lambda:'',lambda:None,Path(d)/'idle.jsonl')
            idle.require_idle()
            with self.assertRaises(RuntimeError):idle.require_idle()

    def test_failed_cleanup_retries_only_bounded_number_of_times(self):
        with tempfile.TemporaryDirectory() as d:
            sleeps=[]
            idle=CameraIdle(lambda:ACTIVE,lambda:'6692',lambda:None,Path(d)/'idle.jsonl',sleeps.append)
            with self.assertRaisesRegex(RuntimeError,'cleanup'):idle.stop_and_verify()
            self.assertEqual(4,len(sleeps))

class AvailabilityTests(unittest.TestCase):
    SAMSUNG = ACTIVE.replace('com.aiphotographer.app','com.samsung.android.smartface')

    def setup_idle(self, directory, dumps, pid=''):
        self.now = 0.0
        self.stops = []
        self.dumps = list(dumps)
        def read():
            if len(self.dumps) > 1:
                return self.dumps.pop(0)
            return self.dumps[0]
        def sleep(seconds):
            self.now += seconds
        return CameraIdle(read,lambda:pid,lambda:self.stops.append(True),
                          Path(directory)/'idle.jsonl',sleep)

    def test_transient_samsung_releases_without_intervention(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[self.SAMSUNG,EMPTY,EMPTY,EMPTY])
            result=idle.wait_available(lambda:None,clock=lambda:self.now)
            self.assertEqual([],result['clients'])
            self.assertEqual(6,self.now)
            self.assertEqual([],self.stops)
            self.assertIn('smartface',(Path(d)/'idle.jsonl').read_text())

    def test_persistent_owner_times_out_with_identity_and_without_killing(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[self.SAMSUNG])
            with self.assertRaisesRegex(RuntimeError,'timeout.*smartface'):
                idle.wait_available(lambda:None,timeout=6,clock=lambda:self.now)
            self.assertEqual(6,self.now)
            self.assertEqual([],self.stops)

    def test_non_samsung_owners_are_not_bypassed(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[ACTIVE.replace('com.aiphotographer.app','other.camera')])
            with self.assertRaisesRegex(RuntimeError,'timeout.*other.camera'):
                idle.wait_available(lambda:None,timeout=6,clock=lambda:self.now)

    def test_busy_sample_resets_idle_streak(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[EMPTY,EMPTY,self.SAMSUNG,EMPTY,EMPTY,EMPTY])
            idle.wait_available(lambda:None,clock=lambda:self.now)
            self.assertEqual(10,self.now)

    def test_stable_idle_needs_multiple_separated_samples(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[EMPTY])
            checks=[]
            idle.wait_available(lambda:checks.append(self.now),clock=lambda:self.now)
            self.assertEqual(4,self.now)
            self.assertEqual(3,len((Path(d)/'idle.jsonl').read_text().splitlines()))
            self.assertEqual(6,len(checks))

    def test_project_process_or_project_camera_aborts_immediately(self):
        for camera,pid in [(EMPTY,'6692'),(ACTIVE,''),(ACTIVE.replace('com.aiphotographer.app','com.aiphotographer.app.test'),'')]:
            with tempfile.TemporaryDirectory() as d:
                idle=self.setup_idle(d,[camera],pid)
                with self.assertRaisesRegex(RuntimeError,'Unexpected project activity'):
                    idle.wait_available(lambda:None,clock=lambda:self.now)
                self.assertEqual(0,self.now)
                self.assertEqual([],self.stops)

    def test_thermal_guard_interrupts_busy_wait(self):
        from phase2_thermal import ThermalGuard
        from test_phase2_thermal import dump
        for next_sample in [dump(3,35),dump(0,37),dump(1,35)]:
            with tempfile.TemporaryDirectory() as d:
                idle=self.setup_idle(d,[self.SAMSUNG])
                readings=iter([dump(0,35),next_sample])
                stops=[]
                guard=ThermalGuard(lambda:next(readings),lambda:stops.append(True),
                                   Path(d)/'thermal.json',clock=lambda:self.now)
                def check():
                    self.now += 1
                    guard.poll(preflight=True)
                with self.assertRaises(RuntimeError):
                    idle.wait_available(check,clock=lambda:self.now)
                self.assertEqual([True],stops)
                self.assertEqual(2,self.now)
                self.assertEqual([],self.stops)

    def test_unreadable_ownership_fails_without_waiting(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,['Permission denied'])
            with self.assertRaisesRegex(RuntimeError,'telemetry unavailable'):
                idle.wait_available(lambda:None,clock=lambda:self.now)
            self.assertEqual(0,self.now)

    def test_slow_read_cannot_succeed_after_deadline(self):
        with tempfile.TemporaryDirectory() as d:
            idle=self.setup_idle(d,[EMPTY])
            read=idle.read_camera
            def slow():
                self.now+=3
                return read()
            idle.read_camera=slow
            with self.assertRaisesRegex(RuntimeError,'timeout'):
                idle.wait_available(lambda:None,timeout=5,clock=lambda:self.now)

if __name__ == '__main__':unittest.main()
