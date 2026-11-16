import pytest
from google.genai import errors as genai_errors
from google.genai import types

from app import transcription
from app.main import MAX_MEDIA_BYTES

AUDIO = {"Content-Type": "audio/mp4"}


def test_a_voice_note_comes_back_as_text(client, fake, owner):
    fake.answer = {"text": "  Hari ini capek banget, but I'm okay.  "}

    response = client.post("/v1/transcribe", content=b"\x00voice", headers={**owner, **AUDIO})

    assert response.status_code == 200
    assert response.json() == {"text": "Hari ini capek banget, but I'm okay."}


def test_the_audio_is_sent_to_the_model_as_m4a(client, fake, owner):
    client.post("/v1/transcribe", content=b"\x00voice", headers={**owner, **AUDIO})

    audio = fake.calls[0].parts[0].inline_data
    assert audio.data == b"\x00voice"
    assert audio.mime_type == "audio/m4a"


def test_other_audio_types_keep_their_name(client, fake, owner):
    client.post("/v1/transcribe", content=b"voice", headers={**owner, "Content-Type": "audio/wav"})

    assert fake.calls[0].parts[0].inline_data.mime_type == "audio/wav"


def test_the_model_is_told_to_keep_the_mixed_languages_verbatim(client, fake, owner):
    client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO})

    instruction = fake.calls[0].config.system_instruction
    assert "Indonesian mixed with English" in instruction
    assert "Never translate" in instruction
    assert "Verbatim" in instruction


def test_silence_is_an_empty_transcript_not_an_error(client, fake, owner):
    fake.answer = {"text": ""}

    response = client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO})

    assert response.status_code == 200
    assert response.json() == {"text": ""}


def test_the_provider_is_one_function(client, fake, owner, monkeypatch):
    async def another_provider(audio: bytes, mime_type: str) -> str:
        return f"{len(audio)} bytes of {mime_type} from somewhere else"

    monkeypatch.setattr(transcription, "transcribe", another_provider)

    response = client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO})

    assert response.json() == {"text": "5 bytes of audio/mp4 from somewhere else"}
    assert fake.calls == []


def test_something_that_is_not_audio_is_refused(client, fake, owner):
    response = client.post("/v1/transcribe", content=b"{}", headers={**owner, "Content-Type": "application/json"})

    assert response.status_code == 415
    assert fake.calls == []


def test_an_empty_request_is_refused(client, fake, owner):
    assert client.post("/v1/transcribe", content=b"", headers={**owner, **AUDIO}).status_code == 400
    assert fake.calls == []


def test_a_recording_over_the_limit_is_refused(client, fake, owner):
    response = client.post("/v1/transcribe", content=b"0" * (MAX_MEDIA_BYTES + 1), headers={**owner, **AUDIO})

    assert response.status_code == 413
    assert fake.calls == []


def api_error(code: int) -> genai_errors.APIError:
    return genai_errors.APIError(code, {"error": {"code": code, "message": "from Google", "status": "X"}})


@pytest.mark.parametrize(
    "upstream,expected",
    [
        (api_error(429), 429),  # busy: the app retries by itself
        (api_error(500), 503),
        (api_error(503), 503),
        (api_error(404), 500),  # wrong model name: retried once the setting is fixed
        (api_error(403), 500),  # service account lacks access
        (api_error(400), 422),  # this recording cannot be read: the user gets "Try again"
        (TimeoutError("slow"), 503),
    ],
)
def test_model_failures_become_statuses_the_app_understands(client, fake, owner, upstream, expected):
    fake.error = upstream

    response = client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO})

    assert response.status_code == expected
    assert response.json()["error"]
    assert "from Google" not in response.json()["error"]


def test_a_refusal_from_the_model_is_not_retried_forever(client, fake, owner):
    fake.answer = None

    assert client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO}).status_code == 422


@pytest.mark.parametrize(
    "answer,finish_reason,expected",
    [
        ("not json", types.FinishReason.STOP, 502),
        ('["a list"]', types.FinishReason.STOP, 502),
        ('{"text": "cut of', types.FinishReason.MAX_TOKENS, 502),
        ('{"text": ""}', types.FinishReason.SAFETY, 422),
    ],
)
def test_an_answer_that_cannot_be_used_is_an_error(client, fake, owner, answer, finish_reason, expected):
    fake.answer, fake.finish_reason = answer, finish_reason

    assert client.post("/v1/transcribe", content=b"voice", headers={**owner, **AUDIO}).status_code == expected
