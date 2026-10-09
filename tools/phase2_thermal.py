"""Host-only Phase 2 safety guard; no camera launch or thermal override."""
import json
import re
import threading
import time


def thermal_sample(text):
    match = re.search(r'Thermal Status: (\d+)', text)
    skin = re.search(r'Temperature\{mValue=([\d.]+), mType=3, mName=SKIN, mStatus=\d+}', text)
    if not match or not skin:
        raise RuntimeError('Thermal status/skin temperature unavailable; refuse physical testing.')
    return {'status': int(match[1]), 'skinC': float(skin[1])}


class ThermalGuard:
    # Rapid-rise engineering seed, CALIBRATION_REQUIRED; not a medical safety limit.
    def __init__(self, read, stop, output, clock=time.monotonic):
        self.read, self.stop, self.output, self.clock = read, stop, output, clock
        self.samples = []
        self.failure = None
        self.done = threading.Event()
        self.thread = None

    def poll(self, preflight=False):
        try:
            sample = thermal_sample(self.read())
            sample['time'] = self.clock()
            self.samples.append(sample)
            self.output.write_text(json.dumps(self.samples, indent=2), encoding='utf-8')
            if preflight and sample['status'] != 0:
                raise RuntimeError('Preflight requires normal thermal status 0.')
            if sample['status'] >= 3:
                raise RuntimeError('Severe/critical thermal status; stopping camera.')
            prior = next((s for s in self.samples if 0 < sample['time'] - s['time'] <= 30), None)
            if prior and sample['skinC'] - prior['skinC'] >= 2:
                raise RuntimeError('Rapid skin temperature rise (2 C within 30 s); stopping camera.')
        except Exception as error:
            self.failure = str(error)
            self.stop()
            raise
        return sample

    def check(self):
        if self.failure:
            raise RuntimeError(self.failure)

    def start(self):
        self.poll(preflight=True)
        def monitor():
            while not self.done.wait(2):
                try:
                    self.poll()
                except Exception:
                    return
        self.thread = threading.Thread(target=monitor, daemon=True)
        self.thread.start()

    def close(self, final_sample=False):
        self.done.set()
        if self.thread:
            self.thread.join(timeout=15)
        if final_sample and not self.failure:
            self.poll()
        self.check()
