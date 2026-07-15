"""exprsheet - a reproducible facial-expression reference-sheet pipeline.

Importing the package loads :mod:`exprsheet.data`, which registers every batch
in the source-of-truth registry, so the cross-batch dedupe check always sees
the full set.
"""

from __future__ import annotations

from . import data as _data  # noqa: F401  (import registers batches on load)
from .registry import (
    BATCH_SIZE,
    BATCHES,
    DuplicateEmotionError,
    check_duplicates,
    total_emotions,
)
from .schema import Emotion, FACS, Quadrant

__version__ = "0.1.0"

__all__ = [
    "BATCH_SIZE",
    "BATCHES",
    "DuplicateEmotionError",
    "Emotion",
    "FACS",
    "Quadrant",
    "check_duplicates",
    "total_emotions",
    "__version__",
]
