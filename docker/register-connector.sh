#!/usr/bin/env bash
# Register the Debezium outbox connector with Kafka Connect.
# Usage: ./register-connector.sh [connect-url]
#   Default connect-url: http://localhost:8083

set -euo pipefail

CONNECT_URL="${1:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Waiting for Kafka Connect to be ready..."
until curl -s "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
    sleep 2
done

echo "Registering finstream-outbox-connector..."
curl -s -X POST "${CONNECT_URL}/connectors" \
    -H "Content-Type: application/json" \
    -d @"${SCRIPT_DIR}/debezium-connector.json" | python3 -m json.tool 2>/dev/null || true

echo "Done. Connector status:"
curl -s "${CONNECT_URL}/connectors/finstream-outbox-connector/status" | python3 -m json.tool 2>/dev/null || true
