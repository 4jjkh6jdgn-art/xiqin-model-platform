#!/bin/sh
# 启动虚拟 X server（headless-gl 需要），然后执行容器主命令
set -e

if [ -z "$DISPLAY" ]; then
  echo "[entrypoint] 启动 Xvfb :99 ..."
  Xvfb :99 -screen 0 1024x1024x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &
  export DISPLAY=:99
  sleep 1
fi

echo "[entrypoint] DISPLAY=$DISPLAY"
exec "$@"
