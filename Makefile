# ==============================================================================
# Mersin University BAP Regulation Compliance & Pre-Review System (bap-advisor)
# Developer Automation Makefile
# ==============================================================================

SHELL := /bin/bash
export JAVA_HOME ?= /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH := $(JAVA_HOME)/bin:/opt/homebrew/bin:$(PATH)

.PHONY: all help bootstrap dev-db dev-db-down fetch-regulations run-core run-ai run-web test-all test-core test-ai validate-contracts clean

help:
	@echo "bap-advisor Makefile commands:"
	@echo "  make bootstrap          - Prepare environment, .env, Java, Python uv, Node"
	@echo "  make dev-db             - Start PostgreSQL 16 + pgvector container"
	@echo "  make dev-db-down        - Stop PostgreSQL container"
	@echo "  make fetch-regulations  - Fetch MEÜ regulation PDFs into data/regulations/"
	@echo "  make run-core           - Start Spring Boot 3 Core API (:8080)"
	@echo "  make run-ai             - Start Python FastAPI AI Engine (:8001)"
	@echo "  make run-web            - Start Vite Web Dashboard (:5173 / :3000)"
	@echo "  make test-core          - Run Spring Boot tests"
	@echo "  make test-ai            - Run Python FastAPI tests"
	@echo "  make test-all           - Run all backend tests"
	@echo "  make validate-contracts - Validate OpenAPI specifications & JSON Schemas"

bootstrap:
	@bash scripts/dev-bootstrap-mac.sh

dev-db:
	@docker compose -f docker-compose.dev.yml up -d

dev-db-down:
	@docker compose -f docker-compose.dev.yml down

fetch-regulations:
	@uv run --python 3.12 scripts/fetch-meu-regulations.py

run-core:
	@cd services/core-api && ./mvnw spring-boot:run

run-ai:
	@cd services/ai-engine && uv run uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

run-web:
	@cd apps/web-dashboard && npm run dev

test-core:
	@cd services/core-api && ./mvnw test

test-ai:
	@cd services/ai-engine && uv run pytest

test-all: test-core test-ai

validate-contracts:
	@uv run --with pyyaml --python 3.12 python -c "import yaml, json, glob; [yaml.safe_load(open(f, 'r', encoding='utf-8')) for f in glob.glob('contracts/*.yaml')]; [json.load(open(f, 'r', encoding='utf-8')) for f in glob.glob('contracts/schemas/*.json')]; [json.load(open(f, 'r', encoding='utf-8')) for f in glob.glob('contracts/seed-data/*.json')]; print('All YAML & JSON contracts valid!')"

clean:
	@rm -rf services/core-api/target
	@rm -rf services/ai-engine/.pytest_cache services/ai-engine/__pycache__
