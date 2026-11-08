#!/usr/bin/env bash
# ==============================================================================
# Mersin University BAP Regulation Compliance & Pre-Review System (bap-advisor)
# macOS Development Environment Bootstrap & Verification Script
# ==============================================================================

set -euo pipefail

BOLD="\033[1m"
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
RESET="\033[0m"

echo -e "${BOLD}=== BAP Advisor macOS Development Environment Bootstrap ===${RESET}\n"

# 1. Check or create .env file
if [ ! -f .env ]; then
    echo -e "${YELLOW}[!] .env not found. Creating from .env.example...${RESET}"
    cp .env.example .env
    echo -e "${GREEN}[✓] .env created successfully.${RESET}"
else
    echo -e "${GREEN}[✓] .env already exists.${RESET}"
fi

# Ensure storage and regulation directories exist
mkdir -p data/uploads data/regulations
echo -e "${GREEN}[✓] Storage directories verified (data/uploads, data/regulations).${RESET}"

# 2. Check Java 17
JAVA_17_PATH="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [ -d "$JAVA_17_PATH" ]; then
    export JAVA_HOME="$JAVA_17_PATH"
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_VER=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
    echo -e "${GREEN}[✓] Java 17 detected: ${JAVA_VER} (JAVA_HOME=$JAVA_HOME)${RESET}"
else
    echo -e "${RED}[✗] OpenJDK 17 not found at $JAVA_17_PATH${RESET}"
    echo -e "    Please install it via: brew install openjdk@17"
    exit 1
fi

# 3. Check uv and Python 3.12
if command -v uv >/dev/null 2>&1; then
    UV_VER=$(uv --version)
    echo -e "${GREEN}[✓] uv detected: ${UV_VER}${RESET}"
    
    # Check if Python 3.12 is available to uv
    if uv python find 3.12 >/dev/null 2>&1; then
        PY312_PATH=$(uv python find 3.12)
        echo -e "${GREEN}[✓] Python 3.12 detected for uv: ${PY312_PATH}${RESET}"
    else
        echo -e "${YELLOW}[!] Python 3.12 not found in uv cache. Installing via uv...${RESET}"
        uv python install 3.12
    fi
else
    echo -e "${RED}[✗] uv package manager not found.${RESET}"
    echo -e "    Please install it via: brew install uv"
    exit 1
fi

# 4. Check Node.js and npm
if command -v node >/dev/null 2>&1; then
    NODE_VER=$(node -v)
    echo -e "${GREEN}[✓] Node.js detected: ${NODE_VER}${RESET}"
else
    echo -e "${RED}[✗] Node.js not found. Please install via: brew install node${RESET}"
    exit 1
fi

# 5. Database Options Hint
echo -e "\n${BOLD}Database (PostgreSQL 16 + pgvector) Setup Options:${RESET}"
if command -v docker >/dev/null 2>&1; then
    echo -e "  ${GREEN}Option A (Docker - Recommended):${RESET} make dev-db  (or docker compose -f docker-compose.dev.yml up -d)"
else
    echo -e "  ${YELLOW}Option B (Homebrew Native):${RESET}"
    echo -e "    brew install postgresql@16"
    echo -e "    brew install pgvector"
    echo -e "    brew services start postgresql@16"
    echo -e "    psql postgres -c \"CREATE USER bap_user WITH PASSWORD 'bap_password_change_me'; CREATE DATABASE bap_advisor OWNER bap_user;\""
    echo -e "    psql -U bap_user -d bap_advisor -f infra/postgres/init-db.sql"
fi

echo -e "\n${GREEN}${BOLD}=== macOS Environment Ready for Parallel Development ===${RESET}\n"
