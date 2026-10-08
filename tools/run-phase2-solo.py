"""Delayed, local-only Phase 2 session. Run only when the solo tester is ready.

Does not install/grant permissions, change connectivity/OS rotation, or claim human tracking PASS.
--plan prints instructions without connecting to a device. Screenshots stay in ignored evidence.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import signal
import sys
import time
import xml.etree.ElementTree as ET
from phase2_thermal import ThermalGuard
from phase2_camera_idle import CameraIdle

ROOT = Path(__file__).resolve().parents[1]
APP = 'com.aiphotographer.app'
PROJECT_PACKAGES = (APP, APP+'.test', 'com.aiphotographer.perception.mediapipe.test')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', action='store_true')
    parser.add_argument('--adb')
    parser.add_argument('--serial')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--configuration', choices=['rear-portrait','rear-landscape','front-portrait','front-landscape'])
    parser.add_argument('--debug-matrix', action='store_true', help='Disabled during thermal recovery; use one window per invocation.')
    parser.add_argument('--analysis720p', action='store_true', help='Single profile baseline at requested 720p, otherwise 480p.')
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--countdown', type=int, default=15)
    parser.add_argument('--idle-only', action='store_true', help='Stop project and verify camera-off cooldown only; never launch camera.')
    parser.add_argument('--framing-only', action='store_true', help='Save one guarded setup screenshot and stop; no countdown or test collection.')
    parser.add_argument('--trace', action='store_true')
    parser.add_argument('--aspect', choices=['4:3','16:9'], default='4:3')
    parser.add_argument('--lifecycle', action='store_true',
                        help='Disabled during thermal recovery; lifecycle checks need separately bounded sessions.')
    args = parser.parse_args()
    if not args.plan and (args.debug_matrix or args.lifecycle or not 30 <= args.seconds <= 60):
        parser.error('Thermal recovery: one 30-60 s window per invocation; matrix/lifecycle batching disabled.')
    if args.plan:
        print('Four physical setups: rear/front x portrait/landscape. Secure phone at 3-4 m in good light; '
              'keep head, hands, feet visible. Each countdown lets you walk into frame. Repeat a 60-second '
              'sequence: still 0-10; arms 10-20; side steps 20-30; head turns 30-40; closer face 40-50; '
              'still/leave view 50-60. Thermal recovery uses one 30-60 s window, then camera stops. '
              'Review screenshots and actual delivered resolution; adaptation may request 480p. '
              'Cooldown and a new normal-status preflight precede every invocation; countdown defaults to 15 s. See docs/phase-2-physical-verification.md.')
        return
    if not all([args.adb,args.serial,args.output,args.configuration]):
        parser.error('ADB, serial, new output directory and physical configuration are required.')
    output = args.output.resolve()
    if not output.is_relative_to(ROOT/'device-evidence') or output.exists():
        parser.error('Use a NEW directory under ignored device-evidence.')
    if not 1 <= args.seconds <= 600 or not 0 <= args.countdown <= 120:
        parser.error('Duration 1-600 s; countdown 0-120 s.')
    command = [args.adb,'-s',args.serial]
    switch_texts = {ET.parse(p).find("string[@name='switch_camera']").text for p in
                    (ROOT/'app/src/main/res/values/strings.xml',ROOT/'app/src/main/res/values-vi/strings.xml')}

    def adb(*parts):
        with (output/'commands.jsonl').open('a',encoding='utf-8') as audit:
            audit.write(json.dumps({'time':time.time(),'args':list(parts)})+'\n')
        return subprocess.run(command+list(parts),capture_output=True,text=True,encoding='utf-8',timeout=5 if parts in (('shell','dumpsys','thermalservice'),('shell','dumpsys','media.camera')) else 45,check=True).stdout

    def hierarchy():
        adb('shell','uiautomator','dump','/data/local/tmp/phase2-solo.xml')
        return ET.fromstring(adb('shell','cat','/data/local/tmp/phase2-solo.xml'))

    def tap(texts):
        xml = hierarchy()
        nodes = [n for n in xml.iter('node') if n.get('text') in texts and n.get('enabled') == 'true']
        if len(nodes) != 1:
            raise RuntimeError(f'Expected one visible control {texts}; check current APK/layout.')
        bounds = list(map(int,re.findall(r'\d+',nodes[0].get('bounds',''))))
        if len(bounds) != 4:
            raise RuntimeError('Missing control bounds.')
        adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        time.sleep(4)

    def collect(argv):
        # Keep the child alive long enough to write INCOMPLETE and stop its owned capture.
        process = subprocess.Popen(argv,creationflags=getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0))
        try:
            code = process.wait()
            if code:
                raise subprocess.CalledProcessError(code,argv)
        except BaseException:
            if process.poll() is None:
                process.send_signal(getattr(signal,'CTRL_BREAK_EVENT',signal.SIGINT))
                try:
                    process.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    process.terminate();process.wait(timeout=10)
            raise

    output.mkdir(parents=True)
    if adb('get-state').strip() != 'device':
        raise RuntimeError('Device not authorized.')
    if not args.idle_only and not re.search(r'android.permission.CAMERA:\s*granted=true',adb('shell','dumpsys','package',APP)):
        raise RuntimeError('Camera permission is not granted; no capture started.')
    if args.debug_matrix and args.analysis720p:
        parser.error('Debug matrix starts at default 480p; use analysis720p only for a single baseline.')
    metadata = {'requestedConfiguration':args.configuration,'humanGate':'PENDING_PHYSICAL_VERIFICATION',
                'model':adb('shell','getprop','ro.product.model').strip(),
                'api':adb('shell','getprop','ro.build.version.sdk').strip(),
                'warning':'Requested settings are not verified delivered geometry or tracking accuracy.'}
    (output/'session.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    def project_pid():
        result = subprocess.run(command+['shell','pidof',*PROJECT_PACKAGES],capture_output=True,text=True,encoding='utf-8',timeout=5)
        if result.returncode not in (0,1) or result.stderr.strip():
            raise RuntimeError('Project process telemetry unavailable: '+result.stderr)
        return result.stdout
    def stop_project():
        failures = []
        for package in PROJECT_PACKAGES:
            try:
                adb('shell','am','force-stop',package)
            except Exception as error:
                failures.append(str(error))
        if failures:
            raise RuntimeError('Project package stop failed: '+'; '.join(failures))
    idle = CameraIdle(lambda: adb('shell','dumpsys','media.camera'), project_pid,
                      stop_project, output/'camera-idle.jsonl')
    guard = ThermalGuard(lambda: adb('shell','dumpsys','thermalservice'),
                         stop_project, output/'thermal-guard.json')
    try:
        idle.stop_and_verify()
        guard.start()
        print('Camera remains stopped: 30-second normal-status cooldown preflight.', flush=True)
        for _ in range(15):
            time.sleep(2)
            idle.require_idle()
            guard.check()
            if any(sample['status'] != 0 for sample in guard.samples):
                raise RuntimeError('Cooldown requires normal thermal status throughout.')
        guard.check()
        if args.idle_only:
            idle.require_idle()
            metadata['collectionStatus'] = 'IDLE_VERIFIED_NO_CAMERA_LAUNCH'
            return
        if not args.framing_only:
            print('Walk into frame during countdown; camera remains stopped until it ends.', flush=True)
            for left in range(args.countdown,0,-1):
                if left % 5 == 0 or left <= 3:
                    print(f'Camera starts in {left} s',flush=True)
                time.sleep(1)
                idle.require_idle()
                guard.check()
        guard.check()
        idle.require_idle()
        # Fresh task resets diagnostic controls to known defaults; no app data or evidence is deleted.
        adb('shell','am','start','-S','-f','0x10008000','-n',APP+'/.MainActivity',
            '--ez','analysis720p',str(args.analysis720p).lower(),
            '--ez','framingOnly',str(args.framing_only).lower(),
            '--ez','framingFront',str(args.configuration.startswith('front')).lower(),
            '--ez','framingWide',str(args.aspect == '16:9').lower())
        time.sleep(6)
        ui = hierarchy()
        if args.framing_only:
            labels = {ET.parse(p).find("string[@name='framing_preview']").text for p in
                      (ROOT/'app/src/main/res/values/strings.xml', ROOT/'app/src/main/res/values-vi/strings.xml')}
            if not any(n.get('text') in labels for n in ui.iter('node')):
                raise RuntimeError('Preview-only mode not confirmed; refusing framing screenshot.')
        bounds = list(map(int,re.findall(r'\d+',next(ui.iter('node')).get('bounds',''))))
        if len(bounds) != 4:
            raise RuntimeError('Cannot verify displayed orientation.')
        displayed = 'landscape' if bounds[2]-bounds[0] > bounds[3]-bounds[1] else 'portrait'
        metadata['displayedOrientation'] = displayed
        if not args.configuration.endswith(displayed):
            raise RuntimeError('Displayed orientation does not match physical setup; no capture started.')
        if args.configuration.startswith('front') and not args.framing_only:
            tap(switch_texts)
        configurations = [(720 if args.analysis720p else 480, args.aspect)]
        if args.aspect == '16:9' and not args.framing_only:
            tap({'4:3 / 16:9'})
        if args.analysis720p and not args.framing_only:
            selected = {n.get('text') for n in hierarchy().iter('node')} & {'R480P','R720P'}
            if selected == {'R480P'}:
                tap(selected)
        guard.check()
        if args.framing_only:
            data = subprocess.run(command+['exec-out','screencap','-p'],capture_output=True,timeout=20,check=True).stdout
            if not data.startswith(b'\x89PNG\r\n\x1a\n'):
                raise RuntimeError('Invalid framing screenshot.')
            (output/'framing.png').write_bytes(data)
            guard.check()
            metadata['collectionStatus'] = 'FRAMING_ONLY_NO_TEST_COLLECTION'
            return
        previous = configurations[0]
        for index,(resolution,aspect) in enumerate(configurations):
            if index and aspect != previous[1]:
                tap({'4:3 / 16:9'})
            if args.debug_matrix:
                selected = {n.get('text') for n in hierarchy().iter('node')} & {'R480P','R720P'}
                if len(selected) != 1:
                    raise RuntimeError('Cannot establish current requested analysis resolution.')
                if selected != {f'R{resolution}P'}:
                    tap(selected)
            print(f'Run {index+1}: requested {resolution}p {aspect}. Collection starts now.',flush=True)
            run = output/f'{index+1:02}-{resolution}p-{aspect.replace(":","x")}'
            capture = [sys.executable,str(ROOT/'tools/collect-phase2-device.py'),'--adb',args.adb,
                       '--serial',args.serial,'--output',str(run),'--seconds',str(args.seconds),'--preview-samples']
            if args.trace:
                config = output/f'{index+1:02}-trace.pbtxt'
                template = (ROOT/'tools/phase2-perfetto.pbtxt').read_text()
                config.write_text(re.sub(r'duration_ms: \d+',f'duration_ms: {args.seconds*1000}',template))
                capture += ['--trace-config',str(config)]
            guard.check()
            collect(capture)
            guard.check()
            previous = (resolution,aspect)
        metadata['collectionStatus'] = 'COMPLETE_REQUIRES_EVIDENCE_REVIEW'
    except BaseException:
        metadata['collectionStatus'] = 'INCOMPLETE'
        raise
    finally:
        cleanup_errors = []
        try:
            idle.stop_and_verify()
        except Exception as error:
            cleanup_errors.append('camera cleanup: '+str(error))
        try:
            guard.close(final_sample=True)
        except Exception as error:
            cleanup_errors.append('thermal guard: '+str(error))
        # Guard shutdown or another launcher may race force-stop: verify again afterward.
        try:
            idle.require_idle()
        except Exception as error:
            cleanup_errors.append('final idle verification: '+str(error))
        if guard.failure:
            metadata['thermalStop'] = guard.failure
        if cleanup_errors:
            metadata['collectionStatus'] = 'INCOMPLETE'
            metadata['cleanupErrors'] = cleanup_errors
        (output/'session.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
        if cleanup_errors:
            raise RuntimeError('; '.join(cleanup_errors))



if __name__ == '__main__':
    main()
