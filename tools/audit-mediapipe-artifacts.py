"""Inspect official Maven metadata/POMs and logger bytecode; write evidence outside Git.

Usage: python tools/audit-mediapipe-artifacts.py --output device-evidence/phase2/artifact-audit
Requires a JDK javap executable on PATH (or --javap).
This does not install, alter or execute MediaPipe SDK classes.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

BASE = "https://dl.google.com/dl/android/maven2/com/google/mediapipe"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def fetch(url, target):
    if not target.exists():
        with urllib.request.urlopen(url, timeout=90) as response:
            target.write_bytes(response.read())
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--javap", default="javap")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = fetch(f"{BASE}/tasks-core/maven-metadata.xml", args.output / "metadata.xml")
    versions = [node.text for node in ET.parse(metadata).findall("./versioning/versions/version")]

    def audit(version):
        stem = f"tasks-core-{version}"
        directory = args.output / version
        directory.mkdir(exist_ok=True)
        url = f"{BASE}/tasks-core/{version}/{stem}"
        pom = fetch(f"{url}.pom", directory / f"{stem}.pom")
        aar = fetch(f"{url}.aar", directory / f"{stem}.aar")
        deps = []
        for dep in ET.parse(pom).findall("m:dependencies/m:dependency", NS):
            deps.append({tag: dep.findtext(f"m:{tag}", namespaces=NS)
                         for tag in ("groupId", "artifactId", "version", "scope", "optional")})
        with zipfile.ZipFile(aar) as archive:
            jar = directory / "classes.jar"
            jar.write_bytes(archive.read("classes.jar"))
        with zipfile.ZipFile(jar) as archive:
            factories = [name[:-6].replace("/", ".") for name in archive.namelist()
                         if name.endswith("TasksStatsLoggerFactory.class")]
        # Older releases call the proto logger directly rather than through a factory.
        results = []
        for factory in factories + ["com.google.mediapipe.tasks.core.TaskRunner"]:
            result = subprocess.run([args.javap, "-classpath", str(jar), "-c", "-p", factory],
                                    capture_output=True, text=True, check=True)
            results.append(result.stdout)
        bytecode = "\n".join(results)
        (directory / "initialization.javap.txt").write_text(bytecode, encoding="utf-8")
        vision_apis = None
        if not any(dep["groupId"] == "com.google.android.datatransport" for dep in deps):
            vision = fetch(f"{BASE}/tasks-vision/{version}/tasks-vision-{version}.aar", directory / "vision.aar")
            with zipfile.ZipFile(vision) as archive:
                import io
                with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
                    names = set(classes.namelist())
            prefix = "com/google/mediapipe/tasks/vision/"
            vision_apis = {"pose": prefix + "poselandmarker/PoseLandmarker.class" in names,
                           "face": prefix + "facelandmarker/FaceLandmarker.class" in names}
        return {"version": version, "aar_sha256": hashlib.sha256(aar.read_bytes()).hexdigest(),
                "dependencies": deps, "factory_present": bool(factories),
                "proto_logger_factory": "TasksStatsProtoLogger.create" in bytecode,
                "dummy_logger_factory": "TasksStatsDummyLogger" in bytecode,
                "transport_free_vision_apis": vision_apis}

    with ThreadPoolExecutor(max_workers=4) as executor:
        results = list(executor.map(audit, versions))
    (args.output / "summary.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    for row in results:
        transport = any(dep["groupId"] == "com.google.android.datatransport" for dep in row["dependencies"])
        print(f"{row['version']}: transport={transport} proto={row['proto_logger_factory']} "
              f"dummy={row['dummy_logger_factory']} factory={row['factory_present']} "
              f"transport_free_vision_apis={row['transport_free_vision_apis']}")


if __name__ == "__main__":
    main()
