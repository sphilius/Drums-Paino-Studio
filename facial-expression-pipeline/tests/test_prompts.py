"""The baseline-consistency trick must be reflected in the prompt Markdown."""

from __future__ import annotations

from exprsheet import registry
from exprsheet.prompts import (
    BASELINE_FILENAME,
    BASELINE_MARKER,
    build_prompts_markdown,
)


def _first_batch_markdown() -> str:
    number = min(registry.BATCHES)
    return build_prompts_markdown(
        number,
        registry.BATCHES[number],
        offset=registry.batch_offset(number),
        include_baseline=registry.is_first_batch(number),
        series_total=registry.total_emotions(),
    )


def test_first_batch_includes_zero_reference_baseline() -> None:
    md = _first_batch_markdown()
    assert BASELINE_MARKER in md
    assert "ZERO reference images" in md


def test_every_expression_prompt_references_the_baseline() -> None:
    number = min(registry.BATCHES)
    md = _first_batch_markdown()
    # One reference per emotion (baseline block also names the file once).
    assert md.count(BASELINE_FILENAME) >= len(registry.BATCHES[number])


def test_generation_numbers_are_sequential_and_start_after_baseline() -> None:
    number = min(registry.BATCHES)
    emotions = registry.BATCHES[number]
    md = _first_batch_markdown()
    # Baseline is generation #1; the first emotion is generation #2, and so on.
    assert "### Generation #2 -" in md
    last = len(emotions) + 1  # +1 for the baseline occupying generation #1
    assert f"### Generation #{last} -" in md


def test_later_batch_omits_baseline_but_still_references_it() -> None:
    number = min(registry.BATCHES)
    emotions = registry.BATCHES[number]
    md = build_prompts_markdown(
        number + 99,  # pretend it's a later batch
        emotions,
        offset=len(emotions),
        include_baseline=False,
        series_total=2 * len(emotions),
    )
    assert BASELINE_MARKER not in md
    assert BASELINE_FILENAME in md
    # Continues global numbering: first emotion here is generation #(len+2).
    assert f"### Generation #{len(emotions) + 2} -" in md
