#!/usr/bin/env bash
set -euo pipefail

PLAN_FILE="${1:-.planning/ticket-hunter-reflow/task_plan.md}"

if [[ ! -f "$PLAN_FILE" ]]; then
  echo "MISSING PLAN: $PLAN_FILE"
  exit 1
fi

INCOMPLETE=$(grep -c '\*\*Status:\*\* pending\|\*\*Status:\*\* in_progress' "$PLAN_FILE" || true)

if [[ "$INCOMPLETE" -gt 0 ]]; then
  echo "INCOMPLETE PHASES: $INCOMPLETE"
  grep '\*\*Status:\*\*' "$PLAN_FILE" || true
  exit 1
fi

echo "ALL PHASES COMPLETE"
