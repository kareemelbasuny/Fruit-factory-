from __future__ import annotations
import argparse, json, datetime
from pathlib import Path
from .story import generate_story
from .tts import synthesize
from .render import render_scene, concat_videos


def build_one(language: str, theme: str, seed: int | None, root: Path):
    story = generate_story(language, theme, seed)
    lang_name = "arabic" if language == "ar" else "english"
    job = root / lang_name; audio_dir=job/"audio"; scene_dir=job/"scenes"; work=job/"work"
    for p in (audio_dir,scene_dir,work): p.mkdir(parents=True, exist_ok=True)
    (job/"story.json").write_text(json.dumps(story.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
    (job/"caption.txt").write_text(story.caption + "\n" + " ".join(story.hashtags), encoding="utf-8")
    clips=[]
    for idx, scene in enumerate(story.scenes, start=1):
        audio=synthesize(scene.line, language, audio_dir/f"{idx:02d}.mp3")
        clip=render_scene(scene, language, audio, scene_dir/f"{idx:02d}.mp4", work)
        clips.append(clip)
    final=job/f"fruit_short_{lang_name}.mp4"
    concat_videos(clips, final, work)
    return final


def main():
    ap=argparse.ArgumentParser(description="Generate bilingual fruit-story vertical shorts for free.")
    ap.add_argument("--language", choices=["en","ar","both"], default="both")
    ap.add_argument("--theme", choices=["surprise","funny"], default="surprise")
    ap.add_argument("--seed", type=int, default=None)
    ap.add_argument("--output", default="output")
    args=ap.parse_args()
    stamp=datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    root=Path(args.output)/stamp; root.mkdir(parents=True, exist_ok=True)
    langs=["en","ar"] if args.language=="both" else [args.language]
    outputs=[str(build_one(lang,args.theme,args.seed,root)) for lang in langs]
    print(json.dumps({"ok":True,"outputs":outputs}, ensure_ascii=False))

if __name__ == "__main__": main()
