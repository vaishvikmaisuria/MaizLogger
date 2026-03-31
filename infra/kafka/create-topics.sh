#!/bin/bash
# create-topics.sh — Creates Kafka topics for the Mobile Observability Platform
# Run inside the Kafka broker container or with access to kafka-topics.sh

set -euo pipefail

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"

echo "Waiting for Kafka to be ready..."
sleep 10

echo "Creating topics..."

# Raw topics (high-volume: 6 partitions)
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.events.raw --partitions 6 --replication-factor 1

kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.api.raw --partitions 6 --replication-factor 1

# Raw topics (lower-volume: 3 partitions)
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.errors.raw --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.sessions.raw --partitions 3 --replication-factor 1

# Dead-letter queues (3 partitions each)
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.events.dlq --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.api.dlq --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.errors.dlq --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic mobile.sessions.dlq --partitions 3 --replication-factor 1

echo "All topics created."
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list
