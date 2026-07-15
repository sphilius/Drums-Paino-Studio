# exprsheet — facial-expression reference-sheet pipeline

A reproducible pipeline that turns a source-of-truth emotion dataset into
**print-ready reference sheets** (PDF) and **copy-paste image-generation
prompts** (Markdown) — one emotion per page, one consistent synthetic face
across the entire series.

Built to replace the "one batch at a time by hand" workflow: emotions live in a
single data module, every build runs a hard cross-batch dedupe, and the prompts
enforce a baseline-image trick so the reference portrait's identity stays locked
while only the expression changes.

---

## What it produces

For a given batch `N`, `build` writes two files under `output/batch-NN/`:

| File | What it is |
| --- | --- |
| `exprsheet-batch-NN.pdf` | Print-ready sheets: a cover page, one page per emotion (color-quadrant badge, one-line definition, six-row FACS table, empty reference-photo box), and a closing page. |
| `prompts-batch-NN.md` | Copy-paste prompts for a grayscale photographic portrait of one synthetic subject, numbered by global generation. |

---

## Data model

Every emotion is one immutable record (`exprsheet/schema.py`):

- **name** — e.g. `Joyful`
- **quadrant** — the Mood-Meter color quadrant: `Yellow` (high energy / pleasant),
  `Red` (high / unpleasant), `Blue` (low / unpleasant), `Green` (low / pleasant)
- **definition** — one line
- **facs** — six fields: `brow`, `eyes`, `nose`, `mouth`, `cheeks`,
  `head_and_shoulders`

Records validate themselves on construction — a blank FACS field or a bad
quadrant fails at import, not at render time. The dataset is the **single source
of truth** in `exprsheet/data.py`; it ships with a 24-emotion starter batch and
is designed to grow to ~282 emotions (~12 batches of 24).

---

## Install

From this directory:

```bash
python -m pip install -e ".[dev]"      # editable install + test deps
# or, without installing the package:
python -m pip install -r requirements-dev.txt
```

Requires Python 3.10+. Runtime dependency: `reportlab`. Test-only:
`pytest`, `pypdf`.

---

## Usage

Run as a module from this directory (or use the `exprsheet` console script after
`pip install -e .`):

```bash
python -m exprsheet list               # show registered batches + counts
python -m exprsheet check              # run the cross-batch dedupe check only
python -m exprsheet build --batch 1    # emit PDF + prompts for batch 1
```

`build` options:

```
--batch, -b N     batch number to build (required)
--out,   -o DIR   output directory (default: output)
--no-pdf          emit only the prompt Markdown
--no-prompts      emit only the PDF
```

Every `build` runs the dedupe check **first** and aborts (exit code 2) if any
emotion name appears in more than one batch — so no face is ever emitted twice.

---

## The batch workflow

The pipeline is designed to be fed 24 emotions at a time:

1. **Add a batch.** In `exprsheet/data.py`, define a new list of exactly 24
   `Emotion` records and register it:

   ```python
   BATCH_2 = [ _e("Awe", Y, "…", brow="…", eyes="…", nose="…",
                  mouth="…", cheeks="…", head_and_shoulders="…"),
               # …23 more… ]
   register_batch(2, BATCH_2)
   ```

2. **Check.** `python -m exprsheet check` — confirms the new names don't collide
   with any earlier batch. A collision fails loudly, naming both batches.

3. **Build.** `python -m exprsheet build --batch 2` — writes
   `output/batch-02/`.

4. **Generate the images** using `prompts-batch-02.md` (see below), then paste
   each portrait into the matching page's reference-photo box.

Generation numbers are **global**: batch 2's first emotion continues counting
from where batch 1 stopped, so the baseline reference stays valid across every
batch.

---

## The baseline-image consistency trick

Every portrait in the series must look like the **same person** — a single
invented, synthetic subject (never a real individual) — with only the expression
changing. The prompt file enforces this:

- **Generation #1 — the baseline.** The first prompt renders a *neutral* portrait
  of the synthetic subject with **zero reference images attached**. Its output is
  the canonical face. Save it as `baseline.png`.
- **Every later generation** (#2, #3, …) explicitly references `baseline.png` as
  the identity anchor and instructs the model to keep identity, hairstyle,
  lighting, framing and background **identical**, changing *only* the facial
  expression — steered by that emotion's six FACS cues.

Because the subject description and technical spec are fixed constants reused
verbatim in every prompt, and every non-baseline prompt points back to the one
baseline image, identity drift is minimized across all ~282 portraits. If a face
starts to drift, regenerate that one against the same `baseline.png`.

---

## Tests

```bash
python -m pytest            # from this directory
```

The suite asserts the pipeline's invariants:

- **24 records per batch** — every registered batch has exactly `BATCH_SIZE`.
- **Zero cross-batch collisions** — and the dedupe guard actually raises on a
  planted collision (cross-batch and within-batch).
- **All six FACS fields populated** on every record.
- **PDF page count = records + cover + closing** — verified both from the
  builder's return value and by re-reading the written PDF with `pypdf`.
- Baseline-trick invariants: generation #1 is the zero-reference baseline, and
  every expression prompt references it.

---

## Project layout

```
facial-expression-pipeline/
├── exprsheet/
│   ├── __init__.py      # loads data so every batch is registered
│   ├── __main__.py      # python -m exprsheet
│   ├── schema.py        # Emotion / FACS / Quadrant (self-validating)
│   ├── data.py          # SOURCE OF TRUTH: the emotion records
│   ├── registry.py      # batch registry + hard dedupe check
│   ├── prompts.py       # Markdown prompt generation (baseline trick)
│   ├── pdf.py           # reportlab reference-sheet renderer
│   └── cli.py           # argparse CLI (build / check / list)
├── tests/
├── pyproject.toml
├── requirements.txt
├── requirements-dev.txt
└── README.md
```
