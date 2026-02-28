#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from typing import Any

DENY_PATTERNS = [
    r"\brm\s+-rf\s+/",
    r"\bsudo\s+rm\b",
    r"\bcurl\b.+\|\s*(bash|sh)\b",
    r"\bwget\b.+\|\s*(bash|sh)\b",
    r"\bmkfs\b",
    r"\bdd\s+if=",
    r"\bchmod\s+-R\s+777\b",
]


def _read_stdin_json() -> dict[str, Any]:
    raw = sys.stdin.read().strip()
    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def main() -> int:
    payload = _read_stdin_json()
    tool_input = payload.get("tool_input")
    command = ""

    if isinstance(tool_input, dict):
        maybe_command = tool_input.get("command")
        if isinstance(maybe_command, str):
            command = maybe_command

    for pattern in DENY_PATTERNS:
        if re.search(pattern, command):
            print(
                json.dumps(
                    {
                        "decision": "deny",
                        "message": f"Blocked risky Bash command matching pattern: {pattern}",
                    }
                )
            )
            return 0

    print(json.dumps({"decision": "allow"}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
