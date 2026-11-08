import pytest
from fastapi.testclient import TestClient
from app.config import get_settings
from app.llm.airgap_guard import (
    AirgapSecurityViolationError,
    assert_airgap_compliance,
    is_local_or_private_host,
)
from app.llm.gateway import LLMGateway, get_llm_gateway
from app.main import app

client = TestClient(app)
VALID_TOKEN = get_settings().internal_service_token


def test_airgap_host_validation_local():
    assert is_local_or_private_host("localhost") is True
    assert is_local_or_private_host("localhost:11434") is True
    assert is_local_or_private_host("127.0.0.1") is True
    assert is_local_or_private_host("127.0.0.1:8000") is True
    assert is_local_or_private_host("::1") is True
    assert is_local_or_private_host("host.docker.internal") is True
    assert is_local_or_private_host("gpu-server.local") is True


def test_airgap_host_validation_rfc1918():
    assert is_local_or_private_host("10.0.0.1") is True
    assert is_local_or_private_host("10.254.1.20:8000") is True
    assert is_local_or_private_host("172.16.0.5") is True
    assert is_local_or_private_host("172.28.1.1") is True
    assert is_local_or_private_host("192.168.1.50") is True
    assert is_local_or_private_host("192.168.100.2:11434") is True


def test_airgap_host_validation_public_rejected():
    assert is_local_or_private_host("api.nano-gpt.com") is False
    assert is_local_or_private_host("api.openai.com") is False
    assert is_local_or_private_host("8.8.8.8") is False
    assert is_local_or_private_host("1.1.1.1") is False


def test_airgap_guard_blocks_when_active():
    # When airgapped_mode is True, public domain must raise AirgapSecurityViolationError
    with pytest.raises(AirgapSecurityViolationError):
        assert_airgap_compliance("https://api.nano-gpt.com/api/v1", airgapped_mode=True)

    with pytest.raises(AirgapSecurityViolationError):
        assert_airgap_compliance("https://api.openai.com/v1", airgapped_mode=True)

    # Allowed local targets when airgapped_mode is True
    assert_airgap_compliance("http://localhost:11434/v1", airgapped_mode=True)
    assert_airgap_compliance("http://127.0.0.1:8000/v1", airgapped_mode=True)
    assert_airgap_compliance("http://host.docker.internal:11434/v1", airgapped_mode=True)
    assert_airgap_compliance("http://192.168.1.150:8000/v1", airgapped_mode=True)


def test_airgap_guard_permits_when_disabled():
    # When airgapped_mode is False, public URLs are permitted
    assert_airgap_compliance("https://api.nano-gpt.com/api/v1", airgapped_mode=False)


def test_provider_status_endpoint():
    response = client.get(
        "/internal/v1/providers/status",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
    )
    assert response.status_code == 200
    data = response.json()
    assert "active_provider" in data
    assert "available_providers" in data
    assert "airgapped_mode" in data
    assert "embedding_dimension" in data
    assert data["embedding_dimension"] == 1024


def test_provider_config_endpoint_switch_to_ollama():
    payload = {
        "llm_provider": "ollama",
        "ollama_base_url": "http://localhost:11434/v1",
        "ollama_model": "qwen2.5:14b-instruct",
        "airgapped_mode": True,
    }
    response = client.put(
        "/internal/v1/providers/config",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["active_provider"] == "ollama"
    assert data["active_model"] == "qwen2.5:14b-instruct"
    assert data["airgapped_mode"] is True


def test_provider_config_airgap_rejection():
    # While airgapped_mode is active, switching to public nanogpt must be rejected
    payload = {
        "llm_provider": "nanogpt",
        "airgapped_mode": True,
    }
    response = client.put(
        "/internal/v1/providers/config",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json=payload,
    )
    assert response.status_code == 400
    err = response.json()
    assert "AIRGAPPED_MODE" in err["detail"]

    # Restore safe default config
    client.put(
        "/internal/v1/providers/config",
        headers={"X-Internal-Service-Token": VALID_TOKEN},
        json={"llm_provider": "nanogpt", "airgapped_mode": False},
    )
