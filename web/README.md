# BeatCraft Web

A touchscreen-first drum pad + piano roll hybrid sequencer that runs entirely in the
browser — no backend, no account, no app store. It's a web companion to the Android
app in this repo: the sequencing model, sound-pack format, and MIDI export are all
ported 1:1 from the Kotlin `AudioEngine`/`MidiFileExporter`, re-implemented on top of
the Web Audio API so it can be hosted as a static site (e.g. on Vercel or Cloudflare
Pages) and played on any phone, tablet, or desktop with a modern browser.

## What's here vs. the Android app

Ported: drum pads with live sound design (tune/decay/tone), the step sequencer
(mute/randomize/duplicate-bar), the piano roll with key/scale highlighting + Scale
Lock, WAV/stem/MIDI export, and the Sound Pack + Synth Preset community library
(saved to `localStorage`, shared as copyable JSON text).

Intentionally left out (browser platform constraints or fake/simulated features in
the original): microphone vocal recording, the VST FX rack, and the
Bluetooth/USB/"cloud sync" panel. A "Project Stems" library listing past exports
isn't included either, since browsers don't expose a way to list previously
downloaded files.

## Run locally

```bash
npm install
npm run dev       # http://localhost:5173, hot reload
npm run build     # type-checks + produces dist/
npm run preview   # serve the production build locally
```

Requires a browser with the Web Audio API (all evergreen browsers). Audio only
starts after a user gesture (tap a pad or press play) — that's a browser autoplay
restriction, not a bug.

## Deploy

The app is a static site (Vite build output in `dist/`) — either platform below is a
zero-config deploy once you point it at this `web/` subdirectory.

### Vercel

1. On [vercel.com](https://vercel.com), **Add New → Project**, import this GitHub repo.
2. Set **Root Directory** to `web`.
3. Framework preset: Vite (auto-detected). Build command `npm run build`, output
   directory `dist` (already declared in `vercel.json`).
4. Deploy. Every push to the connected branch redeploys automatically.

Or from the CLI (requires `vercel login` once):

```bash
cd web
npx vercel --prod
```

### Cloudflare Pages

1. On the [Cloudflare dashboard](https://dash.cloudflare.com) → **Workers & Pages →
   Create → Pages → Connect to Git**, pick this repo.
2. Set **Root directory** to `web`, build command `npm run build`, build output
   directory `dist`.
3. Save and deploy. Every push redeploys automatically.

Or from the CLI (requires `wrangler login` once):

```bash
cd web
npm run build
npx wrangler pages deploy dist --project-name=beatcraft-web
```

Both platforms' free tiers are sufficient for this app (static assets only, no
server-side function calls).
