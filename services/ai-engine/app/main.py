from datetime import datetime, timezone
from fastapi import FastAPI, Header, HTTPException, Security, status
from fastapi.middleware.cors import CORSMiddleware
from app.config import get_settings
from app.evaluator.router import router as evaluator_router
from app.llm.router import router as llm_router
from app.ocr.router import router as ocr_router
from app.rag.router import router as rag_router
from app.schemas import HealthStatus

settings = get_settings()

app = FastAPI(
    title="Mersin University BAP Advisor - AI Engine",
    version="0.1.0",
    docs_url="/docs",
    openapi_url="/internal/v1/openapi.json"
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


async def verify_internal_token(
    x_internal_service_token: str = Header(..., alias="X-Internal-Service-Token")
):
    expected_token = get_settings().internal_service_token
    if x_internal_service_token != expected_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service token",
            headers={"WWW-Authenticate": "ApiKey"}
        )


# Health check endpoint (unauthenticated)
@app.get("/internal/v1/health", response_model=HealthStatus, tags=["Health"])
async def get_health() -> HealthStatus:
    cfg = get_settings()
    return HealthStatus(
        status="UP",
        version="0.1.0",
        airgapped_mode=cfg.airgapped_mode,
        llm_provider=cfg.llm_provider,
        embedding_model=cfg.embedding_model,
        timestamp=datetime.now(timezone.utc)
    )


# Protected internal API routers
app.include_router(ocr_router, prefix="/internal/v1", dependencies=[Security(verify_internal_token)])
app.include_router(rag_router, prefix="/internal/v1", dependencies=[Security(verify_internal_token)])
app.include_router(evaluator_router, prefix="/internal/v1", dependencies=[Security(verify_internal_token)])
app.include_router(llm_router, prefix="/internal/v1", dependencies=[Security(verify_internal_token)])
