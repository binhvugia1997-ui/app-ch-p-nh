"""Camera-off invariant for Phase 2 host tools. Never launches activities."""
import json
import re
import time


def active_clients(text):
    match = re.search(r'Active Camera Clients:\s*\[(.*?)\]', text, re.S)
    if not match:
        raise RuntimeError('Camera ownership telemetry unavailable; idle is unverified.')
    body = match[1].strip()
    if not body:
        return []
    owners = re.findall(r'Camera ID:\s*([^,]+),.*?PID:\s*(\d+),.*?Client Package Name:\s*([^,\s)]+)', body, re.S)
    if not owners:
        raise RuntimeError('Unrecognized active camera client; idle is unverified.')
    return [{'cameraId':camera, 'pid':int(pid), 'package':package} for camera,pid,package in owners]


class CameraIdle:
    def __init__(self, read_camera, read_pid, stop_project, output, sleep=time.sleep):
        self.read_camera, self.read_pid = read_camera, read_pid
        self.stop_project, self.output, self.sleep = stop_project, output, sleep

    def snapshot(self):
        text = self.read_camera()
        state = {'time':time.time(), 'clients':active_clients(text), 'projectPid':self.read_pid().strip()}
        with self.output.open('a',encoding='utf-8') as f:
            f.write(json.dumps(state)+'\n')
        return state

    def require_idle(self):
        state = self.snapshot()
        if state['clients'] or state['projectPid']:
            raise RuntimeError('Camera-off invariant violated: '+json.dumps(state))
        return state

    def stop_and_verify(self):
        self.stop_project()
        # Bounded release settling; never relaunch or kill another app's camera.
        last = None
        for attempt in range(5):
            try:
                return self.require_idle()
            except RuntimeError as error:
                last = error
                if attempt < 4:
                    self.sleep(.25)
        raise RuntimeError('Project cleanup/zero-camera idle could not be verified: '+str(last))
