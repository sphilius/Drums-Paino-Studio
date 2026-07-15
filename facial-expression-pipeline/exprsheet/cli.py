"""Command-line interface: ``python -m exprsheet <command>``.

Commands
--------
``build --batch N``   Emit the print-ready PDF and the companion Markdown
                      prompt file for batch ``N``.
``check``             Run the cross-batch dedupe check and report.
``list``              List every registered batch and its record count.

Every ``build`` runs the hard dedupe check first and aborts loudly on any
collision.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import registry
from .pdf import build_pdf
from .prompts import build_prompts_markdown
from .registry import (
    BATCH_SIZE,
    BATCHES,
    DuplicateEmotionError,
    UnknownBatchError,
    batch_offset,
    check_duplicates,
    is_first_batch,
    total_emotions,
)


def _run_dedupe_check() -> None:
    """Run the global dedupe check; exit(2) with a clear message on collision."""
    try:
        check_duplicates()
    except DuplicateEmotionError as exc:
        print(f"error: dedupe check failed - {exc}", file=sys.stderr)
        raise SystemExit(2) from exc


def cmd_build(args: argparse.Namespace) -> int:
    number: int = args.batch
    out_dir = Path(args.out)

    # 1. Hard dedupe across ALL batches before emitting anything.
    _run_dedupe_check()

    # 2. Resolve the requested batch.
    try:
        emotions = registry.get_batch(number)
    except UnknownBatchError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    if not emotions:
        print(f"error: batch {number} is empty", file=sys.stderr)
        return 2
    if len(emotions) != BATCH_SIZE:
        print(
            f"warning: batch {number} has {len(emotions)} records "
            f"(expected {BATCH_SIZE})",
            file=sys.stderr,
        )

    offset = batch_offset(number)
    batch_dir = out_dir / f"batch-{number:02d}"
    pdf_path = batch_dir / f"exprsheet-batch-{number:02d}.pdf"
    prompts_path = batch_dir / f"prompts-batch-{number:02d}.md"

    written: list[str] = []

    if not args.no_pdf:
        pages = build_pdf(number, emotions, pdf_path, offset=offset)
        written.append(f"  PDF     {pdf_path}  ({pages} pages)")

    if not args.no_prompts:
        markdown = build_prompts_markdown(
            number,
            emotions,
            offset=offset,
            include_baseline=is_first_batch(number),
            series_total=total_emotions(),
        )
        prompts_path.parent.mkdir(parents=True, exist_ok=True)
        prompts_path.write_text(markdown, encoding="utf-8")
        written.append(f"  Prompts {prompts_path}")

    print(f"Built batch {number:02d} ({len(emotions)} emotions):")
    for line in written:
        print(line)
    return 0


def cmd_check(args: argparse.Namespace) -> int:
    try:
        check_duplicates()
    except DuplicateEmotionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    print(
        f"OK: {total_emotions()} emotions across {len(BATCHES)} batch(es), "
        "no duplicates."
    )
    return 0


def cmd_list(args: argparse.Namespace) -> int:
    if not BATCHES:
        print("No batches registered.")
        return 0
    for number in sorted(BATCHES):
        records = BATCHES[number]
        flag = "" if len(records) == BATCH_SIZE else f"  (expected {BATCH_SIZE}!)"
        print(f"batch {number:02d}: {len(records):>3} records{flag}")
    print(f"total: {total_emotions()} emotions across {len(BATCHES)} batch(es)")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="exprsheet",
        description="Generate facial-expression reference sheets (PDF + prompts).",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_build = sub.add_parser(
        "build", help="emit the PDF and prompt Markdown for a batch"
    )
    p_build.add_argument(
        "--batch", "-b", type=int, required=True, help="batch number to build"
    )
    p_build.add_argument(
        "--out", "-o", default="output", help="output directory (default: output)"
    )
    p_build.add_argument(
        "--no-pdf", action="store_true", help="skip the PDF, emit only prompts"
    )
    p_build.add_argument(
        "--no-prompts", action="store_true", help="skip the prompts, emit only the PDF"
    )
    p_build.set_defaults(func=cmd_build)

    p_check = sub.add_parser("check", help="run the cross-batch dedupe check")
    p_check.set_defaults(func=cmd_check)

    p_list = sub.add_parser("list", help="list registered batches and counts")
    p_list.set_defaults(func=cmd_list)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
