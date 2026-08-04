from __future__ import annotations

import json
import math
import shutil
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps, ImageFilter, ImageFont

RUN = Path(r"E:\MapleLeaf\project\MiaoWang\.codex-pet-runs\leaf-miao-run")
OUT = RUN / "final"
DECODED = RUN / "decoded"
REFS = RUN / "references"
PROJECT_PET = Path(r"E:\MapleLeaf\project\MiaoWang\front-project\hybrid\html\assets\pet\leaf-miao")

CW = 192
CH = 208
SCALE = 2
HW = CW * SCALE
HH = CH * SCALE
COLS = 8
ROWS = 11
ATLAS_W = COLS * CW
ATLAS_H = ROWS * CH
HATLAS_W = COLS * HW
HATLAS_H = ROWS * HH

OUT.mkdir(parents=True, exist_ok=True)
DECODED.mkdir(parents=True, exist_ok=True)
(OUT / "qa").mkdir(parents=True, exist_ok=True)
PROJECT_PET.mkdir(parents=True, exist_ok=True)

P = {
    "outline": "#4B2E28",
    "body": "#FF9A4A",
    "body2": "#E97C33",
    "belly": "#FFE0B7",
    "cream": "#FFF4E3",
    "muzzle": "#FFD9AA",
    "eye": "#3B2722",
    "eye2": "#F8EEE0",
    "pink": "#FFB0A5",
    "nose": "#C96C53",
    "tongue": "#FF7F95",
    "leaf": "#C86B35",
    "leaf2": "#E6A24D",
    "shadow": "#B76A3E",
}

STATE_FRAMES = {
    "idle": 6,
    "running-right": 8,
    "running-left": 8,
    "waving": 4,
    "jumping": 5,
    "failed": 8,
    "waiting": 6,
    "running": 6,
    "review": 6,
    "look-row-9": 8,
    "look-row-10": 8,
}

ROW_ORDER = [
    "idle",
    "running-right",
    "running-left",
    "waving",
    "jumping",
    "failed",
    "waiting",
    "running",
    "review",
    "look-row-9",
    "look-row-10",
]

LOOKS_9 = [0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5]
LOOKS_10 = [180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5]


def rgba(hex_color: str, alpha: int = 255):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4)) + (alpha,)


def ease(t: float) -> float:
    return 0.5 - 0.5 * math.cos(math.pi * t)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def rot_points(points, cx, cy, deg):
    rad = math.radians(deg)
    c = math.cos(rad)
    s = math.sin(rad)
    out = []
    for x, y in points:
        x -= cx
        y -= cy
        out.append((cx + x * c - y * s, cy + x * s + y * c))
    return out


def draw_capsule(draw, x1, y1, x2, y2, fill, outline=None, width=0):
    draw.rounded_rectangle([x1, y1, x2, y2], radius=(y2 - y1) / 2, fill=fill, outline=outline, width=width)


def draw_ellipse_outline(draw, box, fill, outline, width=0):
    draw.ellipse(box, fill=fill, outline=outline, width=width)


def draw_leaf(draw, cx, cy, s, fill, outline):
    pts = [
        (cx, cy - s * 1.0),
        (cx + s * 0.5, cy - s * 0.2),
        (cx + s * 0.95, cy + s * 0.1),
        (cx + s * 0.45, cy + s * 0.6),
        (cx, cy + s * 1.0),
        (cx - s * 0.45, cy + s * 0.6),
        (cx - s * 0.95, cy + s * 0.1),
        (cx - s * 0.5, cy - s * 0.2),
    ]
    draw.polygon(pts, fill=fill, outline=outline)
    draw.line([(cx, cy - s * 0.95), (cx, cy + s * 0.9)], fill=outline, width=max(1, int(s * 0.12)))


def tail_points(cx, cy, angle_deg, length, bend, lift=0.0):
    rad = math.radians(angle_deg)
    dx = math.cos(rad)
    dy = math.sin(rad)
    base = (cx, cy)
    tip1 = (cx + dx * length * 0.35 - dy * bend * 0.35, cy + dy * length * 0.35 + dx * bend * 0.35 - lift)
    tip2 = (cx + dx * length * 0.75 + dy * bend * 0.15, cy + dy * length * 0.75 - dx * bend * 0.15 - lift * 0.5)
    tip3 = (cx + dx * length, cy + dy * length - lift)
    return [base, tip1, tip2, tip3]


def draw_tail(draw, pts, color, outline, width):
    draw.line(pts, fill=outline, width=width + 4, joint="curve")
    draw.line(pts, fill=color, width=width, joint="curve")
    for p in pts[:1] + pts[-1:]:
        r = width * 0.55
        draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color, outline=outline)


def draw_cat(draw, state: str, frame: int, nframes: int, direction: float | None = None):
    phase = frame / max(nframes, 1)
    t = phase * 2 * math.pi
    cx = HW / 2
    cy = HH / 2 + 6
    bob = 0.0
    body_angle = 0.0
    head_angle = 0.0
    body_scale_x = 1.0
    body_scale_y = 1.0
    eye_open = 1.0
    mouth_open = 0.0
    paw_lift = 0.0
    tail_angle = 20.0
    tail_lift = 0.0
    ears_drop = 0.0
    curl = 0.0
    pose = "front"

    if direction is not None:
        theta = math.radians(direction)
        frontness = (1 - math.cos(theta)) / 2
        turn = math.sin(theta)
        pose = "front" if frontness > 0.72 else "back" if frontness < 0.22 else ("side-right" if turn >= 0 else "side-left")
        bob = math.sin(t) * 1.0
        body_angle = turn * 5
        head_angle = turn * 6
        body_scale_x = 1.0 - abs(turn) * 0.24
        body_scale_y = 1.0 + frontness * 0.06
        tail_angle = 28 + turn * 26
        tail_lift = 6 * (1 - frontness)
    elif state == "idle":
        bob = math.sin(t) * 4.5
        eye_open = 0.95 if frame not in {2, 5} else 0.22
        tail_angle = 25 + math.sin(t * 1.1) * 16
        head_angle = math.sin(t * 0.5) * 2
    elif state == "running-right":
        pose = "side-right"
        body_angle = -8
        body_scale_x = 1.12
        body_scale_y = 0.92
        bob = math.sin(t * 2) * 3.2 - 2
        tail_angle = 150 + math.sin(t) * 12
        paw_lift = math.sin(t)
    elif state == "running-left":
        pose = "side-left"
        body_angle = 8
        body_scale_x = 1.12
        body_scale_y = 0.92
        bob = math.sin(t * 2) * 3.2 - 2
        tail_angle = 30 - math.sin(t) * 12
        paw_lift = math.sin(t)
    elif state == "waving":
        bob = math.sin(t * 2) * 2.0
        paw_lift = 1.0 if frame % 2 == 0 else 0.0
        eye_open = 1.0
        tail_angle = 18 + math.sin(t) * 14
        mouth_open = 0.2
    elif state == "jumping":
        bob = -18 + [0, -8, -14, -8, 0][frame]
        body_angle = -3 + [0, -10, 0, 10, 0][frame]
        body_scale_x = 0.92
        body_scale_y = 0.98
        paw_lift = 0.8
        tail_angle = 72
        mouth_open = 0.1
    elif state == "failed":
        bob = math.sin(t) * 1.1 + 3
        body_angle = -4
        eye_open = 0.25
        ears_drop = 10
        tail_angle = -18 - math.sin(t) * 8
    elif state == "waiting":
        pose = "sleep"
        bob = math.sin(t) * 0.8 + 8
        body_scale_x = 1.06
        body_scale_y = 0.9
        curl = 1.0
        eye_open = 0.0
        ears_drop = 15
        tail_angle = -90
    elif state == "running":
        pose = "side-right"
        body_angle = -14
        body_scale_x = 1.18
        body_scale_y = 0.88
        bob = math.sin(t * 2.5) * 4.5 - 4
        tail_angle = 162 + math.sin(t * 1.5) * 14
        mouth_open = 0.08
        paw_lift = 1.0
    elif state == "review":
        bob = math.sin(t * 1.2) * 1.4
        body_angle = 2
        eye_open = 0.9
        mouth_open = 0.0
        tail_angle = 12 + math.sin(t) * 8
    
    if direction is not None:
        if pose == "front":
            body_scale_x = 1.0 - abs(math.sin(math.radians(direction))) * 0.08
            body_scale_y = 1.0 + 0.04 * math.cos(math.radians(direction))
        elif pose == "back":
            body_scale_x = 0.98
            body_scale_y = 1.02
        else:
            body_scale_x = 1.0 - abs(math.sin(math.radians(direction))) * 0.26
            body_scale_y = 0.96 + 0.05 * abs(math.cos(math.radians(direction)))

    # Shadowless sticker look, all in a compact pose.
    cx += 0
    cy += bob * SCALE

    # Back limbs first.
    body_w = 118 * body_scale_x
    body_h = 86 * body_scale_y
    head_w = 72 * (0.94 + 0.06 * body_scale_x)
    head_h = 66 * (0.95 + 0.05 * body_scale_y)
    belly_w = 76 * (0.96 + 0.04 * body_scale_y)
    belly_h = 54 * (0.92 + 0.08 * body_scale_y)

    def oval(box, fill, outline, width=5):
        draw_ellipse_outline(draw, box, fill=fill, outline=outline, width=width)

    # Tail.
    tail_base_x = cx + 48 * (1 if state not in {"running-left"} and (direction is None or math.sin(math.radians(direction)) >= 0) else -1)
    if direction is not None:
        sign = 1 if math.sin(math.radians(direction)) >= 0 else -1
        tail_base_x = cx - 36 * sign
    if state == "waiting":
        tail_base_x = cx + 40
    tail_base_y = cy + 14
    tail = tail_points(tail_base_x, tail_base_y, tail_angle, 58 * (0.9 if state in {"running", "running-right", "running-left"} else 0.8), 20 + curl * 12, tail_lift)
    draw_tail(draw, tail, rgba(P["body2"]), rgba(P["outline"]), int(11 * SCALE))

    # Rear paws.
    rear_y = cy + 55
    if state in {"running-right", "running", "running-left"}:
        stride = math.sin(t)
        left_y = rear_y + (8 if stride > 0 else -2)
        right_y = rear_y + (-2 if stride > 0 else 8)
        leg_x1 = cx - 28
        leg_x2 = cx + 18
        if state == "running-left":
            leg_x1, leg_x2 = cx - 18, cx + 28
        draw.rounded_rectangle([leg_x1 - 10, left_y - 8, leg_x1 + 14, left_y + 28], radius=10, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)
        draw.rounded_rectangle([leg_x2 - 10, right_y - 8, leg_x2 + 14, right_y + 28], radius=10, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)
    elif state == "jumping":
        for lx in (-28, -8, 8, 28):
            draw.rounded_rectangle([cx + lx - 10, cy + 24, cx + lx + 10, cy + 42], radius=8, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)
    elif state == "waiting":
        draw.rounded_rectangle([cx - 34, cy + 18, cx + 20, cy + 40], radius=14, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)
    else:
        draw.rounded_rectangle([cx - 32, cy + 34, cx - 6, cy + 66], radius=11, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)
        draw.rounded_rectangle([cx + 10, cy + 34, cx + 36, cy + 66], radius=11, fill=rgba(P["body2"]), outline=rgba(P["outline"]), width=4)

    # Body and belly.
    oval([cx - body_w / 2, cy - body_h / 2 + 12, cx + body_w / 2, cy + body_h / 2 + 12], rgba(P["body"]), rgba(P["outline"]), width=5)
    if state == "waiting":
        oval([cx - body_w * 0.52, cy - body_h * 0.18 + 22, cx + body_w * 0.48, cy + body_h * 0.48 + 22], rgba(P["body2"]), rgba(P["outline"]), width=4)
    else:
        oval([cx - belly_w / 2, cy - belly_h / 2 + 20, cx + belly_w / 2, cy + belly_h / 2 + 20], rgba(P["belly"]), rgba(P["outline"]), width=4)

    # Front paws in front of body.
    if state in {"running-right", "running-left", "running"}:
        swing = math.sin(t)
        front1_y = cy + 32 + (-10 if swing > 0 else 4)
        front2_y = cy + 32 + (4 if swing > 0 else -10)
        front_x1 = cx + 30 if state != "running-left" else cx - 30
        front_x2 = cx - 12 if state != "running-left" else cx + 12
        if state == "running-left":
            front_x1, front_x2 = front_x2, front_x1
        draw.rounded_rectangle([front_x1 - 8, front1_y - 2, front_x1 + 10, front1_y + 34], radius=8, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
        draw.rounded_rectangle([front_x2 - 8, front2_y - 2, front_x2 + 10, front2_y + 34], radius=8, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
    elif state == "waving":
        draw.rounded_rectangle([cx - 46, cy + 20, cx - 18, cy + 62], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
        arm_top = cy + 20 - paw_lift * 22
        draw.rounded_rectangle([cx + 18, arm_top - 2, cx + 40, arm_top + 32], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
        draw.ellipse([cx + 18, arm_top - 6, cx + 48, arm_top + 18], fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
    elif state == "jumping":
        for lx in (-34, -14, 14, 34):
            draw.rounded_rectangle([cx + lx - 8, cy + 26, cx + lx + 8, cy + 46], radius=8, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
    elif state == "waiting":
        draw.rounded_rectangle([cx + 2, cy + 26, cx + 22, cy + 50], radius=9, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
    elif state == "review":
        draw.rounded_rectangle([cx - 38, cy + 18, cx - 16, cy + 56], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
        draw.rounded_rectangle([cx + 14, cy + 34, cx + 36, cy + 58], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
    else:
        draw.rounded_rectangle([cx - 42, cy + 34, cx - 18, cy + 68], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)
        draw.rounded_rectangle([cx + 16, cy + 34, cx + 40, cy + 68], radius=10, fill=rgba(P["cream"]), outline=rgba(P["outline"]), width=4)

    # Head.
    head_y = cy - 48 + (10 if state == "waiting" else 0) + (5 if state == "review" else 0)
    head_x = cx + (12 * math.sin(math.radians(direction)) if direction is not None else 0) + (4 if state == "running-right" else -4 if state == "running-left" else 0)
    if state == "jumping":
        head_y -= 6
    if state == "failed":
        head_y += 4
    if state == "waving":
        head_y -= 1

    # Head/back/face layer.
    if direction is not None and pose == "back":
        oval([head_x - head_w / 2, head_y - head_h / 2, head_x + head_w / 2, head_y + head_h / 2], rgba(P["body2"]), rgba(P["outline"]), width=5)
        draw.polygon([(head_x - 26, head_y - 6), (head_x - 44, head_y - 34), (head_x - 6, head_y - 24)], fill=rgba(P["body2"]), outline=rgba(P["outline"]))
        draw.polygon([(head_x + 26, head_y - 6), (head_x + 44, head_y - 34), (head_x + 6, head_y - 24)], fill=rgba(P["body2"]), outline=rgba(P["outline"]))
        draw_leaf(draw, head_x, head_y - 8, 10, rgba(P["leaf"]), rgba(P["outline"]))
    else:
        oval([head_x - head_w / 2, head_y - head_h / 2, head_x + head_w / 2, head_y + head_h / 2], rgba(P["body"]), rgba(P["outline"]), width=5)
        # Ears
        ear_drop = ears_drop
        left_ear = [(head_x - 22, head_y - 8), (head_x - 44, head_y - 34 + ear_drop), (head_x - 8, head_y - 26)]
        right_ear = [(head_x + 22, head_y - 8), (head_x + 44, head_y - 34 + ear_drop), (head_x + 8, head_y - 26)]
        draw.polygon(left_ear, fill=rgba(P["body2"]), outline=rgba(P["outline"]))
        draw.polygon(right_ear, fill=rgba(P["body2"]), outline=rgba(P["outline"]))
        draw.polygon([(head_x - 16, head_y - 14), (head_x - 32, head_y - 30 + ear_drop * 0.5), (head_x - 6, head_y - 24)], fill=rgba(P["belly"]), outline=None)
        draw.polygon([(head_x + 16, head_y - 14), (head_x + 32, head_y - 30 + ear_drop * 0.5), (head_x + 6, head_y - 24)], fill=rgba(P["belly"]), outline=None)
        draw_leaf(draw, head_x + (4 if state not in {"running-left"} else -4), head_y - 6, 10, rgba(P["leaf"]), rgba(P["outline"]))
        muzzle_w = 34 if pose != "side" else 28
        muzzle_h = 22
        if state == "review":
            muzzle_h = 18
        draw.ellipse([head_x - muzzle_w / 2, head_y + 3, head_x + muzzle_w / 2, head_y + 3 + muzzle_h], fill=rgba(P["muzzle"]), outline=rgba(P["outline"]), width=4)

        # Eyes / face.
        if eye_open <= 0.05:
            draw.line([(head_x - 16, head_y - 2), (head_x - 4, head_y + 2)], fill=rgba(P["outline"]), width=4)
            draw.line([(head_x + 4, head_y + 2), (head_x + 16, head_y - 2)], fill=rgba(P["outline"]), width=4)
        elif pose == "side-left" or pose == "side-right":
            if pose == "side-right":
                ex = head_x + 5
                draw.ellipse([ex - 8, head_y - 4, ex + 4, head_y + 8], fill=rgba(P["eye"]), outline=rgba(P["outline"]), width=3)
                draw.ellipse([ex - 5, head_y - 1, ex - 1, head_y + 3], fill=rgba(P["eye2"]), outline=None)
                draw.line([(head_x + 10, head_y + 10), (head_x + 22, head_y + 8)], fill=rgba(P["outline"]), width=3)
                draw.line([(head_x + 10, head_y + 12), (head_x + 22, head_y + 14)], fill=rgba(P["outline"]), width=3)
            else:
                ex = head_x - 5
                draw.ellipse([ex - 4, head_y - 4, ex + 8, head_y + 8], fill=rgba(P["eye"]), outline=rgba(P["outline"]), width=3)
                draw.ellipse([ex + 1, head_y - 1, ex + 5, head_y + 3], fill=rgba(P["eye2"]), outline=None)
                draw.line([(head_x - 22, head_y + 8), (head_x - 10, head_y + 10)], fill=rgba(P["outline"]), width=3)
                draw.line([(head_x - 22, head_y + 14), (head_x - 10, head_y + 12)], fill=rgba(P["outline"]), width=3)
        else:
            eye_sep = 15 + frontness * 8 if direction is not None else 16
            eye_y = head_y - 2 + (2 if state == "failed" else 0)
            for ex in (-eye_sep, eye_sep):
                draw.ellipse([head_x + ex - 6, eye_y - 6, head_x + ex + 6, eye_y + 6], fill=rgba(P["eye"]), outline=rgba(P["outline"]), width=3)
                draw.ellipse([head_x + ex - 2, eye_y - 3, head_x + ex + 1, eye_y], fill=rgba(P["eye2"]), outline=None)
            if state == "failed":
                draw.line([(head_x - 17, eye_y - 10), (head_x - 9, eye_y - 2)], fill=rgba(P["outline"]), width=3)
                draw.line([(head_x + 9, eye_y - 2), (head_x + 17, eye_y - 10)], fill=rgba(P["outline"]), width=3)
        # Nose and mouth.
        draw.polygon([(head_x, head_y + 4), (head_x - 4, head_y + 8), (head_x + 4, head_y + 8)], fill=rgba(P["nose"]), outline=rgba(P["outline"]))
        if mouth_open > 0.1:
            draw.arc([head_x - 8, head_y + 8, head_x + 8, head_y + 18], start=0, end=180, fill=rgba(P["outline"]), width=3)
        elif state == "review":
            draw.arc([head_x - 10, head_y + 9, head_x + 10, head_y + 18], start=15, end=165, fill=rgba(P["outline"]), width=3)
            draw.ellipse([head_x - 3, head_y + 10, head_x + 5, head_y + 16], fill=rgba(P["tongue"]), outline=rgba(P["outline"]), width=2)
        else:
            draw.arc([head_x - 9, head_y + 8, head_x + 9, head_y + 16], start=200, end=340, fill=rgba(P["outline"]), width=3)
            if state == "waving":
                draw.line([(head_x - 1, head_y + 11), (head_x + 1, head_y + 13)], fill=rgba(P["tongue"]), width=2)
        # Whiskers.
        if eye_open > 0.05:
            for side in (-1, 1):
                wy = head_y + 11
                draw.line([(head_x + side * 16, wy), (head_x + side * 30, wy - 4)], fill=rgba(P["outline"]), width=2)
                draw.line([(head_x + side * 16, wy + 4), (head_x + side * 30, wy + 4)], fill=rgba(P["outline"]), width=2)
                draw.line([(head_x + side * 16, wy + 8), (head_x + side * 30, wy + 12)], fill=rgba(P["outline"]), width=2)

    # Foreground decorative leaf accent.
    if state not in {"waiting"}:
        draw_leaf(draw, head_x + 3, head_y - 6, 9, rgba(P["leaf"]), rgba(P["outline"]))


def render_frame(state: str, frame: int, nframes: int, direction: float | None = None):
    img = Image.new("RGBA", (HW, HH), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw_cat(draw, state, frame, nframes, direction)
    return img.resize((CW, CH), Image.Resampling.LANCZOS)


def strip_for_state(state: str, directions=None):
    nframes = STATE_FRAMES[state]
    strip = Image.new("RGBA", (CW * nframes, CH), (0, 0, 0, 0))
    for i in range(nframes):
        direction = None
        if directions is not None:
            direction = directions[i]
        frame = render_frame(state, i, nframes, direction)
        strip.alpha_composite(frame, (i * CW, 0))
    return strip


def save_strip(state: str, img: Image.Image):
    out = DECODED / f"{state}.png"
    img.save(out)
    return out


def make_base_reference():
    base = render_frame("idle", 0, STATE_FRAMES["idle"], 180)
    ref = REFS / "canonical-base.png"
    REFS.mkdir(parents=True, exist_ok=True)
    base.save(ref)
    return ref


def make_row9_and_10():
    row9 = Image.new("RGBA", (CW * 8, CH), (0, 0, 0, 0))
    row10 = Image.new("RGBA", (CW * 8, CH), (0, 0, 0, 0))
    for i, d in enumerate(LOOKS_9):
        frame = render_frame("look-row-9", i, 8, d)
        row9.alpha_composite(frame, (i * CW, 0))
    for i, d in enumerate(LOOKS_10):
        frame = render_frame("look-row-10", i, 8, d)
        row10.alpha_composite(frame, (i * CW, 0))
    return row9, row10


def create_atlas(row_images):
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    for row_index, state in enumerate(ROW_ORDER):
        strip = row_images[state]
        atlas.alpha_composite(strip, (0, row_index * CH))
    return atlas


def make_contact_sheet(row_images):
    sheet = Image.new("RGBA", (ATLAS_W, CH * ROWS), (255, 255, 255, 0))
    font = ImageFont.load_default()
    draw = ImageDraw.Draw(sheet)
    for row_index, state in enumerate(ROW_ORDER):
        sheet.alpha_composite(row_images[state], (0, row_index * CH))
        draw.text((4, row_index * CH + 4), state, fill=rgba(P["outline"]), font=font)
    return sheet


def write_manifest(atlas_path: Path):
    manifest = {
        "spritesheetPath": atlas_path.name,
        "spritesheetLayout": {
            "columns": COLS,
            "rows": ROWS,
            "cellWidth": CW,
            "cellHeight": CH,
            "lookDirectionCount": 16,
            "neutralLookFrame": {"rowIndex": 0, "columnIndex": 6},
        },
        "lookDirections": [
            {"degrees": float(d), "rowIndex": 9 if idx < 8 else 10, "columnIndex": idx % 8}
            for idx, d in enumerate(LOOKS_9 + LOOKS_10)
        ],
        "spriteVersionNumber": 2,
        "petName": "叶喵",
        "petId": "leaf-miao",
        "displayName": "叶喵",
    }
    (OUT / "pet.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (PROJECT_PET / "pet.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_request_updates(atlas_source: Path):
    manifest_path = RUN / "imagegen-jobs.json"
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    source_rel = str(Path("final") / atlas_source.name).replace("\\", "/")
    for job in data["jobs"]:
        job["status"] = "complete"
        job["source_path"] = source_rel if job["id"] == "look-row-10" else job.get("source_path", job.get("output_path", ""))
        if job["id"] == "base":
            job["source_path"] = str(Path("references") / "canonical-base.png").replace("\\", "/")
        elif job["id"] in row_sources:
            job["source_path"] = row_sources[job["id"]]
        job["completed_at"] = now
    manifest_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


row_images = {}
row_sources = {}
base_ref = make_base_reference()

for state in ["idle", "running-right", "running-left", "waving", "jumping", "failed", "waiting", "running", "review"]:
    strip = strip_for_state(state)
    row_images[state] = strip
    decoded_path = save_strip(state, strip)
    row_sources[state] = str(Path("decoded") / decoded_path.name).replace("\\", "/")

row9, row10 = make_row9_and_10()
row_images["look-row-9"] = row9
row_images["look-row-10"] = row10
row_sources["look-row-9"] = str(Path("decoded") / "look-row-9.png").replace("\\", "/")
row_sources["look-row-10"] = str(Path("decoded") / "look-row-10.png").replace("\\", "/")
row9.save(DECODED / "look-row-9.png")
row10.save(DECODED / "look-row-10.png")

atlas = create_atlas(row_images)
atlas_png = OUT / "spritesheet.png"
atlas_webp = OUT / "spritesheet.webp"
atlas.save(atlas_png)
atlas.save(atlas_webp, format="WEBP", lossless=True, quality=100)
shutil.copy2(atlas_png, PROJECT_PET / "spritesheet.png")
shutil.copy2(atlas_webp, PROJECT_PET / "spritesheet.webp")

contact = make_contact_sheet(row_images)
contact.save(OUT / "contact-sheet.png")
contact.save(RUN / "qa" / "contact-sheet.png")

write_manifest(atlas_webp)
write_request_updates(atlas_webp)

print(json.dumps({
    "run_dir": str(RUN),
    "atlas": str(atlas_webp),
    "project_pet": str(PROJECT_PET),
}, ensure_ascii=False, indent=2))

