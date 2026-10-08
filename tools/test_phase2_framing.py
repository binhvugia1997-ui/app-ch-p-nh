"""Host-only runner integration: all ADB and camera operations are mocked."""
import importlib.util
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

    def run_framing(self, supported, framing=True):
        calls = []
        xml = '<hierarchy><node bounds="[0,0][1544,720]"><node text="Framing preview (analysis off)" /></node></hierarchy>'
        if not supported:
            xml = '<hierarchy><node bounds="[0,0][1544,720]" /></hierarchy>'
        def run(args, **kwargs):
            calls.append(args)
            if 'get-state' in args: value = 'device'
            elif 'package' in args: value = 'android.permission.CAMERA: granted=true'
            elif 'cat' in args: value = xml
            elif 'screencap' in args: value = b'\x89PNG\r\n\x1a\nmock'
            else: value = ''
            return SimpleNamespace(stdout=value)
        class Guard:
            failure = None
            samples = [{'status':0}]
            def __init__(self,*args): pass
            def start(self): pass
            def check(self): pass
            def close(self, **kwargs): pass
        with tempfile.TemporaryDirectory(dir=solo.ROOT/'device-evidence') as parent:
            output = Path(parent)/'new-framing'
            argv = ['solo','--adb','fake-adb','--serial','fake','--configuration','front-landscape',
                    '--framing-only','--aspect','16:9','--seconds','30','--output',str(output)]
            if not framing:
                argv.remove('--framing-only')
                argv[argv.index('front-landscape')] = 'rear-landscape'
                argv[argv.index('16:9')] = '4:3'
            with patch.object(solo.sys,'argv',argv), patch.object(solo.subprocess,'run',side_effect=run), \
                 patch.object(solo,'ThermalGuard',Guard), patch.object(solo.time,'sleep',side_effect=lambda seconds: calls.append(['SLEEP',seconds])), \
                 patch.object(solo.subprocess,'Popen') as popen:
                popen.return_value.wait.return_value = 0
                if supported:
                    solo.main()
                else:
                    with self.assertRaisesRegex(RuntimeError, 'Preview-only mode not confirmed'):
                        solo.main()
            if not framing:
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
