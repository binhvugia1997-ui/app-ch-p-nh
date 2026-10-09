import tempfile
from pathlib import Path
import unittest
from phase2_thermal import ThermalGuard, thermal_sample


def dump(status=0, skin=35):
    return f'Thermal Status: {status}\nTemperature{{mValue={skin}, mType=3, mName=SKIN, mStatus={status}}}'


class ThermalTests(unittest.TestCase):
    def test_preflight_rejects_every_non_normal_status_and_missing_data(self):
        for text in [dump(s) for s in range(1, 7)] + ['', 'Thermal Status: 0']:
            with tempfile.TemporaryDirectory() as d:
                stopped = []
                guard = ThermalGuard(lambda: text, lambda: stopped.append(True), Path(d)/'thermal.json')
                with self.assertRaises(RuntimeError): guard.poll(preflight=True)
                self.assertEqual([True], stopped)

    def test_severe_stops_and_failure_remains_latched(self):
        with tempfile.TemporaryDirectory() as d:
            stopped = []
            guard = ThermalGuard(lambda: dump(3), lambda: stopped.append(True), Path(d)/'thermal.json')
            with self.assertRaises(RuntimeError): guard.poll()
            with self.assertRaises(RuntimeError): guard.check()
            self.assertEqual([True], stopped)

    def test_rapid_rise_stops_even_with_normal_android_status(self):
        with tempfile.TemporaryDirectory() as d:
            ticks = iter([0, 2]); texts = iter([dump(0,35),dump(0,37)])
            stopped = []
            guard = ThermalGuard(lambda: next(texts), lambda: stopped.append(True), Path(d)/'thermal.json',lambda: next(ticks))
            guard.poll(preflight=True)
            with self.assertRaises(RuntimeError): guard.poll()
            self.assertEqual([True], stopped)

    def test_read_failure_stops_and_final_check_is_recorded(self):
        with tempfile.TemporaryDirectory() as d:
            stopped = []
            def broken(): raise TimeoutError('ADB timeout')
            guard = ThermalGuard(broken, lambda: stopped.append(True), Path(d)/'thermal.json')
            with self.assertRaises(TimeoutError): guard.poll()
            self.assertEqual([True], stopped)
            with self.assertRaises(RuntimeError): guard.close(final_sample=True)

    def test_normal_stable_samples_allowed(self):
        self.assertEqual({'status':0,'skinC':36.2},thermal_sample(dump(0,36.2)))

if __name__ == '__main__': unittest.main()
