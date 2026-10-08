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

if __name__ == '__main__':unittest.main()
