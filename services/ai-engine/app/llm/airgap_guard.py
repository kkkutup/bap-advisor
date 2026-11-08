import ipaddress
import logging
from urllib.parse import urlparse

logger = logging.getLogger(__name__)


class AirgapSecurityViolationError(PermissionError):
    """Raised when an outbound connection to an unauthorized/public host is attempted in AIRGAPPED_MODE."""
    pass


def is_local_or_private_host(hostname: str) -> bool:
    """
    Checks if a hostname or IP address is loopback, Docker internal, .local, or RFC1918 private LAN.
    """
    if not hostname:
        return False

    host = hostname.strip().lower()

    # Handle IPv6 brackets [::1]:8000
    if host.startswith("[") and "]" in host:
        host = host[1:host.index("]")]
    elif ":" in host and host.count(":") == 1:  # IPv4 with port, e.g. localhost:8000
        host = host.split(":")[0]

    if host in ("localhost", "host.docker.internal", "127.0.0.1", "::1"):
        return True
    if host.endswith(".local") or host.endswith(".internal"):
        return True

    try:
        ip = ipaddress.ip_address(host)
        return ip.is_loopback or ip.is_private or ip.is_link_local
    except ValueError:
        return False


def assert_airgap_compliance(url: str, airgapped_mode: bool) -> None:
    """
    Enforces airgap safety.
    If airgapped_mode is True, raises AirgapSecurityViolationError for any non-local/non-private target.
    """
    if not airgapped_mode:
        return

    parsed = urlparse(url)
    hostname = parsed.hostname or url.split(":")[0].replace("http://", "").replace("https://", "").split("/")[0]

    if not is_local_or_private_host(hostname):
        msg = (
            f"AIRGAPPED_MODE Security Violation: Outbound HTTP connection to public internet host '{hostname}' "
            f"(target: {url}) is strictly forbidden when airgap lock is active. "
            f"Only localhost, 127.0.0.1, host.docker.internal, and RFC1918 private LAN endpoints are allowed."
        )
        logger.critical(msg)
        raise AirgapSecurityViolationError(msg)
