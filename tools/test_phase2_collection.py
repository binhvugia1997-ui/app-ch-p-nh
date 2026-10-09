"""Deterministic evidence-window tests; no ADB or human required."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('collector', Path(__file__).with_name('collect-phase2-device.py'))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


def line(t, count, session=1, pid=123):
    counters = ', '.join(f'{k}={count}' for k in collector.COUNTERS)
    return f' 12345.123 {pid} 124 I Phase2Baseline: PerceptionMetrics({counters}, elapsedSeconds={t}, sessionId={session})'


class CollectionTests(unittest.TestCase):
    def test_same_session_rates_and_exact_integer_counters(self):
        n = 2**53 + 1
        result = collector.metric_report(line(1,n)+'\n'+line(3,n+10), [{'pid':'123'}], True)
        self.assertEqual(n,result['first']['offered'])
        self.assertEqual(5,result['ratesHz']['poseCompleted'])

    def test_restart_reset_missing_pid_and_interruption_reject_window(self):
        pairs = [(line(1,1),line(3,2,session=2)), (line(1,1),line(3,2,pid=456)),
                 (line(3,2),line(1,1)), (line(1,2),line(3,1))]
        for a,b in pairs:
            self.assertTrue(collector.metric_report(a+'\n'+b,[{'pid':'123'}],True)['counterWindowInvalid'])
        for samples,complete in [([{'pid':''}],True),([{'pid':'123'}],False),([],True)]:
            r=collector.metric_report(line(1,1)+'\n'+line(3,2),samples,complete)
            self.assertTrue(r['counterWindowInvalid'])
            self.assertNotIn('ratesHz',r)

    def test_valid_face478_counter_is_optional_for_older_evidence(self):
        a=line(1,1)+', faceValid478=2'
        b=line(3,5)+', faceValid478=6'
        r=collector.metric_report(a+'\n'+b,[{'pid':'123'}],True)
        self.assertEqual(4,r['counterDeltas']['faceValid478'])
        old=collector.metric_report(line(1,1)+'\n'+line(3,5),[{'pid':'123'}],True)
        self.assertNotIn('faceValid478',old['counterDeltas'])
        bad=collector.metric_report(a+'\n'+b.replace('faceValid478=6','faceValid478=1'),[{'pid':'123'}],True)
        self.assertTrue(bad['counterWindowInvalid'])
        self.assertNotIn('ratesHz',bad)

    def test_latency_percentiles_are_retained_not_averaged(self):
        text=line(1,1)+', pose=LatencySummary(count=3, p50Ms=10.0, p95Ms=12.0)'
        r=collector.metric_report(text,[{'pid':'123'}],True)
        self.assertEqual(12,r['last']['latencies']['pose']['p95Ms'])
        self.assertTrue(r['counterWindowInvalid'])


if __name__ == '__main__':
    unittest.main()
