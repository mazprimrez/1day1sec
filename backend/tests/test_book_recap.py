import json

import pytest

from app import book_recap

from conftest import sample

# What a well-behaved model writes for tests/samples/book.json.
BOOK_ANSWER = {
    "title": "Dari ragu ke nggak mau selesai",
    "quote": "I think this book is less about leaving than about who stays.",
    "quotePage": 214,
    "summary": "Di awal you weren't sure about the narrator. Around the kitchen pages it turned into kangen rumah, and by page 214 you were reading it as a book about who stays.",
    "points": [
        {"title": "Narator yang nyebelin", "line": "You went from not liking the narrator to understanding why.", "pages": [12, 214], "fromNotes": True},
        {"title": "Dapur dan rumah Eyang", "line": "The kitchen kept pulling you back to your own family.", "pages": [58], "fromNotes": True},
        {"title": "Yang tinggal, bukan yang pergi", "line": "Your reading of what the book is about changed on page 214.", "pages": [214], "fromNotes": True},
        {"title": "Baca pelan-pelan", "line": "You slowed down because you didn't want it to end.", "pages": [215], "fromNotes": True},
        {"title": "Dibanding Pulang ke Timur", "line": "You could set this beside the other book about going home you finished this year.", "pages": [], "fromNotes": False},
    ],
    "gap": "Your notes don't say yet who you'd recommend it to.",
}


def post(client, owner, request=None):
    return client.post("/v1/book-recap", json=request or sample("book"), headers=owner)


def test_the_thoughts_reach_the_model_as_they_were_sent(client, fake, owner):
    fake.answer = BOOK_ANSWER
    request = sample("book")

    response = post(client, owner, request)

    assert response.status_code == 200
    assert json.loads(fake.calls[0].parts[0].text) == request
    # Indonesian and English are sent as written, not as escape codes.
    assert request["thoughts"][0]["text"] in fake.calls[0].parts[0].text


def test_it_uses_the_model_named_by_the_one_setting(client, fake, owner):
    fake.answer = BOOK_ANSWER

    post(client, owner)

    assert fake.calls[0].model == "test-model"


def test_the_prompt_comes_from_its_own_file(client, fake, owner):
    fake.answer = BOOK_ANSWER

    post(client, owner)

    assert book_recap.PROMPT_PATH.name == "book_recap.md"
    assert fake.calls[0].config.system_instruction == book_recap.PROMPT_PATH.read_text(encoding="utf-8")


def test_the_prompt_forbids_quoting_the_book_and_writing_the_review():
    prompt = book_recap.PROMPT_PATH.read_text(encoding="utf-8")

    assert "Never quote the book." in prompt
    assert "Never write the review." in prompt


def test_a_summary_comes_back_in_the_shape_the_app_reads(client, fake, owner):
    fake.answer = BOOK_ANSWER

    response = post(client, owner)

    assert response.status_code == 200
    assert response.json() == BOOK_ANSWER


def test_it_also_answers_without_the_v1_prefix(client, fake, owner):
    fake.answer = BOOK_ANSWER

    assert client.post("/book-recap", json=sample("book"), headers=owner).json() == BOOK_ANSWER


def test_only_the_owner_gets_a_summary(client, fake):
    assert client.post("/v1/book-recap", json=sample("book")).status_code == 401
    stranger = {"Authorization": "Bearer stranger-token"}
    assert client.post("/v1/book-recap", json=sample("book"), headers=stranger).status_code == 403
    assert fake.calls == []


# At least three thoughts


@pytest.mark.parametrize("kept", [0, 1, 2])
def test_fewer_than_three_thoughts_is_refused_before_the_model_is_asked(client, fake, owner, kept):
    request = sample("book")
    request["thoughts"] = request["thoughts"][:kept]

    response = post(client, owner, request)

    assert response.status_code == 400
    assert f"This book has {kept}" in response.json()["error"]
    assert fake.calls == []


def test_thoughts_without_words_or_a_page_do_not_count(client, fake, owner):
    request = sample("book")
    request["thoughts"] = request["thoughts"][:2] + [
        {"page": 90, "date": "2026-11-06", "text": "   "},
        {"page": 91, "date": "2026-11-06"},
        {"date": "2026-11-06", "text": "No page on this one."},
    ]

    response = post(client, owner, request)

    assert response.status_code == 400
    assert "This book has 2" in response.json()["error"]
    assert fake.calls == []


def test_exactly_three_thoughts_is_enough(client, fake, owner):
    fake.answer = BOOK_ANSWER
    request = sample("book")
    request["thoughts"] = request["thoughts"][:3]

    assert post(client, owner, request).status_code == 200


# The quote is the reader's own words


def test_a_quote_that_is_not_in_the_thoughts_is_dropped(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "This book is more about staying than leaving."}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("", None)
    assert body["title"] == BOOK_ANSWER["title"]


def test_a_translated_quote_is_dropped(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "Just read it slowly.", "quotePage": 215}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("", None)


def test_a_quote_comes_back_as_the_reader_wrote_it(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "“i don't want  this one to END.”", "quotePage": 215}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("I don't want this one to end.", 215)


def test_a_quote_given_the_wrong_page_gets_the_page_it_is_from(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "Pelan-pelan aja bacanya.", "quotePage": 12}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("Pelan-pelan aja bacanya.", 215)


def test_no_quote_is_a_valid_answer(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "", "quotePage": 0}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("", None)


# Never the book itself


def test_a_line_the_reader_copied_from_the_book_is_never_the_quote(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "rumah itu tidak pernah benar-benar kosong", "quotePage": 142}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("", None)


def test_a_quote_that_runs_into_a_copied_line_is_dropped_too(client, fake, owner):
    fake.answer = {
        **BOOK_ANSWER,
        "quote": "Dia nulis “rumah itu tidak pernah benar-benar kosong” and I had to stop reading for a bit.",
        "quotePage": 142,
    }

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("", None)


def test_the_readers_own_sentence_beside_a_copied_line_is_kept(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "quote": "Ternyata gue bacanya sambil mikirin Ibu terus.", "quotePage": 142}

    body = post(client, owner).json()

    assert (body["quote"], body["quotePage"]) == ("Ternyata gue bacanya sambil mikirin Ibu terus.", 142)


def test_a_word_or_two_in_quotation_marks_is_the_readers_own():
    thoughts = [{"page": 30, "text": "Everyone keeps saying “pulang” like it means one thing."}]

    quote, page = book_recap.verified_quote("Everyone keeps saying “pulang” like it means one thing.", 30, thoughts)

    assert (quote, page) == ("Everyone keeps saying “pulang” like it means one thing.", 30)


def test_a_line_copied_in_straight_quotation_marks_counts_as_the_books():
    thoughts = [{"page": 30, "text": 'She writes "the house was never really empty" and I felt that.'}]

    assert book_recap.verified_quote("the house was never really empty", 30, thoughts) == ("", None)


# Never the review


def test_points_are_cut_to_five(client, fake, owner):
    extra = {"title": "A sixth", "line": "One too many.", "pages": [12], "fromNotes": True}
    fake.answer = {**BOOK_ANSWER, "points": BOOK_ANSWER["points"] + [extra]}

    points = post(client, owner).json()["points"]

    assert [point["title"] for point in points] == [point["title"] for point in BOOK_ANSWER["points"]]


def test_a_point_is_one_line_however_much_the_model_writes(client, fake, owner):
    paragraph = "This is where the review would start if the model were allowed to write it. " * 10
    fake.answer = {**BOOK_ANSWER, "points": [{**BOOK_ANSWER["points"][0], "line": paragraph + "\n\nAnd more."}]}

    line = post(client, owner).json()["points"][0]["line"]

    assert len(line) <= book_recap.MAX_LINE_CHARS
    assert line.endswith("…")
    assert "\n" not in line


def test_the_summary_stays_one_short_paragraph(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "summary": "First paragraph.\n\n" + "A long second paragraph. " * 80}

    summary = post(client, owner).json()["summary"]

    assert len(summary) <= book_recap.MAX_SUMMARY_CHARS
    assert "\n" not in summary


# Points say where they come from


def test_pages_that_have_no_thought_are_removed_from_a_point(client, fake, owner):
    point = {**BOOK_ANSWER["points"][0], "pages": [214, 300, 12, 214]}
    fake.answer = {**BOOK_ANSWER, "points": [point]}

    assert post(client, owner).json()["points"][0]["pages"] == [12, 214]


def test_a_point_left_without_pages_is_not_from_the_notes(client, fake, owner):
    point = {**BOOK_ANSWER["points"][0], "pages": [300, 301], "fromNotes": True}
    fake.answer = {**BOOK_ANSWER, "points": [point]}

    assert post(client, owner).json()["points"][0] == {**point, "pages": [], "fromNotes": False}


def test_a_suggestion_from_outside_the_notes_cites_no_pages(client, fake, owner):
    point = {**BOOK_ANSWER["points"][4], "pages": [12, 58]}
    fake.answer = {**BOOK_ANSWER, "points": [point]}

    assert post(client, owner).json()["points"][0] == {**point, "pages": [], "fromNotes": False}


def test_points_without_a_title_are_left_out(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "points": [{**BOOK_ANSWER["points"][0], "title": " "}, BOOK_ANSWER["points"][1]]}

    assert post(client, owner).json()["points"] == [BOOK_ANSWER["points"][1]]


# Answers the app cannot use, and requests that are not a book


@pytest.mark.parametrize("missing", ["title", "summary"])
def test_a_summary_without_a_title_or_summary_is_an_error_the_app_retries(client, fake, owner, missing):
    fake.answer = {**BOOK_ANSWER, missing: "  "}

    response = post(client, owner)

    assert response.status_code == 502
    assert response.json()["error"]


def test_a_summary_without_points_is_an_error_the_app_retries(client, fake, owner):
    fake.answer = {**BOOK_ANSWER, "points": []}

    assert post(client, owner).status_code == 502


@pytest.mark.parametrize(
    "body",
    [
        b"not json",
        b"[]",
        json.dumps({**sample("book"), "book": {"author": "No title"}}).encode(),
        json.dumps({**sample("book"), "book": "Rumah di Ujung Musim"}).encode(),
        json.dumps({**sample("book"), "thoughts": "all of them"}).encode(),
        json.dumps({**sample("book"), "thoughts": ["a thought", "another", "a third"]}).encode(),
    ],
)
def test_a_request_that_is_not_a_book_with_thoughts_is_refused(client, fake, owner, body):
    response = client.post("/v1/book-recap", content=body, headers={**owner, "Content-Type": "application/json"})

    assert response.status_code == 400
    assert fake.calls == []
