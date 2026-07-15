"""Print-ready PDF renderer (reportlab).

Layout: one cover page, one page per emotion, one closing page.  Each emotion
page shows a color-quadrant badge, the one-line definition, a six-row FACS
table, and an empty reference-photo box captioned with the generation number.

:func:`build_pdf` returns the number of pages it wrote, which equals
``len(emotions) + COVER_AND_CLOSING_PAGES``.
"""

from __future__ import annotations

from collections import Counter
from datetime import date
from pathlib import Path
from collections.abc import Sequence

from reportlab.lib import colors
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.units import inch
from reportlab.lib.utils import simpleSplit
from reportlab.pdfgen import canvas

from .schema import Emotion, Quadrant

#: Extra pages added around the per-emotion pages (cover + closing).
COVER_AND_CLOSING_PAGES = 2

PAGE_W, PAGE_H = LETTER
MARGIN = 0.75 * inch
CONTENT_W = PAGE_W - 2 * MARGIN

_BODY_FONT = "Helvetica"
_BOLD_FONT = "Helvetica-Bold"
_ITALIC_FONT = "Helvetica-Oblique"


def _draw_wrapped(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    width: float,
    *,
    font: str = _BODY_FONT,
    size: float = 11,
    leading: float = 14,
) -> float:
    """Draw wrapped ``text`` starting at the top-left ``(x, y)``.

    Returns the y-coordinate just below the last line drawn.
    """
    c.setFont(font, size)
    for line in simpleSplit(text, font, size, width):
        c.drawString(x, y, line)
        y -= leading
    return y


def _draw_badge(c: canvas.Canvas, emotion: Emotion, top: float) -> float:
    """Draw the color-quadrant badge; return the y below it."""
    height = 0.7 * inch
    y = top - height
    fill = colors.HexColor(emotion.quadrant.hex_color)
    text_color = colors.HexColor(emotion.quadrant.text_color)

    c.setFillColor(fill)
    c.roundRect(MARGIN, y, CONTENT_W, height, 8, stroke=0, fill=1)

    c.setFillColor(text_color)
    c.setFont(_BOLD_FONT, 26)
    c.drawString(MARGIN + 16, y + height - 34, emotion.name)
    c.setFont(_BODY_FONT, 10)
    c.drawString(
        MARGIN + 16,
        y + 12,
        f"{emotion.quadrant.value.upper()} quadrant  -  {emotion.quadrant.summary}",
    )
    c.setFillColor(colors.black)
    return y - 0.28 * inch


def _draw_facs_table(c: canvas.Canvas, emotion: Emotion, top: float) -> float:
    """Draw the six-row FACS table; return the y below it."""
    label_w = 1.5 * inch
    desc_w = CONTENT_W - label_w
    pad = 6
    size = 10.5
    leading = 13

    c.setFont(_BOLD_FONT, 12)
    c.drawString(MARGIN, top, "FACS breakdown")
    y = top - 20

    c.setStrokeColor(colors.HexColor("#D1D5DB"))
    c.setLineWidth(0.75)

    for label, description in emotion.facs.rows():
        desc_lines = simpleSplit(description, _BODY_FONT, size, desc_w - 2 * pad)
        row_h = max(len(desc_lines), 1) * leading + 2 * pad
        row_top = y
        row_bottom = y - row_h

        # Label cell background.
        c.setFillColor(colors.HexColor("#F3F4F6"))
        c.rect(MARGIN, row_bottom, label_w, row_h, stroke=0, fill=1)
        # Cell borders.
        c.setFillColor(colors.black)
        c.rect(MARGIN, row_bottom, label_w, row_h, stroke=1, fill=0)
        c.rect(MARGIN + label_w, row_bottom, desc_w, row_h, stroke=1, fill=0)

        c.setFont(_BOLD_FONT, size)
        c.drawString(MARGIN + pad, row_top - pad - size + 2, label)

        c.setFont(_BODY_FONT, size)
        text_y = row_top - pad - size + 2
        for line in desc_lines:
            c.drawString(MARGIN + label_w + pad, text_y, line)
            text_y -= leading

        y = row_bottom

    return y


def _draw_photo_box(c: canvas.Canvas, generation: int, top: float, bottom: float) -> None:
    """Draw the empty reference-photo placeholder box."""
    box_top = top - 8
    height = box_top - bottom
    if height < 1.2 * inch:  # guarantee a visible box even on a crowded page
        height = 1.2 * inch
    box_bottom = box_top - height

    c.setStrokeColor(colors.HexColor("#9CA3AF"))
    c.setLineWidth(1)
    c.setDash(4, 3)
    c.rect(MARGIN, box_bottom, CONTENT_W, height, stroke=1, fill=0)
    c.setDash()

    c.setFillColor(colors.HexColor("#9CA3AF"))
    c.setFont(_ITALIC_FONT, 11)
    c.drawCentredString(
        PAGE_W / 2,
        box_bottom + height / 2 + 6,
        "Reference photo",
    )
    c.setFont(_BODY_FONT, 9)
    c.drawCentredString(
        PAGE_W / 2,
        box_bottom + height / 2 - 10,
        f"generation #{generation}  -  paste the generated grayscale portrait here",
    )
    c.setFillColor(colors.black)


def _draw_emotion_page(c: canvas.Canvas, emotion: Emotion, generation: int) -> None:
    top = PAGE_H - MARGIN
    y = _draw_badge(c, emotion, top)
    y = _draw_wrapped(
        c, emotion.definition, MARGIN, y, CONTENT_W, font=_ITALIC_FONT, size=12, leading=15
    )
    y -= 10
    y = _draw_facs_table(c, emotion, y)
    _draw_photo_box(c, generation, y - 6, MARGIN)
    c.showPage()


def _draw_legend(c: canvas.Canvas, top: float) -> float:
    y = top
    chip = 0.22 * inch
    c.setFont(_BODY_FONT, 11)
    for quadrant in Quadrant:
        c.setFillColor(colors.HexColor(quadrant.hex_color))
        c.rect(MARGIN, y - chip, chip, chip, stroke=0, fill=1)
        c.setFillColor(colors.black)
        c.drawString(
            MARGIN + chip + 8,
            y - chip + 4,
            f"{quadrant.value} - {quadrant.summary}",
        )
        y -= chip + 8
    return y


def _draw_cover(
    c: canvas.Canvas, number: int, emotions: Sequence[Emotion], generated_on: date
) -> None:
    top = PAGE_H - 1.4 * inch
    c.setFont(_BOLD_FONT, 30)
    c.drawString(MARGIN, top, "Facial Expression")
    c.drawString(MARGIN, top - 38, "Reference Sheets")

    c.setFont(_BODY_FONT, 16)
    c.drawString(MARGIN, top - 78, f"Batch {number:02d}  -  {len(emotions)} emotions")

    c.setFont(_BODY_FONT, 11)
    c.setFillColor(colors.HexColor("#4B5563"))
    _draw_wrapped(
        c,
        "One consistent synthetic subject - photographic grayscale reference "
        "portraits. Identity is locked to a zero-reference baseline face; only the "
        "expression changes from page to page.",
        MARGIN,
        top - 108,
        CONTENT_W,
        size=11,
        leading=15,
    )
    c.setFillColor(colors.black)

    y = _draw_legend(c, top - 170)

    counts = Counter(e.quadrant for e in emotions)
    c.setFont(_BODY_FONT, 10)
    breakdown = "  -  ".join(
        f"{q.value}: {counts.get(q, 0)}" for q in Quadrant
    )
    c.drawString(MARGIN, y - 12, breakdown)

    c.setFont(_BODY_FONT, 9)
    c.setFillColor(colors.HexColor("#6B7280"))
    c.drawString(MARGIN, MARGIN, f"Generated {generated_on.isoformat()}")
    c.setFillColor(colors.black)
    c.showPage()


def _draw_closing(c: canvas.Canvas, number: int, emotions: Sequence[Emotion]) -> None:
    top = PAGE_H - 1.6 * inch
    c.setFont(_BOLD_FONT, 22)
    c.drawString(MARGIN, top, f"End of Batch {number:02d}")

    counts = Counter(e.quadrant for e in emotions)
    y = top - 40
    c.setFont(_BODY_FONT, 12)
    c.drawString(MARGIN, y, f"{len(emotions)} emotions in this batch:")
    y -= 22
    for quadrant in Quadrant:
        c.setFillColor(colors.HexColor(quadrant.hex_color))
        c.rect(MARGIN, y - 10, 0.2 * inch, 0.2 * inch, stroke=0, fill=1)
        c.setFillColor(colors.black)
        c.setFont(_BODY_FONT, 11)
        c.drawString(MARGIN + 0.3 * inch, y - 8, f"{quadrant.value}: {counts.get(quadrant, 0)}")
        y -= 20

    y -= 16
    c.setFillColor(colors.HexColor("#4B5563"))
    _draw_wrapped(
        c,
        "Consistency reminder: every portrait references generation #1 "
        "(baseline.png). Regenerate against the same baseline if the identity "
        "drifts. No emotion is ever emitted twice - the build fails loudly on any "
        "cross-batch name collision.",
        MARGIN,
        y,
        CONTENT_W,
        size=11,
        leading=15,
    )
    c.setFillColor(colors.black)
    c.showPage()


def build_pdf(
    number: int,
    emotions: Sequence[Emotion],
    out_path: str | Path,
    *,
    offset: int = 0,
    generated_on: date | None = None,
) -> int:
    """Render the reference-sheet PDF for one batch.

    ``offset`` is the count of emotions in earlier batches; combined with the
    baseline (generation #1) it yields each page's global generation number.
    Returns the total page count (cover + one per emotion + closing).
    """
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    generated_on = generated_on or date.today()

    c = canvas.Canvas(str(out_path), pagesize=LETTER)
    c.setTitle(f"Facial Expression Reference Sheets - Batch {number:02d}")

    _draw_cover(c, number, emotions, generated_on)
    for position, emotion in enumerate(emotions):
        generation = offset + position + 2  # +2: generation #1 is the baseline
        _draw_emotion_page(c, emotion, generation)
    _draw_closing(c, number, emotions)

    c.save()
    return len(emotions) + COVER_AND_CLOSING_PAGES
