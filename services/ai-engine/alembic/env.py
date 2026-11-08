import os
from logging.config import fileConfig
from sqlalchemy import engine_from_config, pool, text
from alembic import context
from app.config import get_settings
from app.db.models import Base

# Alembic Config object
config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# Set DB URL from settings / env
database_url = os.environ.get("DATABASE_URL") or get_settings().database_url
config.set_main_option("sqlalchemy.url", database_url)

target_metadata = Base.metadata


def include_object(object, name, type_, reflected, compare_to):
    # Only manage objects in ai_rag schema
    if type_ == "table" and object.schema != "ai_rag":
        return False
    return True


def run_migrations_offline() -> None:
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        version_table_schema="ai_rag",
        include_schemas=True,
        include_object=include_object,
    )

    with context.begin_transaction():
        context.execute("CREATE SCHEMA IF NOT EXISTS ai_rag;")
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        connection.execute(text("CREATE SCHEMA IF NOT EXISTS ai_rag;"))
        try:
            connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector;"))
        except Exception:
            pass
        connection.commit()

        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            version_table_schema="ai_rag",
            include_schemas=True,
            include_object=include_object,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
