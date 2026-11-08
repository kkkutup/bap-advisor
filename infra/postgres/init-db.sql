-- ==============================================================================
-- Mersin University BAP Regulation Compliance & Pre-Review System (bap-advisor)
-- PostgreSQL 16 + pgvector Database Initialization Script
-- ==============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- Dual schema isolation:
-- 'core' schema: Owned by Spring Boot 3 Flyway migrations
CREATE SCHEMA IF NOT EXISTS core;

-- 'ai_rag' schema: Owned by Python FastAPI Alembic migrations
CREATE SCHEMA IF NOT EXISTS ai_rag;

-- Grant permissions to current user
GRANT ALL ON SCHEMA core TO CURRENT_USER;
GRANT ALL ON SCHEMA ai_rag TO CURRENT_USER;
