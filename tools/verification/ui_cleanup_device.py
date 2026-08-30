"""Small, manually invoked ADB primitives for UI-cleanup evidence. No reset or seed command."""

import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(device, *args, binary=False):
    result = subprocess.run(["adb", "-s", device, *args], capture_output=True, timeout=30, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def hierarchy(device):
    adb(device, "shell", "uiautomator", "dump", "/sdcard/window.xml")
    return adb(device, "shell", "cat", "/sdcard/window.xml")


def select(xml, selector):
    nodes = list(ET.fromstring(xml).iter("node"))
    found = [n for n in nodes if n.get("resource-id") == selector or n.get("text") == selector or n.get("content-desc") == selector]
    if not found:
        raise ValueError(f"Visible control not found: {selector}")
    node = found[0]
    if node.get("enabled") == "false":
        raise ValueError(f"Visible control is disabled: {selector}")
    points = list(map(int, re.findall(r"\d+", node.get("bounds"))))
    return (points[0] + points[2]) // 2, (points[1] + points[3]) // 2


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("device")
    parser.add_argument("output", type=Path)
    parser.add_argument("action", choices=["inspect", "capture", "tap", "text", "back", "swipe"])
    parser.add_argument("values", nargs="*")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.action == "tap":
        x, y = select(hierarchy(args.device), args.values[0])
        adb(args.device, "shell", "input", "tap", str(x), str(y))
    elif args.action == "text":
        xml = hierarchy(args.device)
        x, y = select(xml, args.values[0])
        field = next(n for n in ET.fromstring(xml).iter("node") if n.get("resource-id") == args.values[0])
        delete_count = max(8, len(field.get("text", "")) + 2)
        adb(args.device, "shell", "input", "tap", str(x), str(y))
        # Samsung's IME can consume Ctrl+A instead of selecting the whole field.
        # Move to the end and delete explicitly, including text to the right of a cursor.
        adb(args.device, "shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb(args.device, "shell", "input", "keyevent", *(["KEYCODE_DEL"] * delete_count))
        adb(args.device, "shell", "input", "keyevent", *(["KEYCODE_FORWARD_DEL"] * delete_count))
        # ADB shell accepts an argument array; quote only the input text for its remote shell.
        text = args.values[1].replace(" ", "%s")
        adb(args.device, "shell", "input", "text", "'" + text.replace("'", "'\\''") + "'")
    elif args.action == "back":
        adb(args.device, "shell", "input", "keyevent", "KEYCODE_BACK")
    elif args.action == "swipe":
        adb(args.device, "shell", "input", "swipe", *args.values)
    elif args.action in ("inspect", "capture"):
        xml = hierarchy(args.device)
        if args.action == "capture":
            name = args.values[0]
            (args.output / f"{name}.xml").write_text(xml, encoding="utf-8")
            (args.output / f"{name}.png").write_bytes(adb(args.device, "exec-out", "screencap", "-p", binary=True))
        for node in (ET.fromstring(xml).iter("node") if args.action == "inspect" else []):
            text = node.get("text") or node.get("content-desc")
            tag = node.get("resource-id")
            if text or tag:
                print(json.dumps({"text": text, "tag": tag, "bounds": node.get("bounds")}, ensure_ascii=True))
    with (args.output / "actions.jsonl").open("a", encoding="utf-8") as log:
        log.write(json.dumps({"time": time.time(), "device": args.device, "action": args.action, "values": args.values}) + "\n")


if __name__ == "__main__":
    main()
