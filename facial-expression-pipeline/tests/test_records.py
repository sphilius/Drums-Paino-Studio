"""Every record must be well-formed: valid quadrant, text, and all six FACS."""

from __future__ import annotations

import pytest

from exprsheet import registry
from exprsheet.schema import FACS, FACS_FIELDS, Emotion, Quadrant


def _all_records():
    for number, records in registry.BATCHES.items():
        for emotion in records:
            yield number, emotion


def test_every_record_has_all_six_facs_fields_populated() -> None:
    for number, emotion in _all_records():
        for field in FACS_FIELDS:
            value = getattr(emotion.facs, field)
            assert isinstance(value, str) and value.strip(), (
                f"batch {number} / {emotion.name}: FACS field {field!r} is empty"
            )


def test_every_record_has_valid_quadrant_and_text() -> None:
    for number, emotion in _all_records():
        assert isinstance(emotion.quadrant, Quadrant)
        assert emotion.name.strip()
        assert emotion.definition.strip()


def test_facs_has_exactly_six_fields() -> None:
    assert len(FACS_FIELDS) == 6
    assert set(FACS_FIELDS) == {
        "brow",
        "eyes",
        "nose",
        "mouth",
        "cheeks",
        "head_and_shoulders",
    }


def test_missing_facs_field_is_rejected_at_construction() -> None:
    with pytest.raises(ValueError):
        FACS("a", "b", "c", "d", "e", "   ")  # blank last field


def test_wrong_quadrant_type_is_rejected() -> None:
    with pytest.raises(TypeError):
        Emotion(
            name="Bad",
            quadrant="Yellow",  # type: ignore[arg-type]
            definition="x",
            facs=FACS("a", "b", "c", "d", "e", "f"),
        )
