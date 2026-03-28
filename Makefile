.PHONY: help up down logs build test seed ps infra-up infra-down verify-ch

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: ## Start all infrastructure and app containers
	docker compose up -d --build

down: ## Stop all containers and remove orphans
	docker compose down --remove-orphans

logs: ## Tail logs for api and worker
	docker compose logs -f --tail=200 api worker

ps: ## Show running containers
	docker compose ps

infra-up: ## Start only infrastructure (Postgres, ClickHouse, Kafka, Kafka UI)
	docker compose up -d postgres clickhouse zookeeper kafka kafka-init kafka-ui

infra-down: ## Stop only infrastructure containers
	docker compose stop postgres clickhouse zookeeper kafka kafka-ui
	docker compose rm -f kafka-init

infra-logs: ## Tail infrastructure logs
	docker compose logs -f --tail=200 postgres clickhouse kafka

verify-ch: ## Verify ClickHouse tables exist
	@bash scripts/verify-clickhouse.sh

build: ## Build Java modules
	./gradlew :apps:api:build :apps:worker:build

test: ## Run all Java tests
	./gradlew :apps:api:test :apps:worker:test

seed: ## Run seed demo script (requires API key)
	node scripts/seed-demo.mjs

clean: ## Remove Docker volumes (⚠ destructive)
	@echo "This will delete all data volumes. Press Ctrl+C to abort."
	@sleep 3
	docker compose down -v
