#!/bin/bash
set -euo pipefail
case "${1:?expected reader, consumer, writer or provider}" in
    reader)
        curl --fail --silent --max-time 2 http://127.0.0.1:8080/app/health >/dev/null
        curl --fail --silent --max-time 2 http://127.0.0.1:8080/app/readiness >/dev/null
        ;;
    consumer)
        curl --fail --silent --max-time 2 http://127.0.0.1:8080/app/health >/dev/null
        curl --fail --silent --max-time 3 http://127.0.0.1:8080/app/ready >/dev/null
        ;;
    writer)
        # No HTTP server in this process. PID 1 is Java; require bounded, fenced
        # projection pointers with a live TTL, not merely a reachable Redis port.
        grep -q '^Name:.*java' /proc/1/status
        for projection in detail segment status campaign meta; do
            key="crm.customer.$projection:current"
            pointer=$(timeout 2 redis-cli -h redis -p 6379 --raw GET "$key")
            [[ "$pointer" =~ ^[1-9][0-9]*\|.+$ ]]
            ttl=$(timeout 2 redis-cli -h redis -p 6379 --raw PTTL "$key")
            (( ttl > 0 ))
        done
        ;;
    provider)
        # Provider has no HTTP server. TCP readiness is strengthened by the
        # consumer's actual RPC dependency check and the smoke's PostgreSQL read.
        grep -q '^Name:.*java' /proc/1/status
        timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/20880'
        ;;
    *) exit 2 ;;
esac
