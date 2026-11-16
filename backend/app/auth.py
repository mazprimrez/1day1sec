"""Every request must carry proof from Google that it comes from the one allowed account.

Two kinds of proof are accepted in `Authorization: Bearer …`:

- a Google **ID token**, checked here against Google's public keys;
- a Google **access token**, which is what the Android app can get without showing a
  screen (so the nightly recap job can get one too). Google is asked who it belongs to
  and which app it was given to.

Either way the token must have been issued to this app (`GOOGLE_CLIENT_ID`) and belong
to `ALLOWED_EMAIL`, with the address verified.
"""

import logging
from collections.abc import Callable, Mapping
from typing import Any

import requests
from fastapi import Header
from fastapi.concurrency import run_in_threadpool
from google.auth import exceptions as google_exceptions
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token

from .errors import ApiError
from .settings import Settings

log = logging.getLogger(__name__)

TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo"
TOKENINFO_TIMEOUT_SECONDS = 10

# Takes the raw token, returns its claims, raises if Google does not vouch for it.
TokenVerifier = Callable[[str], Mapping[str, Any]]


def google_verifier(settings: Settings) -> TokenVerifier:
    transport = google_requests.Request()
    session = requests.Session()

    def verify(token: str) -> Mapping[str, Any]:
        if looks_like_id_token(token):
            # Checks the signature, expiry, issuer and audience.
            return id_token.verify_oauth2_token(token, transport, audience=list(settings.client_ids))
        return check_access_token(token, settings.client_ids, session.post)

    return verify


def looks_like_id_token(token: str) -> bool:
    """ID tokens are signed JSON (three parts starting with a JSON header); access tokens are not."""
    return token.count(".") == 2 and token.startswith("eyJ")


def check_access_token(token: str, client_ids: tuple[str, ...], post: Callable[..., Any]) -> Mapping[str, Any]:
    """What Google says about an access token, if it was given to this app and is still good.

    Raises ValueError when Google does not know the token, it has expired, or it was
    given to another app: a token from some other app the user signed in to must not
    open this service.
    """
    try:
        response = post(TOKENINFO_URL, data={"access_token": token}, timeout=TOKENINFO_TIMEOUT_SECONDS)
    except requests.RequestException as e:
        raise google_exceptions.TransportError(e) from e
    if response.status_code >= 500:
        raise google_exceptions.TransportError(f"Google answered {response.status_code}")
    if response.status_code != 200:
        raise ValueError("Google does not know this token")
    info = response.json()
    if info.get("azp") not in client_ids:
        raise ValueError("This token was given to another app")
    if _as_int(info.get("expires_in")) <= 0:
        raise ValueError("This token has expired")
    return info


def _as_int(value: object) -> int:
    try:
        return int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return 0


def require_owner(settings: Settings, verify: TokenVerifier):
    """A FastAPI dependency that lets a request through only for the allowed account."""

    async def dependency(authorization: str | None = Header(default=None)) -> None:
        scheme, _, token = (authorization or "").partition(" ")
        token = token.strip()
        if scheme.lower() != "bearer" or not token:
            raise ApiError(401, "Sign in with Google to use this.")
        try:
            claims = await run_in_threadpool(verify, token)
        except google_exceptions.TransportError:
            log.exception("Could not reach Google to check a token")
            raise ApiError(503, "Could not check your Google sign-in. Try again later.")
        except (ValueError, google_exceptions.GoogleAuthError):
            raise ApiError(401, "Your Google sign-in is no longer valid. Sign in again.")
        email = str(claims.get("email", "")).strip().lower()
        verified = claims.get("email_verified") in (True, "true")
        if not verified or email != settings.allowed_email:
            raise ApiError(403, "This Google account is not allowed to use this service.")

    return dependency
