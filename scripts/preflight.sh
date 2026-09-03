#!/usr/bin/env bash
# Preflight ПЕРЕД сборкой/публикацией приложения vpnka-android.
#
# Модель веток = окружение:
#   master → ПРОД      (latest.json / vpnka.apk)
#   dev    → ДЕВ/БЕТА  (beta.json / beta.apk)
#
# Зачем: не дать собрать релиз из устаревшей или грязной рабочей копии —
# ровно это привело к публикации старой версии поверх боевой.
set -euo pipefail
cd "$(dirname "$0")/.."
cd "$(git rev-parse --show-toplevel)"
git fetch origin -q

BR=$(git rev-parse --abbrev-ref HEAD)
case "$BR" in
  master) CH="ПРОД (latest.json / vpnka.apk)";;
  dev)    CH="ДЕВ/БЕТА (beta.json / beta.apk)";;
  *) echo "ERROR: ветка '$BR' — не master и не dev; релиз собирают только с них." >&2; exit 1;;
esac

LOCAL=$(git rev-parse HEAD)
REMOTE=$(git ls-remote --heads origin "$BR" | awk '{print $1}')
if [ -z "$REMOTE" ]; then echo "ERROR: ветка $BR не найдена на origin." >&2; exit 1; fi
if [ "$LOCAL" != "$REMOTE" ]; then
  echo "ERROR: HEAD ($BR) != origin/$BR — копия рассинхронизирована." >&2
  echo "  локально ${LOCAL:0:8} / origin ${REMOTE:0:8}. Сначала: git pull --ff-only" >&2
  exit 1
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: есть незакоммиченные изменения — коммить/прячь перед сборкой." >&2
  git status --short >&2
  exit 1
fi

VER=$(grep -oE 'versionName = "[0-9.]+"' V2rayNG/app/build.gradle.kts | grep -oE '[0-9.]+' | head -1)
echo "✓ preflight ок: $BR = $CH; версия $VER; в синхроне с origin, дерево чистое."
