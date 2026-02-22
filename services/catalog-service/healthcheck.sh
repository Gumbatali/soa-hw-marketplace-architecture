#!/bin/sh
set -e

# Дать сервису время подняться (grace period)
sleep 30

# интервалы между попытками: 5,10,15,20,30 секунд
DELAYS="5 10 15 20 30"

check() {
  python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" >/dev/null 2>&1
}

# первая попытка сразу после sleep 30
if check; then
  exit 0
fi

# дальше попытки с нарастающими паузами
for d in $DELAYS; do
  sleep "$d"
  if check; then
    exit 0
  fi
done

exit 1
