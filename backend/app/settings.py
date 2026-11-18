"""Everything the service reads from its environment."""

import os
from collections.abc import Mapping
from dataclasses import dataclass

# The model behind /transcribe, /extract-text, /recap and /book-recap. Set GEMINI_MODEL to
# change it for all of them; this default is the only place the name is written in code.
DEFAULT_MODEL = "gemini-3.5-flash-lite"


@dataclass(frozen=True)
class Settings:
    model: str
    allowed_email: str
    client_ids: tuple[str, ...]
    project: str | None
    location: str


def load(env: Mapping[str, str] = os.environ) -> Settings:
    """Raises when the two values that keep strangers out are missing: no open default."""
    allowed_email = env.get("ALLOWED_EMAIL", "").strip().lower()
    client_ids = tuple(part.strip() for part in env.get("GOOGLE_CLIENT_ID", "").split(",") if part.strip())
    if not allowed_email:
        raise RuntimeError("Set ALLOWED_EMAIL to the one Google account allowed to use this service.")
    if not client_ids:
        raise RuntimeError("Set GOOGLE_CLIENT_ID to the OAuth client ID the app asks its ID token for.")
    return Settings(
        model=env.get("GEMINI_MODEL", "").strip() or DEFAULT_MODEL,
        allowed_email=allowed_email,
        client_ids=client_ids,
        project=env.get("GOOGLE_CLOUD_PROJECT", "").strip() or None,
        location=env.get("GOOGLE_CLOUD_LOCATION", "").strip() or "global",
    )
