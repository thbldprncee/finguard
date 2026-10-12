#!/bin/bash
# Registers SCRAM credentials in ZooKeeper before the broker starts
# (the broker itself authenticates inter-broker traffic as "admin").
set -euo pipefail

ZK=zookeeper:2181
cub zk-ready "$ZK" 120

add_user() {
  kafka-configs --zookeeper "$ZK" --alter \
    --add-config "SCRAM-SHA-512=[iterations=8192,password=$2]" \
    --entity-type users --entity-name "$1"
}

add_user admin                   "$KAFKA_ADMIN_PASSWORD"
add_user transaction-service     "$KAFKA_TXN_PASSWORD"
add_user fraud-detection-service "$KAFKA_FRAUD_PASSWORD"
add_user notification-service    "$KAFKA_NOTIFICATION_PASSWORD"
add_user audit-service           "$KAFKA_AUDIT_PASSWORD"
add_user api-gateway             "$KAFKA_GATEWAY_PASSWORD"
