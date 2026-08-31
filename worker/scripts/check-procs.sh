#!/bin/sh
for p in $(ls /proc/ | grep -E '^[0-9]+$'); do
  if [ -f "/proc/$p/cmdline" ]; then
    echo "$p: $(cat /proc/$p/cmdline | tr '\0' ' ')"
  fi
done
