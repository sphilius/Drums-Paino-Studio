"""Core data schema for the facial-expression reference pipeline.

Every emotion is represented by a single immutable :class:`Emotion` record.  A
record carries its display name, its Mood-Meter *color quadrant*, a one-line
definition, and a :class:`FACS` breakdown with exactly six anatomical fields.

The dataclasses validate themselves on construction, so an ill-formed record
(missing FACS field, blank name, wrong quadrant type) fails loudly the moment
it is defined in :mod:`exprsheet.data` rather than silently reaching the PDF.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

# The six FACS regions, in the order they should appear on a reference sheet.
FACS_FIELDS: tuple[str, ...] = (
    "brow",
    "eyes",
    "nose",
    "mouth",
    "cheeks",
    "head_and_shoulders",
)

# Human-friendly labels for each FACS region (used by the PDF renderer).
FACS_LABELS: dict[str, str] = {
    "brow": "Brow",
    "eyes": "Eyes",
    "nose": "Nose",
    "mouth": "Mouth",
    "cheeks": "Cheeks",
    "head_and_shoulders": "Head & shoulders",
}


class Quadrant(Enum):
    """The four Mood-Meter color quadrants (energy x pleasantness)."""

    YELLOW = "Yellow"  # high energy, pleasant
    RED = "Red"  # high energy, unpleasant
    BLUE = "Blue"  # low energy, unpleasant
    GREEN = "Green"  # low energy, pleasant

    @property
    def energy(self) -> str:
        return _QUADRANT_META[self].energy

    @property
    def pleasantness(self) -> str:
        return _QUADRANT_META[self].pleasantness

    @property
    def hex_color(self) -> str:
        """Badge fill color for this quadrant (hex string)."""
        return _QUADRANT_META[self].hex_color

    @property
    def text_color(self) -> str:
        """Legible text color to place on top of :attr:`hex_color`."""
        return _QUADRANT_META[self].text_color

    @property
    def summary(self) -> str:
        """e.g. ``"High energy - Pleasant"``."""
        return f"{self.energy} energy - {self.pleasantness}"


@dataclass(frozen=True)
class _QuadrantMeta:
    energy: str
    pleasantness: str
    hex_color: str
    text_color: str


_QUADRANT_META: dict[Quadrant, _QuadrantMeta] = {
    Quadrant.YELLOW: _QuadrantMeta("High", "Pleasant", "#EAB308", "#1F2937"),
    Quadrant.RED: _QuadrantMeta("High", "Unpleasant", "#DC2626", "#FFFFFF"),
    Quadrant.BLUE: _QuadrantMeta("Low", "Unpleasant", "#2563EB", "#FFFFFF"),
    Quadrant.GREEN: _QuadrantMeta("Low", "Pleasant", "#16A34A", "#FFFFFF"),
}


@dataclass(frozen=True)
class FACS:
    """A six-region Facial Action Coding System breakdown.

    Each field is a short natural-language description of what that region does
    for the target expression.  All six fields are required and must be
    non-empty; construction raises ``ValueError`` otherwise.
    """

    brow: str
    eyes: str
    nose: str
    mouth: str
    cheeks: str
    head_and_shoulders: str

    def __post_init__(self) -> None:
        for field_name in FACS_FIELDS:
            value = getattr(self, field_name)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(
                    f"FACS field {field_name!r} must be a non-empty string, "
                    f"got {value!r}"
                )

    def rows(self) -> list[tuple[str, str]]:
        """Return ``(label, description)`` pairs in canonical FACS order."""
        return [(FACS_LABELS[f], getattr(self, f)) for f in FACS_FIELDS]

    def as_dict(self) -> dict[str, str]:
        return {f: getattr(self, f) for f in FACS_FIELDS}


@dataclass(frozen=True)
class Emotion:
    """A single emotion record: the atomic unit of a reference sheet."""

    name: str
    quadrant: Quadrant
    definition: str
    facs: FACS

    def __post_init__(self) -> None:
        if not isinstance(self.name, str) or not self.name.strip():
            raise ValueError("Emotion.name must be a non-empty string")
        if not isinstance(self.quadrant, Quadrant):
            raise TypeError(
                f"Emotion.quadrant must be a Quadrant, got {type(self.quadrant)!r}"
            )
        if not isinstance(self.definition, str) or not self.definition.strip():
            raise ValueError(f"Emotion.definition for {self.name!r} must be non-empty")
        if not isinstance(self.facs, FACS):
            raise TypeError(
                f"Emotion.facs for {self.name!r} must be a FACS, got {type(self.facs)!r}"
            )

    @property
    def key(self) -> str:
        """Normalized dedupe key: case-insensitive, whitespace-trimmed name."""
        return self.name.strip().lower()
