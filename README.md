<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# BeatCraft Workstation

A drum pad + piano roll hybrid sequencer for hobbyist producers, built with Jetpack Compose
and a custom low-latency Kotlin audio engine. This contains everything you need to run your
app locally.

View your app in AI Studio: https://ai.studio/apps/3c11a730-6c5b-415a-be71-03b37e0b9a2a

## Features

- **Drum pads + piano roll sequencer** with a shared step-sequencer clock, per-track mute,
  one-tap "duplicate previous bar" / randomize, and haptic + press-animation tactile pads.
- **Sound design**: tap the tune icon on any pad to reshape its procedural synthesis
  (tune/decay/tone) live, or swap the whole kit via a **Sound Pack**.
- **Melody assistance**: pick a key + scale on the Piano tab to highlight in-key notes, with
  an optional Scale Lock so out-of-key notes can't be placed by accident.
- **Low-latency audio engine**: `AudioTrack` runs in `PERFORMANCE_MODE_LOW_LATENCY` for tight
  pad/MIDI round-trip, alongside real Android `MidiManager` controller input.
- **Real-time export for live sets**: render the full mix to WAV, export isolated
  Drums/Synth/Vocal stems, or export a Standard MIDI File — all from the transport bar.
- **Community Library tab**: save/apply Sound Packs and synth presets, share them as JSON
  through any messaging/email app, and share rendered stems via the OS share sheet.
- **Adaptive layout**: a side `NavigationRail` replaces the bottom bar on tablets/Chromebook
  desktop windows, so the same app scales from phone to desktop workstation.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
