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

    def replay(self, samples):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        ticks = iter(t for t, _ in samples)
        texts = iter(dump(0, c) for _, c in samples)
        stopped = []
        guard = ThermalGuard(lambda: next(texts), lambda: stopped.append(True),
                             Path(directory.name)/'thermal.json', lambda: next(ticks))
        return guard, stopped

    def test_recorded_face_trigger_uses_in_window_countdown_baseline(self):
        # Sanitized numeric replay of all 42 retained parent samples, no device IO.
        recorded = [
            (5064838.4115209, 34.5),
            (5064840.4812207, 34.5),
            (5064842.5571079, 34.5),
            (5064844.6350429, 34.6),
            (5064846.7078764, 34.6),
            (5064848.785936, 34.6),
            (5064850.8650037, 34.6),
            (5064852.9413662, 34.6),
            (5064855.0149991, 34.6),
            (5064857.090508, 34.6),
            (5064859.1457656, 34.6),
            (5064861.2127865, 34.6),
            (5064863.2959041, 34.6),
            (5064865.3621408, 34.6),
            (5064867.442304, 34.6),
            (5064869.5218074, 34.6),
            (5064871.6020587, 34.6),
            (5064873.6975011, 34.6),
            (5064875.789916, 34.6),
            (5064877.8770784, 34.6),
            (5064879.9527073, 34.6),
            (5064882.0505797, 34.6),
            (5064884.1582771, 34.5),
            (5064886.2430628, 34.5),
            (5064888.3236632, 34.5),
            (5064890.3907815, 34.5),
            (5064892.4930286, 34.5),
            (5064894.5621182, 34.5),
            (5064896.6452623, 34.5),
            (5064898.7366072, 34.5),
            (5064900.8120079, 34.5),
            (5064902.9089709, 34.5),
            (5064904.9759002, 35.1),
            (5064907.0559421, 35.1),
            (5064909.1167141, 35.1),
            (5064911.2012183, 35.1),
            (5064913.2971002, 35.9),
            (5064915.3828576, 35.9),
            (5064917.4638494, 35.9),
            (5064919.5523886, 35.9),
            (5064921.6341178, 35.9),
            (5064923.6914649, 36.6),
        ]
        guard, stopped = self.replay(recorded)
        for _ in recorded[:-1]: guard.poll()
        with self.assertRaisesRegex(RuntimeError, 'Rapid skin'): guard.poll()
        trigger = guard.samples[-1]
        baseline = next(s for s in guard.samples if 0 < trigger['time']-s['time'] <= 30)
        self.assertEqual(5064894.5621182, baseline['time'])
        self.assertAlmostEqual(29.1293467, trigger['time']-baseline['time'], places=6)
        self.assertAlmostEqual(2.1, trigger['skinC']-baseline['skinC'])
        self.assertEqual([True], stopped)

    def test_thirty_second_window_inclusive_and_expired_baseline_excluded(self):
        for elapsed, abort in [(29.999, True), (30, True), (30.001, False)]:
            with self.subTest(elapsed=elapsed):
                guard, stopped = self.replay([(0, 34.5), (elapsed, 36.6)])
                guard.poll()
                if abort:
                    with self.assertRaises(RuntimeError): guard.poll()
                else: guard.poll()
                self.assertEqual(int(abort), len(stopped))

    def test_equal_timestamp_is_not_a_positive_elapsed_baseline(self):
        guard, stopped = self.replay([(10, 34.5), (10, 36.6)])
        guard.poll(); guard.poll()
        self.assertEqual([], stopped)

    def test_child_monitor_later_baseline_does_not_reproduce_parent_rise(self):
        guard, stopped = self.replay([(5064907.6600977, 35.1),
                                     (5064924.2822727, 36.6),
                                     (5064936.8007807, 36.7)])
        for _ in range(3): guard.poll()
        self.assertEqual([], stopped)

    def test_skin_sensor_selected_without_confusing_ap_or_battery(self):
        text = ('Thermal Status: 0\n'
                'Temperature{mValue=53.2, mType=0, mName=AP, mStatus=0}\n'
                'Temperature{mValue=33.0, mType=2, mName=BAT, mStatus=0}\n'
                'Temperature{mValue=35.9, mType=3, mName=SKIN, mStatus=0}\n'
                'Temperature{mValue=35.9, mType=3, mName=SKIN, mStatus=0}')
        self.assertEqual({'status': 0, 'skinC': 35.9}, thermal_sample(text))

if __name__ == '__main__': unittest.main()
