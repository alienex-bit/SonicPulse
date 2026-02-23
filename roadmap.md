# 🎵 SonicPulse Strategic Roadmap (Minecraft 1.21.4)

## 🎯 Project Vision
SonicPulse aims to be the most performant and aesthetically versatile media HUD for the Fabric ecosystem, providing professional-grade audio visualization and stream management without impacting game stability.

---

## ⚙️ Phase 1: Performance & Stability
*Focusing on resource efficiency, thread safety, and seamless integration.*

- [x] **ModMenu Compatibility**: Metadata display and configuration screen integration.
- [x] **Idle Throttling**: FFT "Sleep Mode" for `AudioOutput` when music is stopped or the HUD is hidden.
- [x] **Buffer Optimization**: Implementation of dynamic buffer sizing in `line.write` to eliminate audio error logs.
- [x] **Resource Cleanup**: Critical shutdown hooks for LavaPlayer and OpenAL to prevent memory leaks.
- [x] **Stream Buffering**: Configurable latency buffer for high-latency or low-bandwidth networks.

---

## 🎧 Phase 2: Audio Fidelity & UX
*Enhancing the auditory experience and user control interfaces.*

- [x] **Audio Engine Config Tab**: Dedicated sub-menu for real-time engine manipulation.
- [x] **DSP Equalizer**: Frequency-specific gain sliders for Bass and Treble.
- [x] **Stereo Widening**: Immersive soundstage modification logic.
- [ ] **Environmental Reverb**: Virtual room presets including Hall, Cave, and Large Room.
- [x] **Underwater Muffling**: When underwater option to muffle audio playback.
---

## 🌐 Phase 3: Social & Integration
*Connecting SonicPulse with external platforms and multiplayer environments.*

- [ ] **Discord Rich Presence**: Optional status integration for track titles and artist info.
- [ ] **Sync-Link Sharing**: Quick-share clickable URLs in Minecraft chat from the History tab.
- [ ] **Server Radio**: Logic expansion for localized server-wide broadcasting.

---

## 🎨 Phase 4: HUD Refinements
*Deep customization and advanced visual styling options.*

- [x] **Color Themes & Palettes**: Curated presets (Neon, Retro, Cyber) and custom element modes.
- [x] **Typography Options**: Selectable monospace/UI fonts with bold and outline toggles.
- [x] **Minimal Effects**: CPU-friendly pulsing and peak-tinting visual effects.
- [x] **Background Panels**: Semi-transparent rectangular panels with configurable opacity.
- [x] **Accessibility Modes**: High-contrast palettes and reduced motion options.
- [x] **Live Preview**: Real-time workbench preview for style modifications.
- [x] **Performance Guardrails**: "Low CPU Mode" and safe visual defaults.
- [x] **Favourite / History Config**: Quick-access, sorting, and retention controls.

---

## 🧪 Phase 5: Experimental
*Cutting-edge features and physical world interactions.*

- [ ] **3D Positional Audio**: Distance-based fading and panning linked to Jukebox blocks.
- [ ] **Redstone-Sync Visuals**: Music-reactive Redstone triggers based on amplitude data.
- [ ] **Dynamic Metadata Fetching**: Automatic labeling for raw URLs lacking metadata.

---
*Last Updated: February 2026*
