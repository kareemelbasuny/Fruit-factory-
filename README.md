# 🍌 Fruit Factory V1

A free-first workflow for creating short vertical fruit-story videos for YouTube Shorts and TikTok.

**V1 pipeline:** Start button / GitHub Action → bilingual story → free TTS → animated fruit scenes → burned-in subtitles → 1080×1920 MP4 → downloadable artifact.

## العربية
المشروع بيولّد فيديوهات قصص قصيرة بشخصيات فاكهة بالإنجليزي أو العربي أو اللغتين. النسخة V1 لا تستخدم API مدفوع. الشكل الحالي كرتوني متحرك ومصمم ليكون خفيفًا على GitHub Actions؛ توليد فيديو AI سينمائي كامل من prompt ليس جزءًا من المسار المجاني المضمون.

## Run from GitHub
1. Open **Actions**.
2. Choose **Generate Fruit Short**.
3. Click **Run workflow**.
4. Choose `both`, `en`, or `ar` and a theme.
5. When the run finishes, download the **fruit-shorts-...** artifact.

## Run locally
```bash
sudo apt-get install -y ffmpeg espeak-ng fonts-dejavu-core
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=src python -m fruit_factory.main --language both --theme surprise
```

## Cloudflare Start button
The `cloudflare/` folder contains a Worker dashboard with a **START / ابدأ** button. It triggers the GitHub workflow.

Required Worker secrets:
```bash
wrangler secret put GITHUB_TOKEN
wrangler secret put DASHBOARD_KEY
```
Use a GitHub fine-grained token limited to this repository with Actions write permission. Then deploy from the `cloudflare` folder with `wrangler deploy`.

## Automation
A daily cron example is included in `.github/workflows/generate-short.yml` but intentionally commented out until the first test video is approved.

## Publishing status
- **YouTube:** V1 generates a ready MP4 + caption. Direct publishing will be added after OAuth credentials are configured.
- **TikTok:** V1 generates a ready package. Direct publishing requires TikTok Content Posting API access/app approval.

## V1 goals
- [x] English
- [x] Arabic
- [x] English + Arabic in one run
- [x] Free story engine
- [x] Free TTS with local fallback
- [x] Vertical 1080×1920 renderer
- [x] Burned-in captions
- [x] GitHub Actions Start button
- [x] Cloudflare Worker dashboard code
- [ ] Automatic YouTube publishing
- [ ] Automatic TikTok publishing
- [ ] AI-generated cinematic scenes (optional later provider/local GPU path)
