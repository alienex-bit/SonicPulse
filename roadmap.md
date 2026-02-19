# 🎵 SonicPulse Strategic Roadmap (Minecraft 1.21.4)

### Phase 1: Performance & Stability ⚙️
* **ModMenu compatibility**: Ensure the mod is fully compatible with ModMenu, including config screen integration and metadata display.
* **Idle Throttling**: Implement a "Sleep Mode" for the `AudioOutput` thread to halt FFT calculations when no music is playing or the HUD is hidden.
* **Buffer Optimization**: Refine the `line.write` logic in `AudioOutput.java` to use dynamic buffer sizing, reducing the "Audio Error" logs.
* **Resource Cleanup**: Add a shutdown hook to ensure the LavaPlayer manager and OpenAL threads are terminated when leaving a world to prevent memory leaks.
* **Stream Buffering for Slow Connections**: Add a configurable buffer amount (in seconds) for streaming audio sources so playback can tolerate high-latency or low-bandwidth networks. Expose a reasonable default (e.g., 5 seconds) and a maximum cap in config; example config key: `audioEngine.streamBufferSeconds` (default: 5).

---

### Phase 2: Audio Fidelity & UX 🎧
* **Audio Engine Config Tab**: A dedicated menu for real-time sound manipulation in the `ConfigScreen`.
* **DSP Equalizer**: Add Gain sliders for Bass (low-end) and Treble (high-end) by targeting specific frequency bins in `updateAmplitudes`.
* **Stereo Widening**: Modify the relationship between `sampleL` and `sampleR` in `AudioOutput` to create a more immersive soundstage.
* **Environmental Reverb**: Implement virtual room presets (Hall, Cave, Large Room) by adding a feedback delay loop to the PCM stream.
* **Drag-and-Drop HUD**: An interactive mode to click and drag the visualizer to any pixel-perfect position on the screen, replacing fixed presets.

---

### Phase 3: Social & Integration 🌐
* **Discord Rich Presence**: Optional integration to show song titles and artist info as your Discord status.
* **Sync-Link Sharing**: A button in the `History` tab to share a clickable URL in Minecraft chat for other SonicPulse users.
* **Server Radio**: Expand `SonicPulse.java` logic to allow server-wide broadcasting to everyone in specific areas.

---

### Phase 4: HUD Refinements 🎨
* **Color Themes & Palettes**: Define a small curated palette of themes (Dark, Light, Neon, Retro) plus a custom mode allowing per-element color selection. Add default text/accent colors and ensure color arrays and names remain synchronized.
* **Font & Typography Options**: Offer a handful of readable monospace and UI fonts, font-size scaling, and bold/outline toggles for titles. Persist user selections in config.
* **Minimal, Performant Effects**: Add subtle, optional effects such as gentle pulsing (amplitude-linked), fade-in/out for HUD show/hide, and tinting on peaks — keep effects CPU-friendly and disabled by default.
* **Background Panels & Opacity**: Provide optional semi-transparent rectangular backgrounds for grouped HUD elements, with configurable opacity and corner style (sharp/rounded) limited to low-cost visuals.
* **Contrast & Accessibility Modes**: Add High-Contrast and Color-Blind friendly palettes; include an option for reduced motion to disable animated effects.
* **Per-Element Styles & Presets**: Allow per-element overrides (color, font size, opacity) and a simple preset/save/load system so users can switch visual themes quickly.
* **Live Preview in HUD Workbench**: Integrate a live-preview mode in the HUD Workbench so users can see style changes in real time while positioning elements.
* **Performance Guardrails**: Impose safe defaults and caps (e.g., no shader-based effects, limit effect duty-cycle) and add a "Low CPU Mode" that disables most visual effects.
* **Documentation & Example Themes**: Ship a few example theme presets and document recommended combinations for visibility and performance.
* **Favourite / History Configuration** (✅ Complete): Add options to customize how favourites and history are displayed in the HUD, including sorting, filtering, and quick-access controls. Allow users to pin favourite tracks and manage history visibility and retention settings.

---

### Phase 5: Experimental 🧪
* **3D Positional Audio**: Link the audio source to a physical Jukebox block so sound pans and fades based on player distance and orientation.
* **Redstone-Sync Visuals**: Allow `amplitudes` data to trigger Redstone pulses, enabling music-reactive builds.
* **Dynamic Metadata Fetching**: Automatic title/artist labeling for raw URLs that do not provide built-in metadata.