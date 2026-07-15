"""Source-of-truth emotion data.

This module is the *single place* where emotion records live.  Each batch is a
list of exactly :data:`~exprsheet.registry.BATCH_SIZE` (24) records registered
via :func:`~exprsheet.registry.register_batch`.  Importing the package imports
this module, so every batch is always present for the cross-batch dedupe check.

The pipeline is designed to grow to ~282 emotions (~12 batches).  Batch 1 below
is a working starter set spanning all four Mood-Meter quadrants; replace or
extend it with your own records, then append new batches with ``register_batch``
in blocks of 24.  To add batch 2, paste a second ``register_batch(2, [...])``
block at the bottom of this file.
"""

from __future__ import annotations

from .registry import register_batch
from .schema import Emotion, FACS, Quadrant

# Terse quadrant aliases to keep the record tables readable.
Y, R, B, G = Quadrant.YELLOW, Quadrant.RED, Quadrant.BLUE, Quadrant.GREEN


def _e(
    name: str,
    quadrant: Quadrant,
    definition: str,
    *,
    brow: str,
    eyes: str,
    nose: str,
    mouth: str,
    cheeks: str,
    head_and_shoulders: str,
) -> Emotion:
    """Compact constructor for an :class:`Emotion` with its FACS breakdown."""
    return Emotion(
        name=name,
        quadrant=quadrant,
        definition=definition,
        facs=FACS(
            brow=brow,
            eyes=eyes,
            nose=nose,
            mouth=mouth,
            cheeks=cheeks,
            head_and_shoulders=head_and_shoulders,
        ),
    )


# ---------------------------------------------------------------------------
# Batch 1 - starter set (6 per quadrant).  Replace with your own if desired.
# ---------------------------------------------------------------------------
BATCH_1: list[Emotion] = [
    # -- Yellow: high energy, pleasant -------------------------------------
    _e(
        "Joyful", Y, "Great pleasure and buoyant lightness of being.",
        brow="Relaxed, untensed (no AU4)",
        eyes="Narrowed by raised cheeks; crow's-feet visible (AU6)",
        nose="Neutral",
        mouth="Open smile, corners pulled up and back (AU12)",
        cheeks="Lifted high (AU6)",
        head_and_shoulders="Head upright, shoulders open and slightly back",
    ),
    _e(
        "Excited", Y, "Eager, high-energy anticipation of something good.",
        brow="Raised (AU1+AU2)",
        eyes="Widened and bright, upper lid raised (AU5)",
        nose="Neutral to slightly flared",
        mouth="Open smile, teeth may show (AU12+AU25)",
        cheeks="Raised (AU6)",
        head_and_shoulders="Chin lifted, shoulders raised and leaning forward",
    ),
    _e(
        "Elated", Y, "Ecstatic, on-top-of-the-world exhilaration.",
        brow="Raised and relaxed (AU1+AU2)",
        eyes="Wide and shining (AU5)",
        nose="Neutral",
        mouth="Broad open grin, jaw dropped (AU12+AU25+AU26)",
        cheeks="Strongly raised (AU6)",
        head_and_shoulders="Head tilted back, chin up, shoulders squared and open",
    ),
    _e(
        "Hopeful", Y, "Optimistic expectation that things will turn out well.",
        brow="Inner corners lightly raised (AU1)",
        eyes="Softly widened, gaze directed upward",
        nose="Neutral",
        mouth="Gentle closed smile, corners up (mild AU12)",
        cheeks="Softly lifted",
        head_and_shoulders="Head tilted slightly up, shoulders relaxed and open",
    ),
    _e(
        "Playful", Y, "Lighthearted, teasing, and mischievous.",
        brow="One brow slightly raised (asymmetric AU2)",
        eyes="Sparkling with a slight squint (AU6)",
        nose="Slight crinkle",
        mouth="Asymmetric grin or smirk (unilateral AU12)",
        cheeks="Raised on the smiling side",
        head_and_shoulders="Head cocked to one side, shoulders loose",
    ),
    _e(
        "Proud", Y, "Satisfied confidence in an accomplishment.",
        brow="Relaxed, slightly lowered",
        eyes="Calm, steady, direct gaze",
        nose="Neutral",
        mouth="Closed confident smile (mild AU12)",
        cheeks="Lightly raised",
        head_and_shoulders="Chin up, chest out, shoulders back and squared",
    ),
    # -- Red: high energy, unpleasant --------------------------------------
    _e(
        "Angry", R, "Strong displeasure and hostile antagonism.",
        brow="Drawn down and together (AU4)",
        eyes="Glaring; upper lids tense, lower lids tightened (AU5+AU7)",
        nose="Nostrils flared",
        mouth="Lips pressed or squared, teeth may bare (AU23/AU24)",
        cheeks="Tensed",
        head_and_shoulders="Head thrust forward, shoulders raised and rigid",
    ),
    _e(
        "Anxious", R, "Uneasy, restless worry about a possible threat.",
        brow="Inner corners raised and drawn together (AU1+AU4)",
        eyes="Widened with darting gaze (AU5)",
        nose="Neutral",
        mouth="Lips slightly stretched or pressed thin (AU20)",
        cheeks="Tense",
        head_and_shoulders="Head slightly retracted, shoulders creeping toward ears",
    ),
    _e(
        "Frustrated", R, "Irritated by being blocked from a goal.",
        brow="Lowered and knitted (AU4)",
        eyes="Narrowed with tense lower lids (AU7)",
        nose="Slight wrinkle",
        mouth="Lips pressed, jaw set (AU24)",
        cheeks="Tightened",
        head_and_shoulders="Head lowered, shoulders tense and hunched",
    ),
    _e(
        "Fearful", R, "Alarm and dread in the face of danger.",
        brow="Raised and drawn together (AU1+AU2+AU4)",
        eyes="Wide with whites showing, upper lids high (AU5)",
        nose="Neutral",
        mouth="Lips stretched horizontally, may part (AU20+AU25)",
        cheeks="Pulled back and tense",
        head_and_shoulders="Head pulled back, shoulders raised and braced",
    ),
    _e(
        "Stressed", R, "Overwhelmed and pressured by too many demands.",
        brow="Furrowed (AU4)",
        eyes="Tired yet tense, with a slight squint (AU7)",
        nose="Neutral",
        mouth="Lips compressed, corners drawn tight (AU24)",
        cheeks="Drawn",
        head_and_shoulders="Head heavy and forward, shoulders tight and elevated",
    ),
    _e(
        "Jealous", R, "Resentful of a rival or of what others have.",
        brow="Lowered, faintly asymmetric (AU4)",
        eyes="Sidelong, narrowed glance (AU7)",
        nose="Faint sneer wrinkle",
        mouth="Tight, one corner pulled sideways (AU14)",
        cheeks="Tensed unevenly",
        head_and_shoulders="Head turned slightly away, shoulders guarded",
    ),
    # -- Blue: low energy, unpleasant --------------------------------------
    _e(
        "Sad", B, "Sorrow and heaviness from loss or disappointment.",
        brow="Inner corners raised (AU1)",
        eyes="Downcast, upper lids drooping",
        nose="Neutral",
        mouth="Corners pulled down (AU15)",
        cheeks="Slack",
        head_and_shoulders="Head lowered, shoulders slumped forward",
    ),
    _e(
        "Lonely", B, "A painful sense of isolation and disconnection.",
        brow="Inner corners slightly raised (AU1)",
        eyes="Distant, unfocused gaze",
        nose="Neutral",
        mouth="Slightly downturned and closed (mild AU15)",
        cheeks="Flat",
        head_and_shoulders="Head inclined down, shoulders drawn inward",
    ),
    _e(
        "Disappointed", B, "Let down by an expectation that went unmet.",
        brow="Inner corners up with a slight furrow (AU1+AU4)",
        eyes="Lowered gaze, lids relaxed",
        nose="Neutral",
        mouth="Corners down, lips pressed (AU15)",
        cheeks="Slack",
        head_and_shoulders="Head dips, shoulders drop",
    ),
    _e(
        "Bored", B, "Weary, restless lack of interest.",
        brow="Relaxed to slightly lowered",
        eyes="Half-closed and unfocused, lids drooping",
        nose="Neutral",
        mouth="Slack, corners neutral, faint pout possible",
        cheeks="Slack",
        head_and_shoulders="Head propped or tilted, shoulders slumped",
    ),
    _e(
        "Fatigued", B, "Deep tiredness and depleted energy.",
        brow="Relaxed and heavy",
        eyes="Drooping, lids low and slow (AU43)",
        nose="Neutral",
        mouth="Slack, slightly open",
        cheeks="Sagging",
        head_and_shoulders="Head heavy and lowered, shoulders collapsed",
    ),
    _e(
        "Hopeless", B, "Despairing belief that nothing can improve.",
        brow="Inner corners raised and drawn (AU1+AU4)",
        eyes="Downcast and dull, lids low",
        nose="Neutral",
        mouth="Corners strongly turned down (AU15)",
        cheeks="Slack",
        head_and_shoulders="Head hangs, shoulders deeply slumped",
    ),
    # -- Green: low energy, pleasant ---------------------------------------
    _e(
        "Calm", G, "Peaceful, untroubled, and at rest.",
        brow="Smooth and relaxed",
        eyes="Soft, lids relaxed at rest",
        nose="Neutral",
        mouth="Relaxed, with a faint closed smile",
        cheeks="Relaxed",
        head_and_shoulders="Head level, shoulders low and easy",
    ),
    _e(
        "Content", G, "Quiet satisfaction with things as they are.",
        brow="Relaxed",
        eyes="Softly narrowed and gentle",
        nose="Neutral",
        mouth="Slight closed smile (faint AU12)",
        cheeks="Lightly lifted",
        head_and_shoulders="Head level, shoulders relaxed",
    ),
    _e(
        "Serene", G, "Clear, tranquil, unhurried ease.",
        brow="Smooth",
        eyes="Half-lidded with a soft gaze",
        nose="Neutral",
        mouth="Faint peaceful smile",
        cheeks="Relaxed",
        head_and_shoulders="Head gently upright, shoulders open and low",
    ),
    _e(
        "Relaxed", G, "Free of tension and physically at ease.",
        brow="Fully relaxed",
        eyes="Lids soft, gaze unhurried",
        nose="Neutral",
        mouth="Loose, corners neutral to faintly up",
        cheeks="Slack and easy",
        head_and_shoulders="Head resting easily, shoulders dropped",
    ),
    _e(
        "Grateful", G, "Warm appreciation for something received.",
        brow="Inner corners softly raised (gentle AU1)",
        eyes="Soft, slightly moist, warm gaze (mild AU6)",
        nose="Neutral",
        mouth="Gentle, warm smile (AU12)",
        cheeks="Softly raised",
        head_and_shoulders="Head slightly bowed, shoulders open",
    ),
    _e(
        "Secure", G, "Safe, settled, and free from worry.",
        brow="Smooth and relaxed",
        eyes="Steady, easy gaze",
        nose="Neutral",
        mouth="Relaxed, with a faint smile",
        cheeks="Relaxed",
        head_and_shoulders="Head level and grounded, shoulders settled",
    ),
]

register_batch(1, BATCH_1)

# ---------------------------------------------------------------------------
# Append further batches below as you paste them in (blocks of 24):
#
#     BATCH_2: list[Emotion] = [ _e(...), ... ]   # exactly 24 records
#     register_batch(2, BATCH_2)
#
# The cross-batch dedupe check runs automatically on every build, so a name
# that already exists in an earlier batch will fail loudly.
# ---------------------------------------------------------------------------
