#!/bin/sh
# Claude Code PreToolUse(Bash) 가드: `git commit` 실행 전 스테이지된 추가 내용에서 금칙어를 검사.
# git 훅(.githooks/pre-commit)과 같은 규칙의 중복 안전망(Claude Code 세션용).
# 안전: fail-open — 파싱/실행 오류 시 무조건 통과(exit 0). 명확히 걸릴 때만 차단(exit 2).
input=$(cat 2>/dev/null) || exit 0

# stdin JSON에서 tool_input.command 추출 (node로 견고하게, 실패 시 통과)
cmd=$(printf '%s' "$input" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const j=JSON.parse(s);process.stdout.write((j.tool_input&&j.tool_input.command)||"")}catch(e){}})' 2>/dev/null) || exit 0
[ -n "$cmd" ] || exit 0

# git commit 명령만 대상. --no-verify는 존중.
case "$cmd" in *"git commit"*) : ;; *) exit 0 ;; esac
case "$cmd" in *"--no-verify"*) exit 0 ;; esac

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
denylist=".githooks/denylist.txt"
[ -f "$denylist" ] || exit 0

added=$(git diff --cached --no-color -U0 --diff-filter=ACM -- . ":(exclude)$denylist" 2>/dev/null \
        | grep '^+' | grep -v '^+++' || true)
[ -n "$added" ] || exit 0

while IFS= read -r term; do
  case "$term" in ''|\#*) continue ;; esac
  if printf '%s\n' "$added" | grep -qF -- "$term"; then
    echo "커밋 차단(Claude Code 훅): 금칙어 \"$term\"가 스테이지된 추가 내용에 있습니다. denylist 조정 또는 git commit --no-verify." >&2
    exit 2
  fi
done < "$denylist"
exit 0
