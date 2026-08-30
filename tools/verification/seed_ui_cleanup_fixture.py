"""Seed only the disposable .uiqa install, never debug/release or an existing dataset."""

import argparse
import json
import sqlite3
import subprocess
from datetime import datetime
from pathlib import Path

PACKAGE = "com.robertochavez.timetracker.uiqa"


def adb(device, *args, **kwargs):
    return subprocess.run(["adb", "-s", device, *args], check=True, capture_output=True, timeout=30, **kwargs)


def millis(value):
    return int(datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp() * 1000)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("device")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb(args.device, "shell", "am", "force-stop", PACKAGE)
    for name in ["time_tracker.db", "time_tracker.db-wal", "time_tracker.db-shm"]:
        result = subprocess.run(["adb", "-s", args.device, "exec-out", "run-as", PACKAGE, "cat", "databases/" + name], capture_output=True, timeout=30)
        if result.returncode == 0:
            (args.output / name).write_bytes(result.stdout)
    db = args.output / "time_tracker.db"
    connection = sqlite3.connect(db)
    for table in ["away_sessions", "work_locations", "work_site_sessions"]:
        assert connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] == 0, "Refusing to replace existing data"
    now = millis("2026-08-30T14:00:00Z")
    sites = [
        ("qa-a", "Twin Site", "QA-A"), ("qa-b", "Twin Site", "QA-B"),
        ("qa-legacy", "Legacy Site", None), ("qa-prior", "Previous Week Site", "QA-P"),
    ]
    for index, (id_, name, job) in enumerate(sites):
        connection.execute("INSERT INTO work_locations VALUES (?,?,?,?,?,?,?,?)", (id_, name, "Fixture location", job, 0.0, index * 2.0, 1609.344, now + index))
    sessions = [
        ("qa-monday", "2026-08-24T12:00:00Z", "2026-08-24T20:00:00Z", 40),
        ("qa-midnight", "2026-08-26T03:30:00Z", "2026-08-26T05:30:00Z", 4),
        ("qa-friday", "2026-08-28T12:00:00Z", "2026-08-28T13:00:00Z", 0),
        ("qa-prior", "2026-08-21T12:00:00Z", "2026-08-21T13:00:00Z", 0),
    ]
    for id_, start, end, miles in sessions:
        connection.execute("INSERT INTO away_sessions VALUES (?,?,?,?,?,?)", (id_, millis(start), millis(end), 1, 0, miles))
    intervals = [
        ("d1", "DRIVE", "12:00", "13:00"), ("d2", "DRIVE", "18:00", "19:00"),
        ("i1", "IDLE", "15:00", "16:00"), ("i2", "IDLE", "19:00", "20:00"),
    ]
    for id_, bucket, start, end in intervals:
        connection.execute("INSERT INTO activity_intervals VALUES (?,?,?,?,?)", (id_, "qa-monday", bucket, millis(f"2026-08-24T{start}:00Z"), millis(f"2026-08-24T{end}:00Z")))
    visits = [
        ("v1", "qa-a", "Original Site", "QA-A", "2026-08-24T13:00:00Z", "2026-08-24T15:00:00Z"),
        ("v2", "qa-b", "Twin Site", "QA-B", "2026-08-24T16:00:00Z", "2026-08-24T18:00:00Z"),
        ("v3", "qa-a", "Twin Site", "QA-A", "2026-08-26T03:30:00Z", "2026-08-26T05:30:00Z"),
        ("v4", "qa-legacy", "Legacy Site", None, "2026-08-28T12:00:00Z", "2026-08-28T13:00:00Z"),
        ("v5", "qa-prior", "Previous Week Site", "QA-P", "2026-08-21T12:00:00Z", "2026-08-21T13:00:00Z"),
    ]
    for id_, site, name, job, start, end in visits:
        connection.execute("INSERT INTO work_site_sessions VALUES (?,?,?,?,?,?)", (id_, site, name, job, millis(start), millis(end)))
    connection.commit()
    connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    connection.close()
    adb(args.device, "shell", "run-as", PACKAGE, "rm", "-f", "databases/time_tracker.db-wal", "databases/time_tracker.db-shm")
    adb(args.device, "shell", "-T", f"run-as {PACKAGE} sh -c 'cat > databases/time_tracker.db'", input=db.read_bytes())
    expected = {"week": "2026-08-24", "home": "85h", "work": "11h", "drive": "2h", "site": "7h", "idle": "2h", "review": "0m", "miles": "44", "worksites": {"qa-a": "4h", "qa-b": "2h", "qa-legacy": "1h"}}
    (args.output / "expected.json").write_text(json.dumps(expected, indent=2))
    print(json.dumps(expected))


if __name__ == "__main__":
    main()
