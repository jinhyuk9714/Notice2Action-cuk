#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Any

STATE_DIR_NAME = ".claude/state"


def _read_stdin_json() -> dict[str, Any]:
    raw = sys.stdin.read().strip()
    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def _extract_paths(payload: dict[str, Any]) -> list[str]:
    candidates: list[str] = []
    tool_input = payload.get("tool_input")

    if isinstance(tool_input, dict):
        for key in ("file_path", "path"):
            value = tool_input.get(key)
            if isinstance(value, str):
                candidates.append(value)

        edits = tool_input.get("edits")
        if isinstance(edits, list):
            for edit in edits:
                if isinstance(edit, dict):
                    for key in ("file_path", "path"):
                        value = edit.get(key)
                        if isinstance(value, str):
                            candidates.append(value)

    file_path = payload.get("file_path")
    if isinstance(file_path, str):
        candidates.append(file_path)

    seen: set[str] = set()
    result: list[str] = []
    for item in candidates:
        normalized = item.strip()
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


def main() -> int:
    payload = _read_stdin_json()
    project_dir = Path(os.environ.get("CLAUDE_PROJECT_DIR", Path.cwd()))
    session_id = os.environ.get("CLAUDE_SESSION_ID", "default")

    state_dir = project_dir / STATE_DIR_NAME / session_id
    state_dir.mkdir(parents=True, exist_ok=True)

    edited_files_path = state_dir / "edited_files.json"
    existing: list[str] = []

    if edited_files_path.exists():
        try:
            loaded = json.loads(edited_files_path.read_text(encoding="utf-8"))
            if isinstance(loaded, list):
                existing = [str(item) for item in loaded if isinstance(item, str)]
        except json.JSONDecodeError:
            existing = []

    merged = existing[:]
    for path in _extract_paths(payload):
        if path not in merged:
            merged.append(path)

    edited_files_path.write_text(
        json.dumps(merged, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
