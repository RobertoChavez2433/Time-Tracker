"""Capture the candidate and local preview evidence, without touching production apps."""

import hashlib
import json
import subprocess
import sys
import uuid
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEVICE = "RFCNC0Y975L"
PACKAGE = "com.robertochavez.timetracker.uiqa"


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, timeout=60)


def identity():
    paths = command("git", "ls-files", "-co", "--exclude-standard").decode().splitlines()
    files = {
        p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest()
        for p in sorted(set(paths))
        if (ROOT / p).is_file() and (p.startswith(("app/", "core/", "feature/", "gradle/")) or p.endswith(".gradle.kts"))
    }
    return {
        "head": command("git", "rev-parse", "HEAD").decode().strip(),
        "files": files,
        "tree_sha256": hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest(),
        "apk_sha256": hashlib.sha256((ROOT / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest(),
        "package": PACKAGE,
        "device": DEVICE,
        "mode": "debug",
    }


if sys.argv[1] == "init":
    run = ROOT / "tools/verification/results" / datetime.now().strftime("%Y-%m-%d") / ("settings-" + uuid.uuid4().hex[:10])
    run.mkdir(parents=True)
    (ROOT / "build/settings-run.txt").write_text(str(run))
    before = json.loads((ROOT / "build/settings-before.json").read_text())
    candidate = identity()
    candidate["changed_from_previous_preview"] = [p for p, value in candidate["files"].items() if before["files"].get(p) != value]
    candidate["weekly_table_unchanged"] = all(
        before["files"].get(p) == value for p, value in candidate["files"].items() if p.startswith("feature/reports/")
    )
    (run / "candidate.json").write_text(json.dumps(candidate, indent=2))
    command("adb", "-s", DEVICE, "shell", "am", "force-stop", PACKAGE)
    (run / "preview-before.tar").write_bytes(command("adb", "-s", DEVICE, "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "databases", "files", "shared_prefs"))
    (run / "permissions-before.txt").write_bytes(command("adb", "-s", DEVICE, "shell", "dumpsys", "package", PACKAGE))
    (run / "packages-before.txt").write_bytes(command("adb", "-s", DEVICE, "shell", "pm", "list", "packages", "timetracker"))
    (run / "log-start.txt").write_bytes(command("adb", "-s", DEVICE, "shell", "date '+%m-%d %H:%M:%S.000'"))
    print(json.dumps({k: v for k, v in candidate.items() if k != "files"}, indent=2))
    print(run)
else:
    run = Path((ROOT / "build/settings-run.txt").read_text())
    candidate = json.loads((run / "candidate.json").read_text())
    current = identity()
    result = {"identity_unchanged": current["files"] == candidate["files"] and current["apk_sha256"] == candidate["apk_sha256"]}
    (run / "final-identity.json").write_text(json.dumps(result, indent=2))
    (run / "preview-after.tar").write_bytes(command("adb", "-s", DEVICE, "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "databases", "files", "shared_prefs"))
    print(result)
