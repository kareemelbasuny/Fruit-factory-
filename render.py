from __future__ import annotations
import math
import subprocess
import textwrap
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1080, 1920, 12

BG = {
    "cozy_room": ((73, 39, 28), (190, 128, 70)),
    "kitchen": ((55, 72, 68), (190, 170, 120)),
    "candy_world": ((130, 60, 140), (250, 190, 210)),
    "office": ((45, 55, 75), (160, 170, 190)),
}
FRUIT_COLORS = {
    "banana": (247, 215, 65), "apple": (214, 56, 52), "orange": (242, 142, 43),
    "strawberry": (224, 59, 91), "pear": (170, 199, 68), "grape": (117, 77, 159),
}


def _font(size: int):
    paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
    ]
    for p in paths:
        if Path(p).exists(): return ImageFont.truetype(p, size=size)
    return ImageFont.load_default()


def display_text(text: str, language: str) -> str:
    if language != "ar": return text
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display
        return get_display(arabic_reshaper.reshape(text))
    except Exception:
        return text


def gradient(draw, top, bottom):
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(top[i] * (1-t) + bottom[i] * t) for i in range(3))
        draw.line((0, y, W, y), fill=c)


def draw_character(draw: ImageDraw.ImageDraw, fruit: str, x: int, y: int, scale: float, mouth_open: bool, emotion: str):
    c = FRUIT_COLORS.get(fruit, (230, 190, 80))
    bw, bh = int(330*scale), int(500*scale)
    box = (x-bw//2, y-bh//2, x+bw//2, y+bh//2)
    draw.ellipse(box, fill=c, outline=(80, 55, 35), width=max(2,int(8*scale)))
    # stem
    draw.rounded_rectangle((x-int(25*scale), y-bh//2-int(70*scale), x+int(25*scale), y-bh//2+int(20*scale)), radius=10, fill=(91,120,46))
    # eyes
    ey = y-int(80*scale); ex = int(75*scale); er = int(38*scale)
    for cx in (x-ex, x+ex):
        draw.ellipse((cx-er, ey-er, cx+er, ey+er), fill="white")
        pr = int(14*scale)
        draw.ellipse((cx-pr, ey-pr, cx+pr, ey+pr), fill=(30,25,25))
    # eyebrows / emotion
    brow_y = ey-int(60*scale)
    tilt = int(18*scale) if emotion in {"worried","shocked"} else 0
    draw.line((x-ex-er, brow_y+tilt, x-ex+er, brow_y-tilt), fill=(70,45,30), width=max(2,int(8*scale)))
    draw.line((x+ex-er, brow_y-tilt, x+ex+er, brow_y+tilt), fill=(70,45,30), width=max(2,int(8*scale)))
    # mouth
    my = y+int(70*scale)
    if mouth_open:
        draw.ellipse((x-int(70*scale), my-int(25*scale), x+int(70*scale), my+int(65*scale)), fill=(70,25,30))
        draw.arc((x-int(55*scale), my, x+int(55*scale), my+int(50*scale)), 0, 180, fill=(255,170,175), width=max(2,int(5*scale)))
    else:
        draw.arc((x-int(80*scale), my-int(25*scale), x+int(80*scale), my+int(65*scale)), 0, 180, fill=(70,35,30), width=max(2,int(8*scale)))
    # arms / legs
    arm = int(150*scale); lw=max(2,int(12*scale))
    draw.line((x-bw//3, y+int(30*scale), x-arm, y+int(150*scale)), fill=(110,70,45), width=lw)
    draw.line((x+bw//3, y+int(30*scale), x+arm, y+int(150*scale)), fill=(110,70,45), width=lw)
    draw.line((x-int(80*scale), y+bh//2-int(30*scale), x-int(100*scale), y+bh//2+int(170*scale)), fill=(110,70,45), width=lw)
    draw.line((x+int(80*scale), y+bh//2-int(30*scale), x+int(100*scale), y+bh//2+int(170*scale)), fill=(110,70,45), width=lw)


def probe_duration(path: Path) -> float:
    r = subprocess.run(["ffprobe","-v","error","-show_entries","format=duration","-of","default=nw=1:nk=1",str(path)], capture_output=True, text=True, check=True)
    return max(1.0, float(r.stdout.strip()))


def wrap_text(text: str, language: str, width: int = 24):
    if language == "ar":
        words = text.split(); lines=[]; cur=[]
        for w in words:
            if sum(len(x)+1 for x in cur)+len(w) > width and cur:
                lines.append(" ".join(cur)); cur=[]
            cur.append(w)
        if cur: lines.append(" ".join(cur))
        return lines
    return textwrap.wrap(text, width=width)


def render_scene(scene, language: str, audio: Path, out_path: Path, work: Path):
    duration = probe_duration(audio) + 0.25
    frames = max(1, int(duration * FPS))
    frame_dir = work / (out_path.stem + "_frames"); frame_dir.mkdir(parents=True, exist_ok=True)
    title_font = _font(54); sub_font = _font(62); small_font = _font(38)
    top,bottom = BG.get(scene.background, BG["cozy_room"])
    for i in range(frames):
        im = Image.new("RGB", (W,H)); d = ImageDraw.Draw(im); gradient(d, top,bottom)
        # floor / decor
        d.rectangle((0,1450,W,H), fill=(92,59,42)); d.ellipse((80,1250,1000,1720), fill=(130,90,60))
        t=i/FPS; bob=math.sin(t*3.2)*12
        draw_character(d, scene.speaker, W//2, int(860+bob), 1.22, (i//3)%2==0, scene.emotion)
        speaker = display_text(scene.speaker.upper(), language)
        d.rounded_rectangle((90,90,990,210), radius=40, fill=(0,0,0), outline=(255,255,255), width=3)
        sb = d.textbbox((0,0), speaker, font=title_font); sw=sb[2]-sb[0]
        d.text(((W-sw)//2,120), speaker, font=title_font, fill="white")
        lines=wrap_text(scene.line, language)
        box_h = 120 + len(lines)*78
        y0=H-box_h-170
        d.rounded_rectangle((70,y0,1010,H-110), radius=48, fill=(0,0,0), outline=(255,255,255), width=4)
        y=y0+45
        for line in lines:
            txt=display_text(line, language); bb=d.textbbox((0,0),txt,font=sub_font); tw=bb[2]-bb[0]
            d.text(((W-tw)//2,y),txt,font=sub_font,fill="white"); y+=78
        d.text((40,H-70), "Fruit Factory V1", font=small_font, fill=(245,245,245))
        im.save(frame_dir / f"frame_{i:05d}.jpg", quality=90)
    subprocess.run([
        "ffmpeg","-y","-loglevel","error","-framerate",str(FPS),"-i",str(frame_dir/"frame_%05d.jpg"),
        "-i",str(audio),"-c:v","libx264","-preset","veryfast","-crf","23","-pix_fmt","yuv420p","-c:a","aac","-b:a","128k","-shortest",str(out_path)
    ], check=True)
    return out_path


def concat_videos(scene_files: list[Path], final_path: Path, work: Path):
    manifest=work/"concat.txt"
    manifest.write_text("\n".join(f"file '{p.resolve()}'" for p in scene_files), encoding="utf-8")
    subprocess.run(["ffmpeg","-y","-loglevel","error","-f","concat","-safe","0","-i",str(manifest),"-c","copy",str(final_path)], check=True)
    return final_path
