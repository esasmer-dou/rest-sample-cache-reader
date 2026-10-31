#!/usr/bin/env bash
set -euo pipefail
# Linux prerequisites: Docker Engine + Compose v2 (--wait), curl, jq, timeout.
# --prepare additionally needs JDK 21, Maven, and the checkout's dependencies
# installed/resolvable using the caller's Maven settings (never copied to images).
# Full:   bash scripts/verify-compose-topology.sh --prepare
# Native: bash scripts/verify-compose-topology.sh --prepare --native-provider
# Static: bash scripts/verify-compose-topology.sh --config-only
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
workspace=$(cd -- "$script_dir/../.." && pwd)
topology="$script_dir/../docker/compose"
native=false prepare=false keep=false config_only=false
for arg in "$@"; do
    case "$arg" in
        --native-provider) native=true ;;
        --prepare) prepare=true ;;
        --keep) keep=true ;;
        --config-only) config_only=true ;;
        --help) sed -n '3,10p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done
command -v docker >/dev/null
project="wp3-$(date -u +%Y%m%d%H%M%S)-$$"
compose=(docker compose -p "$project" -f "$topology/docker-compose.yml")
if $native; then compose+=(-f "$topology/native-provider.yml"); fi
if $config_only; then "${compose[@]}" config --quiet; exit; fi
for tool in curl jq timeout; do command -v "$tool" >/dev/null; done
dc() { timeout 30 "${compose[@]}" "$@"; }
if $prepare; then
    command -v mvn >/dev/null
    for repo in rest-sample-cache-reader rest-sample-cache-writer rest-sample-dubbo-provider rest-sample-dubbo-consumer; do
        profile=() output=target
        case "$repo" in
            rest-sample-dubbo-provider)
                if $native; then profile=(-Pnative-provider); output=target/native-provider
                else profile=(-Pfull-provider); fi ;;
            rest-sample-dubbo-consumer)
                if $native; then profile=(-Pnative-static-consumer)
                else profile=(-Pfull-dubbo-consumer); fi ;;
        esac
        # clean avoids stale classes/profiles; no test execution.
        timeout 300 mvn -B -ntp -f "$workspace/$repo/pom.xml" "${profile[@]}" -DskipTests clean package dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$workspace/$repo/$output/dependency"
    done
fi
mkdir -p "$workspace/.tmp"
evidence=$(mktemp -d "$workspace/.tmp/$project.XXXXXX")
started=false
finish() {
    status=$?
    trap - EXIT
    if $started; then
        dc ps -a >"$evidence/containers.txt" 2>&1 || true
        dc logs --no-color --tail 200 >"$evidence/containers.log" 2>&1 || true
        if ! $keep; then dc down --volumes --remove-orphans >"$evidence/cleanup.log" 2>&1 || status=1; fi
    fi
    printf 'exit=%s\nmode=%s\n' "$status" "$($native && echo native-catalog-only || echo full)" >"$evidence/result.properties"
    echo "Evidence: $evidence"
    exit "$status"
}
trap finish EXIT
dc config >"$evidence/compose.resolved.yml"
timeout 600 "${compose[@]}" build >"$evidence/build.log" 2>&1
started=true
timeout 210 "${compose[@]}" up -d --wait --wait-timeout 180 >"$evidence/startup.log" 2>&1
services=(postgres redis cache-writer cache-reader dubbo-provider dubbo-consumer)
for service in "${services[@]}"; do
    cid=$(dc ps -q "$service")
    [[ -n "$cid" ]]
    limits=$(timeout 10 docker inspect --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}} {{.State.Health.Status}} {{.State.OOMKilled}}' "$cid")
    [[ "$limits" == '134217728 1000000000 healthy false' ]] || { echo "$service invalid limits/health: $limits" >&2; exit 1; }
done
reader="http://127.0.0.1:${READER_PORT:-18081}"
consumer="http://127.0.0.1:${CONSUMER_PORT:-18082}"
request() { curl --fail --silent --show-error --connect-timeout 2 --max-time 5 "$@"; }
sql() { dc exec -T postgres psql -U reactor -d reactor_sample -v ON_ERROR_STOP=1 -Atqc "$1" | tr -d '\r'; }
redis() { dc exec -T redis redis-cli --raw "$@" | tr -d '\r'; }
json_matches() { jq -e --arg no "$customer_no" --arg segment "$segment" '([.. | objects | .customerNo? // empty] | index($no)) != null and ([.. | objects | .segment? // empty] | index($segment)) != null' >/dev/null; }
await_cache() {
    local deadline=$((SECONDS + 40)) body
    while (( SECONDS < deadline )); do
        if body=$(request "$reader/api/v1/cache/customers/$customer_id") && json_matches <<<"$body"; then
            printf '%s\n' "$body" >"$evidence/cache-$segment.json"; return
        fi
        sleep 1
    done
    echo "Cache projection did not converge: customer=$customer_id segment=$segment" >&2; return 1
}
request "$reader/app/health" >"$evidence/reader-health.json"
request "$reader/app/readiness" >"$evidence/reader-ready.json"
request "$consumer/app/health" >"$evidence/consumer-health.json"
request "$consumer/app/ready" >"$evidence/consumer-ready.json"
request "$consumer/api/v1/catalog/nested" >"$evidence/catalog.json"
jq -e 'type == "object"' "$evidence/catalog.json" >/dev/null
if $native; then
    echo 'Native catalog RPC only; SQL mutations below bypass the provider (no customer command/DB RPC support).'
    customer_id=$(sql "select id from sample_customers where customer_no='CUST-1001'")
    request "$reader/api/v1/cache/customers/$customer_id" | jq -e '.. | objects | select(.customerNo? == "CUST-1001")' >"$evidence/seed-cache.json"
fi
customer_no="WP3-$(date +%s)-$$" segment=wp3-created
before=$(redis GET crm.customer.detail:current)
[[ "$before" =~ ^([1-9][0-9]*)\|.+$ ]]; before_fence=${BASH_REMATCH[1]}
if $native; then
    sql "insert into sample_customers (customer_no,full_name,segment,email) values ('$customer_no','Compose Smoke','wp3-created','smoke@example.invalid') returning id" >"$evidence/native-sql-create.txt"
else
    body=$(jq -nc --arg no "$customer_no" '{customerNo:$no,fullName:"Compose Smoke",segment:"wp3-created",email:"smoke@example.invalid",requestId:$no}')
    request -X POST -H 'Content-Type: application/json' --data-binary "$body" "$consumer/api/v1/customers" >"$evidence/create.json"
fi
customer_id=$(sql "select id from sample_customers where customer_no='$customer_no' and segment='wp3-created'")
[[ "$customer_id" =~ ^[1-9][0-9]*$ ]]
if ! $native; then request "$consumer/api/v1/customers/db/$customer_id" | json_matches; fi
await_cache
segment=wp3-patched
if $native; then
    sql "update sample_customers set segment='wp3-patched',updated_at=now() where id=$customer_id returning id" >"$evidence/native-sql-patch.txt"
else
    request -X PATCH -H 'Content-Type: application/json' --data-binary '{"segment":"wp3-patched","requestId":"wp3-segment"}' "$consumer/api/v1/customers/$customer_id/segment" >"$evidence/patch.json"
fi
[[ "$(sql "select segment from sample_customers where id=$customer_id")" == "$segment" ]]
if ! $native; then request "$consumer/api/v1/customers/db/$customer_id" | json_matches; fi
await_cache
pointer=$(redis GET crm.customer.detail:current)
[[ "$pointer" =~ ^([1-9][0-9]*)\|(.+)$ ]]
(( BASH_REMATCH[1] > before_fence )); version=${BASH_REMATCH[2]}
redis GET "crm.customer.detail:v:$version:id:$customer_id" | json_matches
ttl=$(redis PTTL "crm.customer.detail:v:$version:id:$customer_id"); (( ttl > 0 ))
printf '%s\n' "$pointer" >"$evidence/fenced-pointer.txt"
if $native; then
    sql "delete from sample_customers where id=$customer_id returning id" >"$evidence/native-sql-delete.txt"
else
    request -X DELETE -H 'Content-Type: application/json' --data-binary '{}' "$consumer/api/v1/customers/$customer_id" >"$evidence/delete.json"
fi
[[ "$(sql "select count(*) from sample_customers where id=$customer_id")" == 0 ]]
printf 'sample,service,pid1_rss_kib,cgroup_resident_kib\n' >"$evidence/rss.csv"
for sample in 1 2 3 4 5; do
    for service in "${services[@]}"; do
        read -r pid_rss resident < <(dc exec -T "$service" sh -s <"$topology/resident-kib.sh")
        [[ "$pid_rss" =~ ^[0-9]+$ && "$resident" =~ ^[0-9]+$ ]]
        printf '%s,%s,%s,%s\n' "$sample" "$service" "$pid_rss" "$resident" >>"$evidence/rss.csv"
        (( pid_rss < 131072 && resident < 131072 )) || { echo "$service RSS exceeds strict 128 MiB gate" >&2; exit 1; }
    done
    sleep 1
done
echo 'Topology smoke PASS; RSS is five bounded samples, NOT sustained-load or p99 qualification.'
