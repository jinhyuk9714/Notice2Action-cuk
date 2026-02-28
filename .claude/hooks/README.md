# Claude Code hooks for Notice2Action CUK

Included hooks:
- `skill_activation.py`: 프롬프트/파일 맥락에 맞는 skill 추천
- `file_change_tracker.py`: 최근 수정 파일 추적
- `pretooluse_guard.py`: 위험한 Bash 차단
- `quality_gate.py`: web/api 변경 영역에 대해 최소 타입/품질 검사

## Notes
- quality gate는 기본적으로 fail-open입니다.
- `CLAUDE_QG_STRICT=1`이면 툴이 없을 때도 block합니다.
- API는 `./gradlew`가 있으면 그것을 우선 사용하고, 없으면 `gradle`을 시도합니다.
