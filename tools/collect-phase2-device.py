"""Collect local Phase 2 diagnostics; outputs belong in ignored device-evidence.

Does not launch/reconfigure the app or grant permissions. Optional preview screenshots remain local
under ignored evidence; no uploads. Without that option no images are captured.
Counter differences describe the selected observation window, not CameraX internal drops.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import signal
import time
import uuid
from phase2_thermal import ThermalGuard
from phase2_process import stop_logcat, stop_trace
from phase2_camera_idle import CameraIdle


COUNTERS = ('offered', 'accepted', 'busySkipped', 'cadenceSkipped', 'poseCompleted',
            'poseDetected', 'faceCompleted', 'faceDetected', 'errors')
FACE_OPTIONAL = ('faceValid478', 'faceContinuityPairs', 'faceDisjointPairs',
                 'faceReacquisitions')


def metric_report(text, samples, complete):
    records = []
    for line in text.splitlines():
        if 'PerceptionMetrics(' not in line:
            continue
        fields = {k: (float(v) if any(c in v for c in '.Ee') else int(v))
                  for k, v in re.findall(r'(\w+)=([0-9]+(?:\.[0-9]+)?(?:E[-+]?\d+)?)', line)}
        fields['latencies'] = {name: {'count': int(count),
            'p50Ms': None if p50 == 'null' else float(p50),
            'p95Ms': None if p95 == 'null' else float(p95)}
            for name, count, p50, p95 in re.findall(
                r'(pose|face|batch)=LatencySummary\(count=(\d+), p50Ms=([^,]+), p95Ms=([^)]+)\)', line)}
        fields['pid'] = re.match(r'^\s*\[?\s*[\d.]+\]?\s+(\d+)\s+', line).group(1) if re.match(
            r'^\s*\[?\s*[\d.]+\]?\s+(\d+)\s+', line) else None
        records.append(fields)
    report = {'measurementStatus': 'COMPLETE' if complete else 'INCOMPLETE',
              'metricRecords': len(records), 'first': records[0] if records else None,
              'last': records[-1] if records else None,
              'warning': 'Rolling task-to-callback percentiles; do not average percentiles. '
                         'App counters do not measure CameraX internal drops. COMPLETE means capture '
                         'finished, not that human framing, tracking, or a verification gate passed. '
                         'Disjoint Face bounds indicate possible jumps, not identity proof.'}
    valid = complete and len(records) >= 2
    if valid:
        first, last = records[0], records[-1]
        seconds = last.get('elapsedSeconds', 0) - first.get('elapsedSeconds', 0)
        pids = {s.get('pid') for s in samples}
        valid = (len(pids) == 1 and None not in pids and '' not in pids and
                 all(r.get('sessionId') is not None and r.get('sessionId') == first.get('sessionId')
                     and r.get('pid') == first.get('pid') and r.get('pid') in pids for r in records) and
                 seconds > 0 and all(k in r for r in records for k in COUNTERS) and
                 all(b.get('elapsedSeconds', 0) >= a.get('elapsedSeconds', 0) and
                     all(b[k] >= a[k] for k in COUNTERS) for a, b in zip(records, records[1:])))
        if valid:
            deltas = {k: last[k] - first[k] for k in COUNTERS}
            for key in FACE_OPTIONAL:
                present = [key in r for r in records]
                if any(present) and not all(present):
                    report['counterWindowInvalid'] = True
                    return report
                if all(present):
                    if any(b[key] < a[key] for a,b in zip(records,records[1:])):
                        report['counterWindowInvalid'] = True
                        return report
                    deltas[key] = last[key] - first[key]
            continuity = ('faceContinuityPairs', 'faceDisjointPairs', 'faceReacquisitions')
            if any(key in deltas for key in continuity) and not all(key in deltas for key in continuity):
                report['counterWindowInvalid'] = True
                return report
            if ('faceContinuityPairs' in deltas and
                    (deltas['faceDisjointPairs'] > deltas['faceContinuityPairs'] or
                     deltas['faceContinuityPairs'] > deltas['faceDetected'] or
                     deltas['faceReacquisitions'] > deltas['faceDetected'])):
                report['counterWindowInvalid'] = True
                return report
            report.update(counterWindowSeconds=seconds, counterDeltas=deltas,
                          ratesHz={k: deltas[k] / seconds for k in ('poseCompleted', 'faceCompleted')})
    if not valid:
        report['counterWindowInvalid'] = True
    return report


def main():
    if hasattr(signal, 'SIGBREAK'):
        def interrupted(*_):
            raise KeyboardInterrupt()
        signal.signal(signal.SIGBREAK, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--trace-config', type=Path)
    parser.add_argument('--cancel-file', type=Path, help='Owned runner cancellation token; never deleted.')
    parser.add_argument('--preview-samples', action='store_true',
                        help='Save local screenshots in ignored evidence for qualitative review.')
    args = parser.parse_args()
    evidence = Path(__file__).resolve().parents[1] / 'device-evidence'
    output = args.output.resolve()
    if not output.is_relative_to(evidence) or not 1 <= args.seconds <= 600:
        parser.error('Use ignored device-evidence and a duration of 1â€“600 seconds.')
    if output.exists():
        parser.error('Choose a new output directory; existing evidence is never overwritten.')
    output.mkdir(parents=True)
    (output / 'summary.json').write_text(json.dumps({'measurementStatus': 'INCOMPLETE',
        'reason': 'Capture has not completed.'}), encoding='utf-8')
    command = [args.adb, '-s', args.serial]

    def adb(*parts):
        result = subprocess.run(command + list(parts), capture_output=True, timeout=5 if parts == ('shell','dumpsys','thermalservice') else 10)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout.decode(errors='replace')

    def stop_project():
        failures = []
        for package in ('com.aiphotographer.app', 'com.aiphotographer.app.test',
                        'com.aiphotographer.perception.mediapipe.test'):
            try:
                adb('shell', 'am', 'force-stop', package)
            except Exception as error:
                failures.append(str(error))
        if failures:
            raise RuntimeError('Project stop failed: ' + '; '.join(failures))

    def project_pid():
        result = subprocess.run(command + ['shell', 'pidof', 'com.aiphotographer.app',
            'com.aiphotographer.app.test', 'com.aiphotographer.perception.mediapipe.test'],
            capture_output=True, timeout=5)
        if result.returncode not in (0, 1) or result.stderr.strip():
            raise RuntimeError('Project PID telemetry unavailable.')
        return result.stdout.decode().strip()

    idle = CameraIdle(lambda: adb('shell', 'dumpsys', 'media.camera'), project_pid,
                      stop_project, output/'cleanup-camera-idle.jsonl')
    guard = ThermalGuard(lambda: adb('shell','dumpsys','thermalservice'),
                         stop_project, output/'thermal-guard.json')
    def check_running():
        guard.check()
        if args.cancel_file and args.cancel_file.exists():
            raise RuntimeError('Runner cancellation requested; collection incomplete.')
        if log.poll() is not None:
            raise RuntimeError('Logcat collector exited before collection completed.')

    def wait_running(seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            check_running()
            time.sleep(min(.25, max(0, deadline - time.monotonic())))
        check_running()

    trace_pid = None
    trace_id = uuid.uuid4().hex
    trace_output = f'/data/misc/perfetto-traces/phase2-{trace_id}.pftrace'
    trace_config = f'/data/misc/perfetto-configs/phase2-{trace_id}.pbtxt'
    complete = False
    failure = None
    samples = []
    cleanup_errors = []
    with (output / 'app.log').open('wb') as log_file:
        log = subprocess.Popen(command + ['logcat', '-T', adb('shell', "date '+%m-%d %H:%M:%S.000'").strip(), '-v', 'monotonic', '-s',
            'Phase2Baseline:I', 'Phase1Baseline:I', 'Phase2Perception:I', 'AndroidRuntime:E'],
            stdout=log_file, stderr=subprocess.STDOUT)
        started = time.monotonic()
        try:
            guard.start()
            check_running()
            if args.trace_config:
                durations = re.findall(r'^\s*duration_ms:\s*(\d+)\s*$', args.trace_config.read_text(), re.M)
                if durations != [str(args.seconds * 1000)]:
                    raise RuntimeError('Trace requires one bounded duration matching collection seconds.')
                adb('push', str(args.trace_config), trace_config)
                trace_pid = adb('shell', 'perfetto', '--background-wait', '--txt', '-c', trace_config,
                                '-o', trace_output).strip()
                if not trace_pid.isdecimal():
                    trace_pid = None
                    raise RuntimeError('Perfetto did not return a capture PID; no valid trace claimed.')
                (output / 'trace-session.json').write_text(json.dumps({
                    'pid': trace_pid, 'captureId': trace_id, 'deviceOutput': trace_output,
                    'status': 'RUNNING'}), encoding='utf-8')
            while True:
                check_running()
                elapsed = time.monotonic() - started
                sample = {'elapsedSeconds': elapsed}
                if args.preview_samples:
                    screenshot = subprocess.run(command + ['exec-out', 'screencap', '-p'],
                                                capture_output=True, timeout=20, check=True).stdout
                    if not screenshot.startswith(b'\x89PNG\r\n\x1a\n'):
                        raise RuntimeError('Screenshot was not PNG; capture incomplete.')
                    (output / f'{len(samples):03}-preview.png').write_bytes(screenshot)
                for name, parts in {
                    'thermal': ('shell', 'dumpsys', 'thermalservice'),
                    'memory': ('shell', 'dumpsys', 'meminfo', 'com.aiphotographer.app'),
                    'pid': ('shell', 'pidof', 'com.aiphotographer.app'),
                }.items():
                    check_running()
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
                check_running()
                if remaining <= 0:
                    complete = True
                    break
                wait_running(min(10, remaining))
        except BaseException as error:
            failure = error
        finally:
            try:
                guard.close(final_sample=True)
            except Exception as error:
                complete = False
                failure = failure or error
            try:
                stop_logcat(log)
            except Exception as error:
                complete = False
                cleanup_errors.append('logcat cleanup: ' + str(error))
                failure = failure or error
            if trace_pid:
                try:
                    stop_trace(adb, trace_pid, trace_id)
                    adb('pull', trace_output, str(output / 'observation.pftrace'))
                    (output / 'trace-session.json').write_text(json.dumps({
                        'pid': trace_pid, 'captureId': trace_id, 'deviceOutput': trace_output,
                        'status': 'STOPPED_CAPTURE_RETRIEVED'}), encoding='utf-8')
                except Exception as error:
                    complete = False
                    cleanup_errors.append('trace cleanup: ' + str(error))
                    failure = failure or error
                    (output / 'trace-session.json').write_text(json.dumps({
                        'pid': trace_pid, 'captureId': trace_id, 'deviceOutput': trace_output,
                        'status': 'INCOMPLETE_CLEANUP_OR_RETRIEVAL', 'error': str(error)}), encoding='utf-8')
            if failure:
                try:
                    idle.stop_and_verify()
                except Exception as error:
                    cleanup_errors.append('project camera cleanup: ' + str(error))
    text = (output / 'app.log').read_text(encoding='utf-8', errors='replace')
    report = metric_report(text, samples, complete)
    if cleanup_errors:
        report['cleanupErrors'] = cleanup_errors
    report['wallSeconds'] = time.monotonic() - started
    if failure:
        report['interruption'] = type(failure).__name__ + ': ' + str(failure)
    (output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('first', 'last')}), flush=True)
    if failure:
        raise failure


if __name__ == '__main__':
    main()
