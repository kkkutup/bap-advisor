import logging
import time
from contextlib import contextmanager
from typing import Generator, Optional
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker
from app.config import get_settings

logger = logging.getLogger(__name__)

_engine = None
_session_factory = None
_last_pg_check_time = 0.0
_last_pg_check_result = False
_PG_CHECK_TTL_SECONDS = 5.0


def get_engine():
    global _engine, _session_factory
    if _engine is None:
        settings = get_settings()
        url = settings.database_url
        # psycopg 3 connection URL
        _engine = create_engine(
            url,
            pool_pre_ping=True,
            pool_recycle=300,
            connect_args={"connect_timeout": 2},
        )
        _session_factory = sessionmaker(autocommit=False, autoflush=False, bind=_engine)
    return _engine


def get_session_factory() -> sessionmaker:
    global _session_factory
    if _session_factory is None:
        get_engine()
    return _session_factory


def is_postgres_available(force_refresh: bool = False) -> bool:
    """
    Check if PostgreSQL database is reachable. Caches result for short TTL to avoid latency.
    """
    global _last_pg_check_time, _last_pg_check_result
    now = time.time()
    if not force_refresh and (now - _last_pg_check_time) < _PG_CHECK_TTL_SECONDS:
        return _last_pg_check_result

    try:
        engine = get_engine()
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        _last_pg_check_result = True
    except Exception as exc:
        logger.debug(f"PostgreSQL connection check failed: {exc}")
        _last_pg_check_result = False

    _last_pg_check_time = now
    return _last_pg_check_result


def init_postgres_schema_if_needed() -> bool:
    """
    Creates ai_rag schema and pgvector extension if PostgreSQL is reachable.
    """
    if not is_postgres_available():
        return False
    try:
        engine = get_engine()
        with engine.begin() as conn:
            conn.execute(text("CREATE SCHEMA IF NOT EXISTS ai_rag;"))
            try:
                conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector;"))
            except Exception as e:
                logger.warning(f"Could not create vector extension (may already exist or need superuser): {e}")
        from app.db.models import Base
        Base.metadata.create_all(bind=engine)
        return True
    except Exception as e:
        logger.warning(f"Could not auto-initialize ai_rag tables: {e}")
        return False


@contextmanager
def get_db_session() -> Generator[Optional[Session], None, None]:
    """
    Context manager yielding a SQLAlchemy session if Postgres is available, else None.
    """
    if not is_postgres_available():
        yield None
        return

    factory = get_session_factory()
    session = factory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
