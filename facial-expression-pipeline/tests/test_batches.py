"""Batch-size and cross-batch dedupe invariants."""

from __future__ import annotations

import pytest

from exprsheet import registry
from exprsheet.registry import BATCH_SIZE, DuplicateEmotionError, check_duplicates
from exprsheet.schema import FACS, Emotion, Quadrant


def _emotion(name: str) -> Emotion:
    return Emotion(
        name=name,
        quadrant=Quadrant.YELLOW,
        definition="test",
        facs=FACS("a", "b", "c", "d", "e", "f"),
    )


def test_at_least_one_batch_registered() -> None:
    assert registry.BATCHES, "expected the seed data module to register a batch"


@pytest.mark.parametrize("number", sorted(registry.BATCHES))
def test_each_batch_has_24_records(number: int) -> None:
    assert len(registry.BATCHES[number]) == BATCH_SIZE


def test_no_cross_batch_name_collisions() -> None:
    # Must not raise.
    check_duplicates()
    keys = [e.key for records in registry.BATCHES.values() for e in records]
    assert len(keys) == len(set(keys)), "found duplicate emotion keys"


def test_dedupe_guard_detects_cross_batch_collision() -> None:
    collision = {
        1: [_emotion("Joyful"), _emotion("Calm")],
        2: [_emotion("Sad"), _emotion("joyful")],  # case-insensitive collision
    }
    with pytest.raises(DuplicateEmotionError) as excinfo:
        check_duplicates(collision)
    assert excinfo.value.first_batch == 1
    assert excinfo.value.second_batch == 2


def test_dedupe_guard_detects_within_batch_collision() -> None:
    collision = {1: [_emotion("Joyful"), _emotion("Joyful")]}
    with pytest.raises(DuplicateEmotionError):
        check_duplicates(collision)
