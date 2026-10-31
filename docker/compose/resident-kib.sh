#!/bin/sh
set -eu
# PID 1 is the JVM (or Redis/PostgreSQL). Also account for resident anonymous
# and file-mapped pages of ALL processes through the container's cgroup.
pid_rss=$(awk '/^VmRSS:/ {print $2}' /proc/1/status)
test -n "$pid_rss"
if [ -r /sys/fs/cgroup/memory.stat ]; then
    resident=$(awk '$1 == "anon" || $1 == "file_mapped" {n += $2; found++} END {if (found != 2) exit 1; printf "%.0f", n / 1024}' /sys/fs/cgroup/memory.stat)
elif [ -r /sys/fs/cgroup/memory/memory.stat ]; then
    resident=$(awk '$1 == "total_rss" || $1 == "total_mapped_file" {n += $2; found++} END {if (found != 2) exit 1; printf "%.0f", n / 1024}' /sys/fs/cgroup/memory/memory.stat)
else
    echo 'Container cgroup resident accounting unavailable; refusing RSS PASS' >&2
    exit 1
fi
printf '%s %s\n' "$pid_rss" "$resident"
