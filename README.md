# MaizLogger
A full-stack Mobile Observability Platform MVP that collects React Native telemetry (events, API timing, errors) via a Spring Boot API → Kafka → ClickHouse pipeline, with a Spring Kafka worker for processing/DLQ handling and a React dashboard for visualizing metrics, latency percentiles, and error feeds.
