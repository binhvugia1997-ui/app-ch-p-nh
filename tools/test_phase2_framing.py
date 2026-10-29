"""Host-only runner integration: all ADB and camera operations are mocked."""
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('solo', Path(__file__).with_name('run-phase2-solo.py'))
solo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(solo)


class FramingTests(unittest.TestCase):
    def test_preview_only_launch_sets_geometry_without_rebind_or_collection(self):
        self.run_framing(True)

    def test_unconfirmed_preview_only_mode_is_rejected_and_stopped(self):
        self.run_framing(False)

    def test_countdown_finishes_before_camera_launch(self):
        self.run_framing(True, framing=False)

    def test_reactivation_during_cooldown_aborts_before_any_launch(self):
        self.run_framing(True, unexpected=True)

    def test_idle_only_never_launches_camera(self):
        self.run_framing(True, idle_only=True)

    def test_stop_failure_does_not_skip_guard_shutdown(self):
        self.run_framing(True, stop_failure=True)

    def test_face_only_confirmed_before_single_collection(self):
        self.run_framing(True, framing=False, face_only=True)

    def test_face_only_unconfirmed_aborts_and_cleans_up(self):
        self.run_framing(False, framing=False, face_only=True)

    def test_face_only_portrait_is_supported_without_rotation_override(self):
        self.run_framing(True, framing=False, face_only=True, orientation='portrait')

    def test_face_only_landscape_request_portrait_display_aborts_and_preserves_evidence(self):
        self.run_framing(True, framing=False, face_only=True, mismatch=True)

    def test_face_only_portrait_request_landscape_display_also_aborts(self):
        self.run_framing(True, framing=False, face_only=True, orientation='portrait', mismatch=True)

    def test_transient_samsung_owner_does_not_abort_preflight(self):
        self.run_framing(True, framing=False, face_only=True, transient=True)

    def test_persistent_samsung_owner_prevents_any_launch(self):
        self.run_framing(True, framing=False, face_only=True, persistent=True)

    def test_thermal_interruption_in_availability_wait_prevents_launch(self):
        self.run_framing(True, framing=False, face_only=True, thermal_abort=True)

    def run_framing(self, supported, framing=True, unexpected=False, idle_only=False, stop_failure=False, face_only=False, orientation='landscape', mismatch=False, transient=False, persistent=False, thermal_abort=False):
        calls = []
        now = [0.0]
        def sleep(seconds):
            calls.append(['SLEEP',seconds])
            now[0] += seconds
        xml = '<hierarchy><node bounds="[0,0][1544,720]"><node text="Framing preview (analysis off)" /></node></hierarchy>'
        if not supported:
            xml = '<hierarchy><node bounds="[0,0][1544,720]" /></hierarchy>'
        if (orientation == 'portrait') != mismatch:
            xml = xml.replace('[1544,720]','[720,1544]')
        xml = xml.replace('<hierarchy>','<hierarchy rotation="0">')
        camera_reads = 0
        def run(args, **kwargs):
            nonlocal camera_reads
            calls.append(args)
            if stop_failure and 'force-stop' in args:
                raise TimeoutError('stop failed')
            if 'get-state' in args: value = 'device'
            elif 'package' in args: value = 'android.permission.CAMERA: granted=true'
            elif 'media.camera' in args:
                camera_reads += 1
                value = 'Active Camera Clients:\n[]\nAllowed user IDs: 0'
                if persistent or (transient and camera_reads in (2,18)):
                    value = 'Active Camera Clients: [(Camera ID: 1, PID: 8505, Client Package Name: com.samsung.android.smartface,)]'
                if unexpected and camera_reads == 2:
                    value = 'Active Camera Clients: [(Camera ID: 0, PID: 6692, Client Package Name: com.aiphotographer.app,)]'

            elif 'pidof' in args: value = '123' if face_only and any('start' in c for c in calls) and not any('force-stop' in c for c in calls[next(i for i,c in enumerate(calls) if 'start' in c)+1:]) else ''
            elif 'logcat' in args: value = 'mode=FACE_ONLY; model=disabled_face_only' if supported else ''
            elif 'cat' in args: value = xml
            elif 'screencap' in args: value = b'\x89PNG\r\n\x1a\nmock'
            else: value = ''
            return SimpleNamespace(stdout=value,stderr='',returncode=0)
        class Guard:
            failure = None
            samples = [{'status':0}]
            def __init__(self,*args): pass
            def start(self): pass
            checks = 0
            def check(self):
                self.checks += 1
                if thermal_abort and self.checks >= 17:
                    raise RuntimeError('thermal interruption during availability wait')
            def close(self, **kwargs): calls.append(['GUARD_CLOSE'])
        with tempfile.TemporaryDirectory(dir=solo.ROOT/'device-evidence') as parent:
            output = Path(parent)/'new-framing'
            argv = ['solo','--adb','fake-adb','--serial','fake','--configuration','front-landscape',
                    '--framing-only','--aspect','16:9','--seconds','30','--output',str(output)]
            if idle_only:
                argv.append('--idle-only')
            if not framing:
                argv.remove('--framing-only')
                argv[argv.index('front-landscape')] = 'rear-landscape'
                argv[argv.index('16:9')] = '4:3'
            if orientation == 'portrait':
                argv[argv.index('rear-landscape')] = 'rear-portrait'
            if face_only:
                argv.append('--face-only')
            with patch.object(solo.sys,'argv',argv), patch.object(solo.subprocess,'run',side_effect=run), \
                 patch.object(solo,'ThermalGuard',Guard), patch.object(solo.time,'sleep',side_effect=sleep), \
                 patch.object(solo.time,'monotonic',side_effect=lambda:now[0]), \
                 patch.object(solo.subprocess,'Popen') as popen:
                popen.return_value.wait.return_value = 0
                if persistent:
                    with self.assertRaisesRegex(RuntimeError,'camera cleanup'):
                        solo.main()
                elif thermal_abort:
                    with self.assertRaisesRegex(RuntimeError,'thermal interruption'):
                        solo.main()
                elif stop_failure:
                    with self.assertRaisesRegex(RuntimeError,'camera cleanup'):
                        solo.main()
                elif unexpected:
                    with self.assertRaisesRegex(RuntimeError,'Unexpected project activity'):
                        solo.main()
                elif mismatch:
                    with self.assertRaisesRegex(RuntimeError,'startup inference may already have run'):
                        solo.main()
                elif face_only and not supported:
                    with self.assertRaisesRegex(RuntimeError,'Face-only mode not confirmed'):
                        solo.main()
                elif supported:
                    solo.main()
                else:
                    with self.assertRaisesRegex(RuntimeError, 'Preview-only mode not confirmed'):
                        solo.main()
            if persistent or thermal_abort:
                self.assertFalse(any('start' in c for c in calls))
                popen.assert_not_called()
                self.assertIn(['GUARD_CLOSE'],calls)
                meta=json.loads((output/'session.json').read_text())
                self.assertEqual('INCOMPLETE',meta['collectionStatus'])
                self.assertIn('timeout' if persistent else 'thermal interruption',meta['failureReason'])
                self.assertFalse(any('force-stop' in c and 'com.samsung.android.smartface' in c for c in calls))
                return
            if stop_failure or idle_only:
                self.assertFalse(any('start' in c for c in calls))
                self.assertIn(['GUARD_CLOSE'],calls)
                if stop_failure:
                    self.assertIn('cleanupErrors',(output/'session.json').read_text())
                else:
                    self.assertIn('IDLE_VERIFIED_NO_CAMERA_LAUNCH',(output/'session.json').read_text())
                popen.assert_not_called()
                return
            if unexpected:
                self.assertFalse(any('start' in c for c in calls))
                self.assertIn('INCOMPLETE',(output/'session.json').read_text())
                self.assertIn('6692',(output/'camera-idle.jsonl').read_text())
                popen.assert_not_called()
                return
            if mismatch:
                popen.assert_not_called()
                self.assertIn(['GUARD_CLOSE'],calls)
                self.assertTrue(any('force-stop' in c for c in calls))
                meta = json.loads((output/'session.json').read_text())
                self.assertEqual('landscape' if orientation == 'portrait' else 'portrait',meta['displayedOrientation'])
                self.assertEqual(orientation,meta['expectedOrientation'])
                self.assertEqual('INCOMPLETE',meta['collectionStatus'])
                self.assertIn('collector not started',meta['failureReason'])
                self.assertTrue((output/'startup-ui.xml').exists())
                self.assertTrue((output/'rotation-settings-before-launch.json').exists())
                self.assertFalse(any('settings' in c and 'put' in c for c in calls))
                return
            if face_only and not supported:
                popen.assert_not_called()
                self.assertIn('INCOMPLETE',(output/'session.json').read_text())
                self.assertIn(['GUARD_CLOSE'],calls)
                return
            if not framing:
                if face_only:
                    launch = next(c for c in calls if 'start' in c)
                    self.assertEqual('true',launch[launch.index('faceOnly')+1])
                self.assertFalse(any('settings' in c and 'put' in c for c in calls))
                popen.assert_called_once()
                self.assertFalse((output/'framing.png').exists())
                launch_index = next(i for i,c in enumerate(calls) if 'start' in c)
                countdowns = [i for i,c in enumerate(calls) if c == ['SLEEP',1]]
                self.assertEqual(15,len(countdowns))
                self.assertTrue(all(i < launch_index for i in countdowns))
                return
            if not supported:
                self.assertFalse((output/'framing.png').exists())
                self.assertIn('INCOMPLETE',(output/'session.json').read_text())
                self.assertFalse(any('screencap' in c for c in calls))
                self.assertTrue(any('force-stop' in c for c in calls))
                popen.assert_not_called()
                return
            self.assertTrue((output/'framing.png').exists())
            self.assertIn('FRAMING_ONLY_NO_TEST_COLLECTION',(output/'session.json').read_text())
            popen.assert_not_called()
        launch = next(c for c in calls if 'start' in c)
        for name in ['framingOnly','framingFront','framingWide']:
            self.assertEqual('true',launch[launch.index(name)+1])
        self.assertFalse(any('input' in c for c in calls))
        self.assertTrue(any('force-stop' in c for c in calls))

if __name__ == '__main__': unittest.main()
