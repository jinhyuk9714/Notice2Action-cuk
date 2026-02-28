#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Any

RULES_PATH = Path(".claude/skills/skill-rules.json")


def _read_stdin_json() -> dict[str, Any]:
    raw = sys.stdin.read().strip()
    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def _load_rules(project_dir: Path) -> dict[str, Any]:
    path = project_dir / RULES_PATH
    if not path.exists():
        return {}
    try:
        loaded = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    return loaded if isinstance(loaded, dict) else {}


def _match_keywords(text: str, keywords: list[str]) -> bool:
    lowered = text.lower()
    return any(keyword.lower() in lowered for keyword in keywords)


def _match_intents(text: str, patterns: list[str]) -> bool:
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def _path_matches(path: str, fragments: list[str]) -> bool:
    return any(fragment.replace("*", "").strip("/") in path for fragment in fragments if fragment)


def main() -> int:
    payload = _read_stdin_json()
    project_dir = Path(os.environ.get("CLAUDE_PROJECT_DIR", Path.cwd()))
    rules = _load_rules(project_dir)

    skills = rules.get("skills", {})
    if not isinstance(skills, dict):
        return 0

    prompt = ""
    for key in ("prompt", "text", "user_prompt"):
        value = payload.get(key)
        if isinstance(value, str):
            prompt = value
            break

    related_paths: list[str] = []
    tool_input = payload.get("tool_input")
    if isinstance(tool_input, dict):
        for candidate_key in ("file_path", "path"):
            value = tool_input.get(candidate_key)
            if isinstance(value, str):
                related_paths.append(value)

    matched: list[tuple[str, str]] = []
    for skill_name, skill_config in skills.items():
        if not isinstance(skill_config, dict):
            continue

        matched_here = False

        prompt_triggers = skill_config.get("promptTriggers")
        if isinstance(prompt_triggers, dict):
            keywords = prompt_triggers.get("keywords", [])
            intents = prompt_triggers.get("intentPatterns", [])

            if isinstance(keywords, list) and _match_keywords(prompt, [str(item) for item in keywords]):
                matched_here = True

            if isinstance(intents, list) and _match_intents(prompt, [str(item) for item in intents]):
                matched_here = True

        file_triggers = skill_config.get("fileTriggers")
        if isinstance(file_triggers, dict):
            path_patterns = file_triggers.get("pathPatterns", [])
            if isinstance(path_patterns, list):
                for path in related_paths:
                    if _path_matches(path, [str(item) for item in path_patterns]):
                        matched_here = True
                        break

        if matched_here:
            description = str(skill_config.get("description", skill_name))
            matched.append((skill_name, description))

    if not matched:
        return 0

    lines = [
        "Recommended project skills for this request:",
        *[f"- {name}: {description}" for name, description in matched[:6]],
    ]

    print(json.dumps({"additionalContext": "\n".join(lines)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
