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

ROOT = Path(__file__).resolve().parents[1]
APP = 'com.aiphotographer.app'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', action='store_true')
    parser.add_argument('--adb')
    parser.add_argument('--serial')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--configuration', choices=['rear-portrait','rear-landscape','front-portrait','front-landscape'])
    parser.add_argument('--debug-matrix', action='store_true', help='Both aspects at both requested resolutions; debug APK required.')
    parser.add_argument('--analysis720p', action='store_true', help='Single profile baseline at requested 720p, otherwise 480p.')
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--countdown', type=int, default=20)
    parser.add_argument('--trace', action='store_true')
    parser.add_argument('--lifecycle', action='store_true',
                        help='After collections, automate home/resume and camera switch/return with local snapshots.')
    args = parser.parse_args()
    if args.plan:
        print('Four physical setups: rear/front x portrait/landscape. Secure phone at 3-4 m in good light; '
              'keep head, hands, feet visible. Each countdown lets you walk into frame. Repeat a 60-second '
              'sequence: still 0-10; arms 10-20; side steps 20-30; head turns 30-40; closer face 40-50; '
              'still/leave view 50-60. Debug matrix batches two aspects and two requested resolutions. '
              'Review screenshots and actual delivered resolution; adaptation may request 480p. '
              'Then two optimized-profile baselines and one 300-second soak. See docs/phase-2-physical-verification.md.')
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
        return subprocess.run(command+list(parts),capture_output=True,text=True,timeout=45,check=True).stdout

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

    if adb('get-state').strip() != 'device':
        raise RuntimeError('Device not authorized.')
    if not re.search(r'android.permission.CAMERA:\s*granted=true',adb('shell','dumpsys','package',APP)):
        raise RuntimeError('Camera permission is not granted; no capture started.')
    if args.debug_matrix and args.analysis720p:
        parser.error('Debug matrix starts at default 480p; use analysis720p only for a single baseline.')
    output.mkdir(parents=True)
    metadata = {'requestedConfiguration':args.configuration,'humanGate':'PENDING_PHYSICAL_VERIFICATION',
                'model':adb('shell','getprop','ro.product.model').strip(),
                'api':adb('shell','getprop','ro.build.version.sdk').strip(),
                'warning':'Requested settings are not verified delivered geometry or tracking accuracy.'}
    (output/'session.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    try:
        # Fresh task resets diagnostic controls to known defaults; no app data or evidence is deleted.
        adb('shell','am','start','-S','-f','0x10008000','-n',APP+'/.MainActivity',
            '--ez','analysis720p',str(args.analysis720p).lower())
        time.sleep(6)
        bounds = list(map(int,re.findall(r'\d+',next(hierarchy().iter('node')).get('bounds',''))))
        if len(bounds) != 4:
            raise RuntimeError('Cannot verify displayed orientation.')
        displayed = 'landscape' if bounds[2]-bounds[0] > bounds[3]-bounds[1] else 'portrait'
        metadata['displayedOrientation'] = displayed
        if not args.configuration.endswith(displayed):
            raise RuntimeError('Displayed orientation does not match physical setup; no capture started.')
        if args.configuration.startswith('front'):
            tap(switch_texts)
        configurations = [(480,'4:3'),(480,'16:9'),(720,'16:9'),(720,'4:3')] if args.debug_matrix else [
            (720 if args.analysis720p else 480,'4:3')]
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
            print(f'Run {index+1}: requested {resolution}p {aspect}. Walk into frame after countdown.',flush=True)
            for left in range(args.countdown,0,-1):
                if left % 5 == 0 or left <= 3:
                    print(f'Start in {left} s',flush=True)
                time.sleep(1)
            run = output/f'{index+1:02}-{resolution}p-{aspect.replace(":","x")}'
            capture = [sys.executable,str(ROOT/'tools/collect-phase2-device.py'),'--adb',args.adb,
                       '--serial',args.serial,'--output',str(run),'--seconds',str(args.seconds),'--preview-samples']
            if args.trace:
                config = output/f'{index+1:02}-trace.pbtxt'
                template = (ROOT/'tools/phase1-perfetto.pbtxt').read_text()
                config.write_text(re.sub(r'duration_ms: \d+',f'duration_ms: {args.seconds*1000}',template))
                capture += ['--trace-config',str(config)]
            collect(capture)
            previous = (resolution,aspect)
        if args.lifecycle:
            def screenshot(name):
                data = subprocess.run(command+['exec-out','screencap','-p'],capture_output=True,
                                      timeout=20,check=True).stdout
                if not data.startswith(b'\x89PNG\r\n\x1a\n'):
                    raise RuntimeError('Lifecycle snapshot is not PNG.')
                (output/(name+'.png')).write_bytes(data)
            screenshot('lifecycle-before')
            adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(3)
            adb('shell','am','start','-n',APP+'/.MainActivity');time.sleep(6)
            screenshot('lifecycle-resumed')
            tap(switch_texts);screenshot('camera-switched')
            tap(switch_texts);screenshot('camera-returned')
            collect([sys.executable,str(ROOT/'tools/collect-phase2-device.py'),'--adb',args.adb,
                '--serial',args.serial,'--output',str(output/'lifecycle-return'),
                '--seconds','15','--preview-samples'])
            (output/'exit-info.txt').write_text(adb('shell','dumpsys','activity','exit-info',APP),encoding='utf-8')
        metadata['collectionStatus'] = 'COMPLETE_REQUIRES_EVIDENCE_REVIEW'
    except BaseException:
        metadata['collectionStatus'] = 'INCOMPLETE'
        raise
    finally:
        (output/'session.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
        adb('shell','am','force-stop',APP)


if __name__ == '__main__':
    main()
