"""Reject known MediaPipe/DataTransport telemetry in candidate official-source AARs.

This bounded static check is not a universal network/privacy proof. Follow it with
dependency/manifest inspection and real Android Pose/Face runtime tests.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

FORBIDDEN = (
    b"com/google/android/datatransport", b"com/google/firebase",
    b"RemoteLoggingClient", b"TasksStatsProtoLogger", b"clearcut",
    b"firebaselogging.googleapis.com", b"firebaselogging-pa.googleapis.com",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core", required=True, type=Path)
    parser.add_argument("--vision", required=True, type=Path)
    parser.add_argument("--javap", default="javap")
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    report = {"artifacts": [], "findings": [], "native_abis": [], "scope":
              "Known telemetry identifiers in AAR Java/native payloads; factory bytecode. "
              "Not proof of all native call paths or runtime network behavior."}
    core_jar = None
    abis = set()
    for path in (args.core, args.vision):
        report["artifacts"].append({"name": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        with zipfile.ZipFile(path) as aar:
            for entry in aar.infolist():
                if entry.is_dir():
                    continue
                content = aar.read(entry)
                payloads = [(entry.filename, content)]
                if entry.filename.endswith(".jar"):
                    with zipfile.ZipFile(io.BytesIO(content)) as jar:
                        payloads = [(entry.filename + "/" + name, jar.read(name))
                                    for name in jar.namelist() if name.endswith(".class")]
                if entry.filename.startswith("jni/") and entry.filename.endswith(".so"):
                    abis.add(entry.filename.split("/")[1])
                for name, data in payloads:
                    for token in FORBIDDEN:
                        if token.lower() in data.lower():
                            report["findings"].append({"artifact": path.name, "entry": name,
                                                       "identifier": token.decode()})
                if path == args.core and entry.filename == "classes.jar":
                    core_jar = content
    if core_jar is None:
        raise SystemExit("Core AAR lacks classes.jar")
    with tempfile.TemporaryDirectory() as directory:
        jar = Path(directory) / "core.jar"
        jar.write_bytes(core_jar)
        result = subprocess.run([args.javap, "-classpath", str(jar), "-c", "-p",
                                 "com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory"],
                                text=True, capture_output=True, check=True)
    report["factory_bytecode"] = result.stdout
    report["dummy_factory"] = "TasksStatsDummyLogger.create" in result.stdout
    report["native_abis"] = sorted(abis)
    report["pass"] = not report["findings"] and report["dummy_factory"] and {"arm64-v8a", "x86_64"} <= abis
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("pass", "dummy_factory", "native_abis", "findings")}))
    if not report["pass"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
