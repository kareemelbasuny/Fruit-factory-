# RAAD AI — Your Personal Intelligence System

**RAAD (رعد)** is a zero-subscription, offline-first, Android-friendly personal command center. The interface is English; the assistant defaults to Egyptian Arabic. This repository contains an actual installable Progressive Web App, optional cloud backup, and an optional authenticated AI gateway.

## Free-first design

The core app is **100% static HTML/CSS/JavaScript**, with zero mandatory npm dependencies, subscriptions, or paid AI keys. It was chosen instead of React/Vite for this first version because it can be uploaded directly to free static hosting, runs offline, and does not require a build server. `npm run build` is optional and copies the app into `dist/`. **No vendor can promise that optional cloud services or AI free-tier quotas will remain free forever.**

## Features

| Module | Actual behavior |
|---|---|
| Overview | Saved task/project totals, upcoming schedule, per-currency ledger totals, daily briefing |
| Tasks | Create, edit, delete, complete, priorities, dates, project links, search and filters |
| Calendar | Month, week and day views; create/edit events; task deadlines |
| Projects | Separate workspaces, progress, tasks, milestones, associated clients, finances |
| Business | Revenue/expenses, clients, project filters; net by one actual recorded currency at a time |
| Income Opportunities | Capture research, estimated costs/revenue scenarios, sources and risks; convert to a project |
| Investments | EGX/US/ETF/gold watchlist and manual holdings, cost/value/gain; no fictional price feed |
| Automations | Locally scheduled briefings/reviews while app remains open; run history and notification request |
| Goals & Habits | Goals, percent progress, daily habit check-ins |
| RAAD Assistant | Egyptian Arabic/English local commands, conversation history, confirmation for local task creation, optional AI gateway |
| Voice | Browser speech recognition and text-to-speech when supported |
| Settings | Preferences, export/import, clear data, cloud configuration, voice and notifications |
| PWA | Android install prompt (browser-controlled), offline service worker cache, 192/512 icons |

**No sample metrics or fake stock prices are inserted.** On first launch data starts empty.

## How it is built

```
index.html                App shell
src/app.js                Navigation, screens, forms, voice, automation, workflows
src/db.js                 Versioned IndexedDB object stores + snapshot export/import
src/ai.js                 Defined local commands + optional authenticated AI adapter
src/cloud.js              Optional Supabase Auth and manual snapshot sync
src/utils.js              Validation, summaries and date/currency helpers
src/styles.css            Responsive Android-first UI
sw.js                     Offline caching for app assets
manifest.webmanifest      PWA manifest
icons/                    Original RAAD artwork/icons
server/server.mjs         Optional server-side Gemini adapter (NOT on by default)
supabase/schema.sql       Optional cloud RLS schema
.github/workflows/pages.yml Free-compatible GitHub Pages build & deploy automation
tests/core.test.mjs       Built-in automated logic tests
tools/build.mjs           Dependency-free static build
```

## Launch on Android — no terminal, no desktop

1. Get the provided **RAAD-AI.zip** on your phone.
2. Publish the *static site files* to a free static host using its upload/dashboard UI. Recommended: **Cloudflare Pages** direct upload (check current availability and terms) or **Netlify Drop**. Upload the unzipped site directory, or an accepted ZIP if the host allows ZIP uploads. No build command is required. The root `index.html`, `src`, `icons`, `sw.js`, and `manifest.webmanifest` must all be deployed together.
3. Open the resulting **verified HTTPS address** in Chrome on Android. From Chrome's menu, choose **Add to Home screen** or **Install app** (wording varies).
4. Open RAAD from your home screen. All basic modules work without signing in. For offline use, open it online at least once so the service worker can cache assets.

> Important: Unpacking the files and opening `index.html` as a `file://` link is **not** equivalent to hosting a PWA: service workers and secure browser features need an HTTPS/localhost origin.

## Local data and privacy

- Records are saved in this browser's **IndexedDB**. They survive ordinary reloads and supported browser restarts, but **do not transfer to other devices** and may be erased when site data is cleared.
- Export a complete JSON backup in **Settings → Export backup**; import on another device through **Settings → Import backup**. An import replaces all local records after confirmation.
- **Erase local data** requires two separate confirmations.
- No account, password, or provider API key is needed for local mode.
- Local conversation history is stored on the device. The optional live AI connector sends a limited context of tasks/projects/events/goals only when you submit a message. Avoid entering secrets in messages.
- Any cloud project URLs and public anon/publishable keys are stored locally. Supabase JWT access tokens exist only in memory for the current session. Never paste service-role keys or Gemini keys into the browser.

## Optional Supabase cloud backup (manual)

1. Create your own Supabase project on its free tier (subject to provider's limits).
2. Enable password-based email sign-in under Supabase Auth.
3. Paste and run `supabase/schema.sql` in your project's SQL editor. It enables Row Level Security and owner-only policies for `user_snapshots`.
4. In RAAD Settings, enter the project URL and **public anon key**, then sign in with your Supabase Auth account.
5. Use **Upload backup** to push the entire local dataset; use **Restore backup** to replace the local dataset with your saved cloud snapshot.

This is **manual backup/restore**, not ongoing real-time automatic synchronization. Do not use it for concurrent editing across devices. No cloud account is configured by default.

## Optional real AI (server-side)

- The app does **not** claim a model is connected out of the box.
- `server/server.mjs` is an optional Node.js AI gateway using the Gemini API. Configure the secret `GEMINI_API_KEY` **only on your backend**, along with `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `ALLOWED_ORIGIN`. Never commit your `.env` file.
- The gateway validates Supabase user tokens server-side, enforces same-origin CORS, limits requests per user, validates payload size, and gives no write permissions or account access to the model.
- Deploy this gateway on a backend service of your choosing, configure its HTTPS `/api/chat` URL under RAAD Settings, and sign into cloud first. A configured URL is not necessarily reachable: a live request is the only evidence of a successful connection.
- Using a Gemini API free tier, if available for your region/account and usage, may avoid per-request charges. **Check limits, rate limits and billing requirements before enabling; no free unlimited AI is promised.**
- Existing static hosting cannot safely host secret-bearing provider API keys by itself.

## Automations and background limitations

Configured Active workflows are checked when the app opens and while the app is visible (once per minute). They create *local* log records and may show browser notifications if permission is granted. They **do not** run in Android background when the tab/browser is closed; service workers here do not provide push scheduling. No real-time stock alerts, financial trades, external emails, or calendar writes are attempted. These require separately deployed, authenticated background infrastructure and explicit user permission.

## Optional developer checks

This is for people maintaining the project; Android users do not need to run commands.

```
npm test
npm run build
npm run serve
```

Node 20+ is sufficient. `npm run build` writes a distributable static folder to `dist/` and needs no package install. A GitHub Pages Actions workflow is included; turn on **Settings → Pages → Source: GitHub Actions** if you use a public repository.

## Security practices and limits

- All app record text is escaped before being added to HTML.
- Important deletes and local assistant-initiated task creation require confirmation.
- Untrusted web/market data is not auto-executed; live AI is reply-only in this version.
- Supabase RLS restricts cloud rows to authenticated owners, **if properly configured**.
- AI backend tokens and model credentials are kept off the frontend; the gateway must use HTTPS in production.
- Browser local data does **not** have strong at-rest encryption. Do not save bank credentials, passwords, or sensitive legal documents.
- No full multi-device cloud reconciliation, multi-agent tool calling, calendar sync, third-party messaging, or unrestricted background agents are included yet.
- Voice relies on browser capabilities (including language/permission support) and is not continuous wake-word listening.
- PWA installation depends on browser and hosting support. An Android APK builder is included, but no APK is claimed until GitHub Actions succeeds.
- No public URL exists until you deploy it to a host you control.

## Next roadmap

1. Host the static PWA over HTTPS and verify Android install/offline behavior.
2. Add authenticated AI via a free-eligible backend with server-managed secrets.
3. Upgrade from manual cloud snapshots to conflict-aware cloud sync.
4. Add user-approved integration adapters (Google Calendar, email, research APIs).
5. Deploy secure background automation workers with explicit schedules and logging.
6. Introduce multi-agent orchestration, advanced risk monitoring and optional Capacitor APK packaging.

RAAD branding and icons are original. No J.A.R.V.I.S / Iron Man material is included.

## Android APK build (NEW)

An Android WebView project is bundled under `android/`, with custom native speech
recognition integration (uses the installed Android speech recognition activity
if available), Android text-to-speech, and system-document-picker JSON backup
export/import. The native application loads the RAAD UI directly from its own
APK using an internal virtual HTTPS origin `https://app.raad.local`, not a
public website. This allows offline IndexedDB storage. No external hosting,
Gemini key, subscription, or Supabase account is required for the core app.

The root project's `npm run package:android` bundles the static site under
`android/app/src/main/assets/www/` and copies the original RAAD icon.
The included `.github/workflows/android-apk.yml` can compile the APK using
GitHub Actions and publish `RAAD-AI-Android-APK` as an Actions artifact.
This is a **debug-signed** Android build intended for direct installation on
personal Android devices; it is not Play Store release-signed.

**Free-first:** public GitHub repositories have free GitHub-hosted Actions
usage subject to GitHub policy and abuse limits. Private repositories can
have account-specific limits and charges; prefer a public repository that
contains no private data, or check usage settings before starting CI.
No passwords or credentials are included. The project's Android app remains
local-only until you explicitly configure optional connections.

On an Android phone, open the GitHub repository's **Actions** tab, choose
**Build RAAD Android APK (Free)**, open the successful run, download the
`RAAD-AI-Android-APK` artifact ZIP, extract it and install `app-debug.apk`.
Only install an APK built from your own trusted repository and review the
Android installation prompt. Do not turn off device security protections
system-wide. On modern Android, allowing APK installs from your chosen Files
app may be required.

This environment lacks the Android SDK and has no direct access to the
Android distribution servers, so an APK is **not locally built/verified** until
a CI run reports success. The source, app packaging step, CI workflow and
JavaScript unit tests are provided even if a CI service is unavailable.

## RAAD OS 2.0 — official ChatGPT plan sign-in (Android)

The new native Android client optionally supports **Sign in with ChatGPT** for personal and open-source use. It creates a device-specific host ID, starts OAuth 2.0 with PKCE in the system browser, uses a loopback callback, verifies the OpenAI ID token against published JWKS, requires the granted `chatgpt.tokens.use.direct` permission, and stores OAuth access/refresh tokens encrypted with Android Keystore AES-GCM. No ChatGPT password, cookie, provider API key, or token is saved in the web UI or browser IndexedDB. Inference uses the officially documented Responses API with the OAuth token, `store: false`, `stream: true`, and an account-available model. Refresh token rotation and a disconnect control are included.

**Activation needs you to sign in on the phone yourself.** The development environment cannot test with your private ChatGPT account. OpenAI controls availability and model/usage allowances based on your plan, region, permission and rate limits. This app does not bypass ChatGPT subscription requirements, grant conversation history/memory access, or promise uninterrupted AI availability. If account-sharing is unavailable, RAAD's offline modules still work; the `Continue in ChatGPT app` action explicitly copies a selected prompt to your clipboard and opens the official ChatGPT experience so you can paste/send it yourself. This is a separate app, not embedded inference.

The APK is built using the free-tier GitHub Actions debug-signed Android workflow. RAAD 2 installs side-by-side under the new Android package `ai.raad.personal.v2`, to avoid conflicts with the old APK's debug signature. Your old app's data is not automatically transferred. **Export your old app's JSON backup, then import it into RAAD 2**, before deciding whether to uninstall the old APK.

Official docs: https://developers.openai.com/siwc/token-sharing-open-source/ and https://developers.openai.com/siwc/token-sharing-open-source/sign-in .


## Android app launcher & media searches (no subscription)

On Android, RAAD understands Egyptian Arabic and English commands such as:

- `يا رعد افتح اليوتيوب` — opens the YouTube app if available; falls back to a browser.
- `شغل لي أغنية عمرو دياب من على اليوتيوب` — opens YouTube search results for that song/artist.
- `شغل أغنية انت عمري من على الساوند كلاود` — opens SoundCloud search results.
- `اعمل سيرش عن أخبار البورصة المصرية` — launches a Google search.
- `search YouTube for AI business tools` and `open SoundCloud` also work.

The commands are parsed and validated locally. All outgoing links are generated by Android from a fixed YouTube, SoundCloud, or Google HTTPS destination; model output cannot provide arbitrary URLs. A confirmation dialog appears before leaving RAAD. **RAAD does not bypass the other app's controls or automatically start a specific audio track**; tap a search result and Play. These actions require connectivity and an appropriate installed app or browser, even though RAAD's local database works offline. Wake Mode may capture a voice command, but responding to the wake phrase while the screen is locked is an experimental 5-minute foreground microphone session, not guaranteed always-on listening.