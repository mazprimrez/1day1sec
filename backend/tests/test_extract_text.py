import pytest


@pytest.mark.parametrize("mime_type", ["image/jpeg", "image/png"])
def test_a_page_comes_back_as_text_with_its_line_breaks(client, fake, owner, mime_type):
    fake.answer = {"text": "Senin\nMeeting jam 10\n\nDon't forget: call Ibu\n"}

    response = client.post("/v1/extract-text", content=b"\x89page", headers={**owner, "Content-Type": mime_type})

    assert response.status_code == 200
    assert response.json() == {"text": "Senin\nMeeting jam 10\n\nDon't forget: call Ibu"}
    image = fake.calls[0].parts[0].inline_data
    assert (image.data, image.mime_type) == (b"\x89page", mime_type)


def test_a_blank_page_is_empty_text(client, fake, owner):
    fake.answer = {"text": ""}

    response = client.post("/v1/extract-text", content=b"page", headers={**owner, "Content-Type": "image/png"})

    assert response.json() == {"text": ""}


def test_the_model_is_told_to_type_what_is_written(client, fake, owner):
    client.post("/v1/extract-text", content=b"page", headers={**owner, "Content-Type": "image/png"})

    instruction = fake.calls[0].config.system_instruction
    assert "exactly as written" in instruction
    assert "Never translate" in instruction


@pytest.mark.parametrize("mime_type", ["image/gif", "application/pdf", "audio/mp4"])
def test_other_file_types_are_refused(client, fake, owner, mime_type):
    response = client.post("/v1/extract-text", content=b"page", headers={**owner, "Content-Type": mime_type})

    assert response.status_code == 415
    assert fake.calls == []
