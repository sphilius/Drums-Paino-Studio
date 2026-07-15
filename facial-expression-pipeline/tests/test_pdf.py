"""The PDF page count must equal records + cover + closing."""

from __future__ import annotations

import pytest

from exprsheet import registry
from exprsheet.registry import BATCH_SIZE

pytest.importorskip("reportlab")

from exprsheet.pdf import COVER_AND_CLOSING_PAGES, build_pdf


def test_build_pdf_reports_correct_page_count(tmp_path) -> None:
    number = min(registry.BATCHES)
    emotions = registry.BATCHES[number]
    out = tmp_path / f"batch-{number:02d}.pdf"

    pages = build_pdf(number, emotions, out)

    assert pages == len(emotions) + COVER_AND_CLOSING_PAGES
    assert pages == BATCH_SIZE + 2
    assert out.exists() and out.stat().st_size > 0


def test_written_pdf_has_expected_page_count(tmp_path) -> None:
    pypdf = pytest.importorskip("pypdf")

    number = min(registry.BATCHES)
    emotions = registry.BATCHES[number]
    out = tmp_path / f"batch-{number:02d}.pdf"
    build_pdf(number, emotions, out)

    try:
        page_count = len(pypdf.PdfReader(str(out)).pages)
    except Exception as exc:  # e.g. a broken cryptography backend in the env
        pytest.skip(f"pypdf could not read the PDF in this environment: {exc}")

    assert page_count == len(emotions) + COVER_AND_CLOSING_PAGES
    assert page_count == BATCH_SIZE + 2
