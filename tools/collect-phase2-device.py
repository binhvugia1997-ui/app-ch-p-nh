"""Collect local Phase 2 diagnostics; outputs belong in ignored device-evidence.

Does not launch/reconfigure the app, grant permissions, or capture/upload images.
Counter differences describe the selected observation window, not CameraX internal drops.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--trace-config', type=Path)
    args = parser.parse_args()
    evidence = Path(__file__).resolve().parents[1] / 'device-evidence'
    output = args.output.resolve()
    if not output.is_relative_to(evidence) or not 1 <= args.seconds <= 600:
        parser.error('Use ignored device-evidence and a duration of 1–600 seconds.')
    output.mkdir(parents=True, exist_ok=True)
    command = [args.adb, '-s', args.serial]

    def adb(*parts):
        result = subprocess.run(command + list(parts), capture_output=True, timeout=45)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout.decode(errors='replace')

    trace = None
    trace_output = '/data/local/tmp/phase2-observation.pftrace'
    trace_file = None
    if args.trace_config:
        adb('push', str(args.trace_config), '/data/local/tmp/phase2-observation.pbtxt')
        trace_file = (output / 'perfetto.log').open('wb')
        trace = subprocess.Popen(command + ['shell', 'perfetto', '--txt', '-c',
            '/data/local/tmp/phase2-observation.pbtxt', '-o', trace_output], stdout=trace_file, stderr=trace_file)
    samples = []
    with (output / 'app.log').open('wb') as log_file:
        log = subprocess.Popen(command + ['logcat', '-T', '1', '-v', 'monotonic', '-s',
            'Phase2Baseline:I', 'Phase1Baseline:I', 'Phase2Perception:I', 'AndroidRuntime:E'],
            stdout=log_file, stderr=subprocess.STDOUT)
        started = time.monotonic()
        try:
            while True:
                elapsed = time.monotonic() - started
                sample = {'elapsedSeconds': elapsed}
                for name, parts in {
                    'thermal': ('shell', 'dumpsys', 'thermalservice'),
                    'memory': ('shell', 'dumpsys', 'meminfo', 'com.aiphotographer.app'),
                    'pid': ('shell', 'pidof', 'com.aiphotographer.app'),
                }.items():
                    try:
                        text = adb(*parts)
                        (output / f'{len(samples):03}-{name}.txt').write_text(text, encoding='utf-8')
                        if name == 'thermal':
                            match = re.search(r'Thermal Status: (\d+)', text)
                            sample['thermalStatus'] = int(match[1]) if match else None
                        elif name == 'memory':
                            for field in ('TOTAL PSS', 'TOTAL RSS'):
                                match = re.search(field + r':\s*(\d+)', text)
                                sample[field + ' KiB'] = int(match[1]) if match else None
                        else:
                            sample['pid'] = text.strip()
                    except (RuntimeError, subprocess.TimeoutExpired) as error:
                        sample[name + 'Error'] = str(error)
                samples.append(sample)
                (output / 'samples.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
                print(json.dumps(sample), flush=True)
                remaining = args.seconds - (time.monotonic() - started)
                if remaining <= 0:
                    break
                time.sleep(min(10, remaining))
        finally:
            log.terminate()
            log.wait(timeout=10)
    if trace:
        trace.wait(timeout=75)
        trace_file.close()
        if trace.returncode:
            raise RuntimeError('Perfetto failed; inspect perfetto.log, do not report a trace pass.')
        adb('pull', trace_output, str(output / 'observation.pftrace'))
    text = (output / 'app.log').read_text(encoding='utf-8', errors='replace')
    records = []
    for line in text.splitlines():
        if 'PerceptionMetrics(' not in line:
            continue
        fields = {k: float(v) for k, v in re.findall(r'(\w+)=([0-9]+(?:\.[0-9]+)?(?:E[-+]?\d+)?)', line)}
        fields['latencies'] = {name: {'count': int(count),
            'p50Ms': None if p50 == 'null' else float(p50),
            'p95Ms': None if p95 == 'null' else float(p95)}
            for name, count, p50, p95 in re.findall(
                r'(pose|face|batch)=LatencySummary\(count=(\d+), p50Ms=([^,]+), p95Ms=([^)]+)\)', line)}
        fields['raw'] = line
        records.append(fields)
    report = {'wallSeconds': time.monotonic() - started, 'metricRecords': len(records),
        'first': records[0] if records else None, 'last': records[-1] if records else None,
        'warning': 'Latency summaries are rolling task-to-callback windows. Do not average percentiles. '
        'Counter deltas require unchanged process/session and do not measure CameraX internal drops.'}
    if len(records) >= 2:
        first, last = records[0], records[-1]
        seconds = last.get('elapsedSeconds', 0) - first.get('elapsedSeconds', 0)
        counters = ('offered', 'accepted', 'busySkipped', 'cadenceSkipped', 'poseCompleted',
                    'poseDetected', 'faceCompleted', 'faceDetected', 'errors')
        deltas = {key: last.get(key, 0) - first.get(key, 0) for key in counters}
        monotonic = all(b.get('elapsedSeconds', 0) >= a.get('elapsedSeconds', 0)
                        for a, b in zip(records, records[1:]))
        if monotonic and seconds > 0 and all(value >= 0 for value in deltas.values()):
            report['counterWindowSeconds'] = seconds
            report['counterDeltas'] = deltas
            report['ratesHz'] = {key: deltas[key] / seconds for key in ('poseCompleted', 'faceCompleted')}
        else:
            report['counterWindowInvalid'] = True
    (output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('first', 'last')}), flush=True)


if __name__ == '__main__':
    main()
