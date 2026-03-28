.PHONY: help up down logs build test seed

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: ## Start all infrastructure and app containers
	docker compose up -d --build

down: ## Stop all containers
	docker compose down

logs: ## Tail logs for api and worker
	docker compose logs -f --tail=200 api worker

build: ## Build Java modules
	./gradlew :apps:api:build :apps:worker:build

test: ## Run all Java tests
	./gradlew :apps:api:test :apps:worker:test

seed: ## Run seed demo script (requires API key)
	node scripts/seed-demo.mjs
