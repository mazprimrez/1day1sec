import json

import pytest

from app import recap

from conftest import sample

# What a well-behaved model writes for tests/samples/week.json.
WEEK_ANSWER = {
    "title": "Dari deadline ke pagi yang tenang",
    "whatHappened": [
        {"date": "2026-11-09", "text": "Meeting seharian; deadline dimajukan ke Rabu."},
        {"date": "2026-11-11", "text": "Deck ke-submit jam 9 malam."},
        {"date": "2026-11-13", "text": "Makan malam sama Ibu di Kemang."},
        {"date": "2026-11-14", "text": "Lari pagi 5K di GBK, lalu ngopi."},
    ],
    "note": "Minggu ini berat di awal, and you still made room for the people and mornings that steady you.",
    "quote": "It was the first quiet morning in weeks.",
    "quoteDate": "2026-11-14",
    "changes": [
        {"direction": "up", "text": "You wrote on 4 days, up from 3."},
        {"direction": "up", "text": "Average mood 3.5, up from 3.0."},
    ],
    "tries": ["Coba satu pagi tanpa HP sebelum kerja.", "Maybe one more dinner with Ibu."],
}


@pytest.mark.parametrize("period", ["week", "month", "year"])
def test_the_entries_reach_the_model_as_they_were_sent(client, fake, owner, period):
    fake.answer = {"title": "A title", "note": "A note."}
    request = sample(period)

    response = client.post("/v1/recap", json=request, headers=owner)

    assert response.status_code == 200
    assert json.loads(fake.calls[0].parts[0].text) == request
    # Indonesian and English are sent as written, not as escape codes.
    assert request["days"][0]["entries"][0] in fake.calls[0].parts[0].text


def test_the_prompt_comes_from_its_own_file(client, fake, owner):
    fake.answer = WEEK_ANSWER

    client.post("/v1/recap", json=sample("week"), headers=owner)

    assert recap.PROMPT_PATH.name == "recap.md"
    assert fake.calls[0].config.system_instruction == recap.PROMPT_PATH.read_text(encoding="utf-8")


def test_a_recap_comes_back_in_the_shape_the_app_reads(client, fake, owner):
    fake.answer = WEEK_ANSWER

    response = client.post("/v1/recap", json=sample("week"), headers=owner)

    assert response.status_code == 200
    assert response.json() == WEEK_ANSWER


def test_the_recap_also_answers_without_the_v1_prefix(client, fake, owner):
    fake.answer = WEEK_ANSWER

    assert client.post("/recap", json=sample("week"), headers=owner).json() == WEEK_ANSWER


def test_a_quote_that_is_not_in_the_entries_is_dropped(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": "It was the first peaceful morning in weeks."}

    body = client.post("/v1/recap", json=sample("week"), headers=owner).json()

    assert (body["quote"], body["quoteDate"]) == ("", "")
    assert body["title"] == WEEK_ANSWER["title"]


def test_a_translated_quote_is_dropped(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": "Today was really tiring.", "quoteDate": "2026-11-09"}

    body = client.post("/v1/recap", json=sample("week"), headers=owner).json()

    assert (body["quote"], body["quoteDate"]) == ("", "")


def test_a_quote_keeps_its_place_despite_quote_marks_case_and_spacing(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": '“it was the first  quiet morning in weeks.”'}

    body = client.post("/v1/recap", json=sample("week"), headers=owner).json()

    assert body["quote"] == "it was the first quiet morning in weeks."
    assert body["quoteDate"] == "2026-11-14"


def test_a_quote_given_the_wrong_day_gets_the_day_it_is_from(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": "Hari ini capek banget.", "quoteDate": "2026-11-14"}

    body = client.post("/v1/recap", json=sample("week"), headers=owner).json()

    assert (body["quote"], body["quoteDate"]) == ("Hari ini capek banget.", "2026-11-09")


def test_suggestions_are_cut_to_two(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "tries": ["One.", "", "Two.", "Three."]}

    assert client.post("/v1/recap", json=sample("week"), headers=owner).json()["tries"] == ["One.", "Two."]


def test_a_year_without_a_previous_period_can_have_no_changes(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": "I forgot to be scared.", "quoteDate": "2026-03-14", "changes": []}

    body = client.post("/v1/recap", json=sample("year"), headers=owner).json()

    assert body["changes"] == []
    assert (body["quote"], body["quoteDate"]) == ("I forgot to be scared.", "2026-03-14")


@pytest.mark.parametrize("missing", ["title", "note"])
def test_a_recap_without_a_title_or_note_is_an_error_the_app_retries(client, fake, owner, missing):
    fake.answer = {**WEEK_ANSWER, missing: "  "}

    response = client.post("/v1/recap", json=sample("week"), headers=owner)

    assert response.status_code == 502
    assert response.json()["error"]


@pytest.mark.parametrize(
    "body",
    [
        b"not json",
        b"[]",
        json.dumps({**sample("week"), "period": "decade"}).encode(),
        json.dumps({**sample("week"), "days": []}).encode(),
        json.dumps({**sample("week"), "days": "all of them"}).encode(),
    ],
)
def test_a_request_that_is_not_a_period_with_days_is_refused(client, fake, owner, body):
    response = client.post("/v1/recap", content=body, headers={**owner, "Content-Type": "application/json"})

    assert response.status_code == 400
    assert fake.calls == []
