#!/bin/bash
# Creates topics and grants each service only the access it needs.
# Retry/DLT topics created by @RetryableTopic use a per-service suffix,
# so each service gets a prefixed ACL for its own retry topics only.
set -euo pipefail

BS=kafka:29092
CFG=/tmp/admin.properties
cat > "$CFG" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=SCRAM-SHA-512
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="admin" password="${KAFKA_ADMIN_PASSWORD}";
EOF

until kafka-topics --bootstrap-server "$BS" --command-config "$CFG" --list >/dev/null 2>&1; do
  echo "Waiting for Kafka..."
  sleep 2
done

for topic in transaction.created fraud.analysis.completed audit.event.created; do
  kafka-topics --bootstrap-server "$BS" --command-config "$CFG" \
    --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1
done

acl() {
  local user="$1"; shift
  kafka-acls --bootstrap-server "$BS" --command-config "$CFG" \
    --add --allow-principal "User:$user" "$@" >/dev/null
}
produce()     { acl "$1" --operation Write --operation Describe --topic "$2"; }
consume()     { acl "$1" --operation Read --operation Describe --topic "$2"; }
group()       { acl "$1" --operation Read --group "$2" --resource-pattern-type prefixed; }
retry_topics(){ acl "$1" --operation All --topic "$2" --resource-pattern-type prefixed; }

# transaction-service: publishes transactions, applies fraud decisions
produce      transaction-service transaction.created
consume      transaction-service fraud.analysis.completed
group        transaction-service transaction-status-updater
retry_topics transaction-service fraud.analysis.completed-txn-

# fraud-detection-service: the only writer of fraud decisions
consume      fraud-detection-service transaction.created
produce      fraud-detection-service fraud.analysis.completed
produce      fraud-detection-service audit.event.created
group        fraud-detection-service fraud-detection-service
retry_topics fraud-detection-service transaction.created-fraud-

# notification-service
consume      notification-service fraud.analysis.completed
group        notification-service notification-service
retry_topics notification-service fraud.analysis.completed-notification-

# audit-service: reads every domain topic
for topic in audit.event.created transaction.created fraud.analysis.completed; do
  consume      audit-service "$topic"
  retry_topics audit-service "$topic-audit-"
done
group audit-service audit-service

# api-gateway: SSE feed (one consumer group per gateway instance)
consume api-gateway transaction.created
consume api-gateway fraud.analysis.completed
group   api-gateway api-gateway-sse

echo "Kafka topics and ACLs are ready."
