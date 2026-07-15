"""Generate the companion Markdown file of image-generation prompts.

The whole series depicts ONE consistent synthetic subject (an invented person,
never a real individual).  Identity consistency is achieved with a baseline
trick:

* **Generation #1** is a neutral baseline portrait rendered with *zero*
  reference images.  Its output (saved as ``baseline.png``) defines the
  canonical face for the entire multi-batch series.
* **Every subsequent prompt** references ``baseline.png`` and instructs the
  model to keep identity, lighting, framing and background locked while
  changing *only* the facial expression.

Generation numbers are global across all batches, so batch 2's first emotion
continues counting from where batch 1 left off.
"""

from __future__ import annotations

from collections.abc import Sequence

from .schema import Emotion, FACS_LABELS, FACS_FIELDS

#: A fixed textual description of the invented subject.  Reused verbatim in
#: every prompt so the identity is described identically each time.
SUBJECT = (
    "a fictional, synthetic adult subject (NOT a real person and not resembling "
    "any real individual): androgynous, approximately 30 years old, short-cropped "
    "dark hair, smooth even skin, no glasses, no jewelry, no makeup, natural "
    "unremarkable features"
)

#: Technical specification appended to every prompt to keep the look consistent.
TECH_SPEC = (
    "Photographic realism, grayscale (black-and-white), soft even studio key "
    "light, plain mid-gray seamless background, sharp focus on the face, "
    "shoulders-up head-and-shoulders framing, camera at eye level, 85mm portrait "
    "look, no text, no watermark, no props, no border."
)

#: Marker string that identifies the baseline block (also asserted in tests).
BASELINE_MARKER = "Generation #1 - Baseline identity"

BASELINE_FILENAME = "baseline.png"


def baseline_prompt(series_total: int) -> str:
    """Return the Markdown block for the zero-reference baseline (generation #1)."""
    return (
        f"### {BASELINE_MARKER} (run with ZERO reference images)\n\n"
        "> Establishes the canonical face for the entire series. Run this first, "
        "with **no reference image attached**.\n\n"
        "**Prompt:**\n\n"
        "```\n"
        f"Create {SUBJECT} with a completely neutral, relaxed resting expression "
        f"(no emotion). {TECH_SPEC}\n"
        "```\n\n"
        f"After generating, save the output as `{BASELINE_FILENAME}`. This is the "
        f"identity anchor for all {series_total} reference portraits in this "
        "series; every generation below must reference it so the face stays "
        "identical and only the expression changes.\n"
    )


def emotion_prompt(emotion: Emotion, generation: int) -> str:
    """Return the Markdown block for a single expression prompt."""
    cues = "; ".join(
        f"{FACS_LABELS[field]} - {getattr(emotion.facs, field)}"
        for field in FACS_FIELDS
    )
    return (
        f"### Generation #{generation} - {emotion.name} "
        f"({emotion.quadrant.value})\n\n"
        f"> {emotion.definition}\n\n"
        f"**Prompt (reference `{BASELINE_FILENAME}` for identity):**\n\n"
        "```\n"
        f"Using generation #1 (`{BASELINE_FILENAME}`) as the fixed identity "
        f"reference, render the SAME subject now expressing {emotion.name.upper()}. "
        "Keep the identity, hairstyle, skin, lighting, framing, background and "
        "camera identical to the baseline; change ONLY the facial expression. "
        f"Target expression cues -> {cues}. {TECH_SPEC}\n"
        "```\n"
    )


def build_prompts_markdown(
    number: int,
    emotions: Sequence[Emotion],
    *,
    offset: int = 0,
    include_baseline: bool = True,
    series_total: int | None = None,
) -> str:
    """Build the full Markdown prompt document for one batch.

    ``offset`` is the number of emotions in all earlier batches, used to compute
    global generation numbers.  Generation #1 is reserved for the baseline, so
    the first emotion of the first batch is generation #2.

    ``include_baseline`` should be True only for the first batch (that is where
    the baseline is established); later batches reference the already-existing
    baseline instead.
    """
    total = series_total if series_total is not None else offset + len(emotions)
    lines: list[str] = []
    lines.append(f"# Image-generation prompts - Batch {number:02d}\n")
    lines.append(
        "One consistent synthetic subject, photographic grayscale reference "
        "portraits. Generation numbers are global across every batch.\n"
    )

    if include_baseline:
        lines.append(baseline_prompt(total))
        lines.append("---\n")
    else:
        lines.append(
            f"> Identity baseline (`{BASELINE_FILENAME}`, generation #1) was "
            "established in batch 01. Every prompt below references it - keep the "
            "face locked and change only the expression.\n"
        )
        lines.append("---\n")

    # Generation #1 is the baseline, so the first-ever emotion is generation #2.
    for position, emotion in enumerate(emotions):
        generation = offset + position + 2
        lines.append(emotion_prompt(emotion, generation))
        lines.append("---\n")

    return "\n".join(lines).rstrip() + "\n"
