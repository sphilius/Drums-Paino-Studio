"""Batch registry and the hard cross-batch dedupe check.

The registry is the single source of truth for *which* emotions belong to
*which* batch.  :mod:`exprsheet.data` populates it at import time by calling
:func:`register_batch`.

The dedupe guarantee is deliberately strict: no emotion name (normalized to a
case-insensitive, trimmed key) may appear in more than one batch, and no name
may appear twice within a batch.  :func:`check_duplicates` raises
:class:`DuplicateEmotionError` the instant a collision is found so a build can
never silently emit the same face twice.
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence

from .schema import Emotion

#: Every batch must contain exactly this many records.
BATCH_SIZE = 24


class DuplicateEmotionError(Exception):
    """Raised when the same emotion name appears in more than one place."""

    def __init__(self, name: str, first_batch: int, second_batch: int) -> None:
        self.name = name
        self.first_batch = first_batch
        self.second_batch = second_batch
        if first_batch == second_batch:
            message = (
                f"Duplicate emotion {name!r} appears twice within batch {first_batch}"
            )
        else:
            message = (
                f"Duplicate emotion {name!r} appears in batch {first_batch} "
                f"and again in batch {second_batch}"
            )
        super().__init__(message)


class UnknownBatchError(KeyError):
    """Raised when a requested batch number is not registered."""


# Global source-of-truth mapping: batch number -> ordered list of emotions.
BATCHES: dict[int, list[Emotion]] = {}


def register_batch(number: int, emotions: Sequence[Emotion]) -> list[Emotion]:
    """Register ``emotions`` as batch ``number``.

    Raises ``ValueError`` if the batch number is invalid or already registered.
    Note: this does *not* enforce ``BATCH_SIZE`` (a batch may be built up
    incrementally while authoring); the size invariant is checked by the test
    suite and surfaced as a warning by the CLI.
    """
    if not isinstance(number, int) or number < 1:
        raise ValueError(f"batch number must be a positive integer, got {number!r}")
    if number in BATCHES:
        raise ValueError(f"batch {number} is already registered")
    records = list(emotions)
    for emotion in records:
        if not isinstance(emotion, Emotion):
            raise TypeError(f"batch {number} contains a non-Emotion: {emotion!r}")
    BATCHES[number] = records
    return records


def get_batch(number: int) -> list[Emotion]:
    """Return the records for batch ``number`` or raise :class:`UnknownBatchError`."""
    try:
        return BATCHES[number]
    except KeyError:
        known = ", ".join(str(n) for n in sorted(BATCHES)) or "(none)"
        raise UnknownBatchError(
            f"batch {number} is not registered; known batches: {known}"
        ) from None


def check_duplicates(
    batches: Mapping[int, Sequence[Emotion]] | None = None,
) -> None:
    """Raise :class:`DuplicateEmotionError` if any name collides across batches.

    Pure over its input: pass an explicit mapping to check a hypothetical set,
    or omit it to check the global :data:`BATCHES` registry.
    """
    source = BATCHES if batches is None else batches
    seen: dict[str, int] = {}
    for number in sorted(source):
        for emotion in source[number]:
            key = emotion.key
            if key in seen:
                raise DuplicateEmotionError(emotion.name, seen[key], number)
            seen[key] = number


def batch_offset(number: int, batches: Mapping[int, Sequence[Emotion]] | None = None) -> int:
    """Count all emotions in batches numbered *before* ``number``.

    Used to compute a stable global generation index across every batch.
    """
    source = BATCHES if batches is None else batches
    return sum(len(source[n]) for n in sorted(source) if n < number)


def total_emotions(batches: Mapping[int, Sequence[Emotion]] | None = None) -> int:
    source = BATCHES if batches is None else batches
    return sum(len(records) for records in source.values())


def is_first_batch(number: int, batches: Mapping[int, Sequence[Emotion]] | None = None) -> bool:
    """True if ``number`` is the lowest-numbered registered batch.

    The baseline identity portrait (generation #1) is established in the first
    batch, so this decides whether a batch's prompt file includes the baseline.
    """
    source = BATCHES if batches is None else batches
    return bool(source) and number == min(source)
