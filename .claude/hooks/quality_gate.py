#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path


def _load_edited_files(project_dir: Path) -> list[str]:
    session_id = os.environ.get("CLAUDE_SESSION_ID", "default")
    path = project_dir / ".claude" / "state" / session_id / "edited_files.json"
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    return [str(item) for item in data if isinstance(item, str)]


def _run(cmd: list[str], cwd: Path) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(cwd),
            text=True,
            capture_output=True,
            check=False,
        )
    except FileNotFoundError:
        return 127, f"Missing tool: {cmd[0]}"
    output = ((proc.stdout or "") + "\n" + (proc.stderr or "")).strip()
    return proc.returncode, output


def _strict_mode() -> bool:
    return os.environ.get("CLAUDE_QG_STRICT", "").strip() == "1"


def _find_gradle_command(project_dir: Path) -> list[str] | None:
    wrapper = project_dir / "apps" / "api" / "gradlew"
    if wrapper.exists():
        return [str(wrapper)]
    if shutil.which("gradle") is not None:
        return ["gradle"]
    return None


def main() -> int:
    project_dir = Path(os.environ.get("CLAUDE_PROJECT_DIR", Path.cwd()))
    edited_files = _load_edited_files(project_dir)

    changed_web = any(path.startswith("apps/web/") for path in edited_files)
    changed_api = any(path.startswith("apps/api/") for path in edited_files)

    failures: list[str] = []
    notices: list[str] = []

    if changed_web and (project_dir / "apps/web").exists():
        if shutil.which("npm") is not None:
            code, out = _run(["npm", "run", "typecheck"], project_dir / "apps/web")
            if code != 0:
                failures.append(f"[web] npm run typecheck failed\n{out}")

            code, out = _run(["npm", "run", "build"], project_dir / "apps/web")
            if code != 0:
                failures.append(f"[web] npm run build failed\n{out}")
        elif _strict_mode():
            failures.append("[web] npm not found; cannot run typecheck/build")
        else:
            notices.append("[web] skipped because npm is not installed")

    if changed_api and (project_dir / "apps/api").exists():
        gradle_cmd = _find_gradle_command(project_dir)
        if gradle_cmd is not None:
            code, out = _run(gradle_cmd + ["test"], project_dir / "apps/api")
            if code != 0:
                failures.append(f"[api] {' '.join(gradle_cmd)} test failed\n{out}")
        elif _strict_mode():
            failures.append("[api] gradle/gradlew not found; cannot run backend checks")
        else:
            notices.append("[api] skipped because gradle/gradlew is not installed")

    if failures:
        print(
            json.dumps(
                {
                    "decision": "block",
                    "message": "Quality gate failed:\n\n" + "\n\n".join(failures),
                }
            )
        )
        return 0

    if notices:
        print(json.dumps({"decision": "allow", "message": "\n".join(notices)}))
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
