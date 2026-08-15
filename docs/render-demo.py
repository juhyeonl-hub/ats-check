#!/usr/bin/env python3
"""Render a terminal demo GIF from real ats-check output.

Every line of output in this GIF was produced by running the actual native
binary; nothing is mocked up. Only the shell prompt is coloured, because the
tool itself writes no colour.
"""
from PIL import Image, ImageDraw, ImageFont
import sys

FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
FONT_SIZE = 15
COLS, ROWS = 98, 32
PAD = 20

BG = (22, 22, 30)
FG = (200, 211, 245)
PROMPT = (127, 216, 143)
DIM = (105, 112, 152)

font = ImageFont.truetype(FONT_PATH, FONT_SIZE)
_bbox = font.getbbox("M")
CELL_W = font.getlength("M")
CELL_H = FONT_SIZE + 5

W = int(COLS * CELL_W) + PAD * 2
H = int(ROWS * CELL_H) + PAD * 2


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read().rstrip("\n").split("\n")


def draw_frame(lines):
    """lines: list of segment-lists; each segment is (text, colour)."""
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    for i, segments in enumerate(lines[-ROWS:]):
        x = PAD
        for text, colour in segments:
            d.text((x, PAD + i * CELL_H), text, font=font, fill=colour)
            x += font.getlength(text)
    return img


def shell(cmd, cursor=False):
    """A prompt line: green $, bright command."""
    return [("$ ", PROMPT), (cmd + ("_" if cursor else ""), FG)]


def plain(text):
    return [(text, FG)]


SCENES = [
    ("ats-check --job jobs/alten.md", "out1.txt"),
    ("ats-check --job jobs/wolt.md", "out2.txt"),
    ("ats-check --job-dir jobs", "out3.txt"),
]

frames, delays = [], []
screen = []  # accumulated (text, colour)

for idx, (cmd, out) in enumerate(SCENES):
    # type the command, three characters per frame
    for n in range(0, len(cmd) + 1, 3):
        frames.append(draw_frame(screen + [shell(cmd[:n], cursor=True)]))
        delays.append(45)
    frames.append(draw_frame(screen + [shell(cmd)]))
    delays.append(320)

    screen.append(shell(cmd))

    # reveal output
    for line in read(out):
        screen.append(plain(line))
    frames.append(draw_frame(screen))
    delays.append(1500 if idx < len(SCENES) - 1 else 3000)

    if idx < len(SCENES) - 1:
        screen.append(plain(""))

# hold the final frame a little longer
frames.append(draw_frame(screen))
delays.append(2600)

pal = [f.convert("P", palette=Image.ADAPTIVE, colors=16) for f in frames]
pal[0].save(
    sys.argv[1] if len(sys.argv) > 1 else "demo.gif",
    save_all=True,
    append_images=pal[1:],
    duration=delays,
    loop=0,
    optimize=True,
    disposal=1,
)
print("frames: %d  size: %dx%d" % (len(frames), W, H))
