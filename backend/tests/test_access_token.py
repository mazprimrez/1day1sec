"""The access-token check: Google is asked who a token belongs to and which app got it."""

from types import SimpleNamespace

import pytest
import requests
from google.auth import exceptions as google_exceptions

from app.auth import TOKENINFO_URL, check_access_token, looks_like_id_token

OURS = "our-android-client.apps.googleusercontent.com"
GOOD = {"azp": OURS, "aud": OURS, "email": "owner@example.com", "email_verified": "true", "expires_in": "3083"}


def google_says(status: int, body: dict | None = None):
    calls = []

    def post(url, data, timeout):
        calls.append(SimpleNamespace(url=url, data=data, timeout=timeout))
        return SimpleNamespace(status_code=status, json=lambda: body or {})

    post.calls = calls
    return post


def test_a_token_given_to_this_app_is_accepted_with_what_google_says_about_it():
    post = google_says(200, GOOD)

    assert check_access_token("ya29.token", (OURS,), post) == GOOD
    assert post.calls[0].url == TOKENINFO_URL
    # In the body, not the address, so it cannot end up in a log of requested URLs.
    assert post.calls[0].data == {"access_token": "ya29.token"}


def test_a_token_given_to_another_app_is_refused():
    post = google_says(200, {**GOOD, "azp": "someone-elses-app.apps.googleusercontent.com"})

    with pytest.raises(ValueError, match="another app"):
        check_access_token("ya29.token", (OURS,), post)


def test_a_token_that_does_not_say_which_app_is_refused():
    post = google_says(200, {key: value for key, value in GOOD.items() if key != "azp"})

    with pytest.raises(ValueError):
        check_access_token("ya29.token", (OURS,), post)


@pytest.mark.parametrize("expires_in", ["0", "-5", None, "soon"])
def test_an_expired_token_is_refused(expires_in):
    with pytest.raises(ValueError, match="expired"):
        check_access_token("ya29.token", (OURS,), google_says(200, {**GOOD, "expires_in": expires_in}))


def test_a_token_google_does_not_know_is_refused():
    with pytest.raises(ValueError, match="does not know"):
        check_access_token("made-up", (OURS,), google_says(400, {"error": "invalid_token"}))


def test_google_being_down_is_not_mistaken_for_a_bad_token():
    with pytest.raises(google_exceptions.TransportError):
        check_access_token("ya29.token", (OURS,), google_says(503))


def test_no_connection_to_google_is_not_mistaken_for_a_bad_token():
    def post(url, data, timeout):
        raise requests.ConnectionError("no route")

    with pytest.raises(google_exceptions.TransportError):
        check_access_token("ya29.token", (OURS,), post)


@pytest.mark.parametrize(
    "token,expected",
    [
        ("eyJhbGciOi.eyJhdWQiOi.c2lnbmF0dXJl", True),
        ("ya29.a0AfH6SMBx-example", False),
        ("ya29.a0.with.dots", False),
        ("made-up", False),
    ],
)
def test_id_tokens_and_access_tokens_are_told_apart(token, expected):
    assert looks_like_id_token(token) is expected
