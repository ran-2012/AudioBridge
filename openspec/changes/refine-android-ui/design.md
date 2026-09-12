## Context

The Android client uses Jetpack Compose and Material 3. Its main page currently combines connection state, Windows master volume, local playback settings, and per-application sessions. The secondary page combines service controls, connection setup, session parameters, logs, battery guidance, and application filtering.

The current phone layout is a vertical stack. The tablet landscape layout uses a full-width master card followed by tall horizontal session cards. True-device screenshots show weak visual hierarchy, oversized controls, unused horizontal space, and insufficient separation between Windows controls and Android-local controls.

The reference assets are:

- `doc/app-ui-refine/audiobridge-responsive.html`
- `doc/app-ui-refine/audiobridge-responsive.png`
- `doc/app-ui-refine/01-main.png`
- `doc/app-ui-refine/02-settings-details.png`
- `doc/app-ui-refine/03-settings-lower.png`

The HTML is a design reference rather than an implementation source. Its information architecture, spacing, state treatment, and responsive composition are applicable to Compose. Its simulated device chrome, English copy, web-specific interaction code, serif display font, and monospace numeric font are not implementation requirements.

## Goals / Non-Goals

**Goals:**

- Give connection state and high-frequency audio controls a clear hierarchy.
- Separate Windows master volume from Android-local playback settings.
- Use a single-column phone composition and a true two-column tablet landscape composition.
- Make application sessions compact, scannable, independently controllable, and safe for long names and large counts.
- Structure settings and diagnostics for both narrow and wide screens.
- Express loading, empty, error, disconnected, connected, and playing states with text and visual cues.
- Preserve existing callbacks, state models, localization approach, Material typography, and business behavior.

**Non-Goals:**

- Changing TCP protocol messages, connection behavior, audio playback, foreground-service behavior, persistence, or Windows-side logic.
- Introducing a navigation framework, new state-management layer, or external UI dependency.
- Introducing custom fonts or reproducing the HTML reference's serif and monospace typography.
- Reproducing browser animations, device frames, status bars, or decorative web effects.
- Renaming existing user-facing concepts unless needed to clarify Windows versus local controls.

## Decisions

### Keep the current screen and callback boundaries

`AudioBridgeScreen` remains responsible for switching between the main and details pages. `MainPageScreen` and `DetailsPageScreen` continue to consume `PlaybackUiState` and existing callbacks.

This avoids moving business behavior into new UI abstractions and keeps the change limited to Compose presentation. A new navigation framework was considered but rejected because the application has only two in-memory destinations and no deep-link requirement.

### Use window-size behavior local to the UI

The phone layout remains a vertically scrollable composition. Tablet landscape uses two content columns: a narrower control column for connection, Windows master volume, and local playback; and a wider session column for application controls. Settings use one column on narrow screens and two columns on wide landscape screens, with logs allowed to span both columns.

The existing configuration-based breakpoint may be retained or replaced by a small UI-local width classification. A project-wide adaptive-layout abstraction is unnecessary for two pages.

### Build a restrained Material control surface

The visual system uses Material color roles with explicit surface separation, borders, state colors, spacing, and shapes. Cards are reserved for coherent control groups and individual repeated session items; page sections are not nested inside decorative cards.

Dynamic color SHALL not be allowed to erase state contrast or hierarchy. The implementation may use a stable application color scheme or map dynamic colors into tested semantic roles. A direct copy of the HTML's OKLCH values is rejected because Compose theme colors must work with Android light/dark behavior and existing Material components.

### Preserve Material typography

All text continues to use the existing `MaterialTheme.typography` and platform font family. Hierarchy is created through existing Material styles, weight, size, color, and spacing.

The HTML's serif headings and monospace values are intentionally excluded. This satisfies the explicit requirement not to change fonts and avoids packaging font assets or creating inconsistent Chinese fallback behavior.

### Keep continuous settings continuous

Windows master volume, application volume, and Android local playback volume remain sliders because they represent continuous values. Playback cache remains compatible with its full supported range and existing callback. The UI may add clearly labeled presets only if the continuous control remains available or all supported values remain reachable.

The HTML's three-option buffer control is not copied as the sole control because the current application supports values from `40ms` to `400ms`.

### Derive all visual states from existing state

Connection, playback, synchronization, errors, session loading, and empty-session presentation derive from `PlaybackUiState`. No parallel UI state model is introduced for server truth.

Temporary interaction state, such as the currently visible page, remains local Compose state. Error and empty views expose existing reconnect or refresh callbacks rather than adding new service commands.

### Validate with previews, tests, and real-device screenshots

Representative previews or UI tests cover phone portrait, tablet landscape, populated sessions, long labels, loading, empty, error, muted, and disconnected states. The final implementation is built and installed on the connected Android device, then compared against the baseline and reference images.

Screenshot validation checks hierarchy, clipping, overlap, scrolling, touch-target spacing, and retention of the existing font system. Pixel-perfect matching to the HTML is not required.

## Risks / Trade-offs

- [Dynamic color produces inconsistent hierarchy across devices] → Define and test semantic surface, border, success, warning, and error roles; disable dynamic color if those guarantees cannot be maintained.
- [Tablet layouts work on one device but fail at intermediate widths] → Base composition on available width and validate at phone, compact landscape, and tablet landscape sizes.
- [Compact session items reduce touchability] → Keep Material controls and minimum touch targets even when visible card padding is reduced.
- [Long application labels push controls off-screen] → Give identity text a bounded weighted region with ellipsis and keep numeric/mute controls in stable-width regions.
- [Frequent slider updates cause excessive recomposition or command traffic] → Reuse current callbacks and behavior; do not add additional state mirroring in the UI.
- [Reference design implies unsupported behavior] → Treat the current application code and this spec as the behavior source of truth; use the HTML only for layout and visual direction.
- [UI refinement accidentally changes fonts] → Do not add font resources or `FontFamily` declarations; include typography verification in review and screenshot checks.

## Migration Plan

1. Add representative Compose previews or fixtures for the required layouts and states.
2. Refine semantic colors, surfaces, shapes, and spacing while preserving `MaterialTheme.typography`.
3. Recompose the main page for phone and tablet layouts using existing state and callbacks.
4. Recompose the settings and diagnostics page for narrow and wide layouts.
5. Add or update focused Compose UI tests for state labels, controls, and responsive visibility.
6. Build and run Android unit/UI checks.
7. Install on the connected device and capture phone/tablet-orientation screenshots for comparison.

Rollback consists of reverting the Compose UI and theme changes. No data or protocol migration is required.

## Open Questions

- Whether dynamic color can meet the required hierarchy on the target devices or should be disabled in favor of a stable AudioBridge palette.
- Whether playback-cache presets should supplement the existing slider after usability validation; the supported range must remain reachable.
- Whether application filtering remains entirely on the diagnostics page or gains a lightweight entry point from the session list.