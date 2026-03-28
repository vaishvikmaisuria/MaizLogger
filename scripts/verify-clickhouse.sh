#!/bin/bash
# verify-clickhouse.sh — Verify ClickHouse is up and mobobs tables exist
set -euo pipefail

CH_HOST="${CLICKHOUSE_HOST:-localhost}"
CH_PORT="${CLICKHOUSE_PORT:-8123}"
CH_URL="http://${CH_HOST}:${CH_PORT}"

echo "==> Checking ClickHouse connectivity..."
if ! curl -sf "${CH_URL}/?query=SELECT%201" > /dev/null 2>&1; then
  echo "FAIL: ClickHouse not reachable at ${CH_URL}"
  exit 1
fi
echo "OK: ClickHouse is reachable"

echo ""
echo "==> Checking database 'mobobs' exists..."
DB_EXISTS=$(curl -sf "${CH_URL}/?query=SELECT%20name%20FROM%20system.databases%20WHERE%20name%3D%27mobobs%27")
if [ -z "$DB_EXISTS" ]; then
  echo "FAIL: Database 'mobobs' does not exist"
  exit 1
fi
echo "OK: Database 'mobobs' exists"

echo ""
echo "==> Listing tables in 'mobobs'..."
TABLES=$(curl -sf "${CH_URL}/?query=SELECT%20name%20FROM%20system.tables%20WHERE%20database%3D%27mobobs%27%20ORDER%20BY%20name")
echo "$TABLES"

EXPECTED_TABLES=("mobile_events" "mobile_api_calls" "mobile_errors" "mobile_sessions")
for tbl in "${EXPECTED_TABLES[@]}"; do
  if echo "$TABLES" | grep -q "^${tbl}$"; then
    echo "  ✓ ${tbl}"
  else
    echo "  ✗ ${tbl} — MISSING!"
    exit 1
  fi
done

echo ""
echo "==> Listing materialized views in 'mobobs'..."
VIEWS=$(curl -sf "${CH_URL}/" --data-urlencode "query=SELECT name FROM system.tables WHERE database='mobobs' AND engine='MaterializedView' ORDER BY name")
echo "$VIEWS"

echo ""
echo "==> All checks passed!"
