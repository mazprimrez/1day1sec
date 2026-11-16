"""Only the one allowed Google account gets through, on every endpoint."""

import pytest

from conftest import sample

ENDPOINTS = [
    ("/v1/transcribe", {"content": b"audio", "headers": {"Content-Type": "audio/mp4"}}),
    ("/v1/extract-text", {"content": b"image", "headers": {"Content-Type": "image/png"}}),
    ("/v1/recap", {"json": sample("week")}),
    ("/transcribe", {"content": b"audio", "headers": {"Content-Type": "audio/mp4"}}),
]


def post(client, path, kwargs, authorization=None):
    headers = dict(kwargs.get("headers", {}))
    if authorization:
        headers["Authorization"] = authorization
    return client.post(path, **{**kwargs, "headers": headers})


@pytest.mark.parametrize("path,kwargs", ENDPOINTS)
def test_a_request_without_a_token_is_refused(client, fake, path, kwargs):
    response = post(client, path, kwargs)

    assert response.status_code == 401
    assert "error" in response.json()
    assert fake.calls == []


@pytest.mark.parametrize("authorization", ["Bearer made-up", "Bearer ", "Basic owner-token", "owner-token"])
def test_a_token_google_did_not_sign_is_refused(client, fake, authorization):
    response = post(client, *ENDPOINTS[0], authorization)

    assert response.status_code == 401
    assert fake.calls == []


@pytest.mark.parametrize("path,kwargs", ENDPOINTS)
def test_another_google_account_is_refused(client, fake, path, kwargs):
    response = post(client, path, kwargs, "Bearer stranger-token")

    assert response.status_code == 403
    assert fake.calls == []


def test_the_owner_with_an_unverified_address_is_refused(client, fake):
    assert post(client, *ENDPOINTS[0], "Bearer unverified-token").status_code == 403
    assert fake.calls == []


@pytest.mark.parametrize("token", ["owner-token", "owner-shouting-token"])
@pytest.mark.parametrize("path,kwargs", ENDPOINTS[:2] + ENDPOINTS[3:])
def test_the_owner_gets_through(client, fake, token, path, kwargs):
    assert post(client, path, kwargs, f"Bearer {token}").status_code == 200
    assert len(fake.calls) == 1
