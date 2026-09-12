## ADDED Requirements

### Requirement: Responsive primary layout
The Android application SHALL present the primary audio controls in layouts tailored to phone portrait and tablet landscape form factors without changing their underlying behavior.

#### Scenario: Phone portrait layout
- **WHEN** the application is displayed on a phone-sized portrait viewport
- **THEN** connection status, Windows master volume, local playback controls, and application sessions are arranged in one vertically scrollable flow

#### Scenario: Tablet landscape layout
- **WHEN** the application is displayed on a tablet-sized landscape viewport
- **THEN** primary controls and application sessions are arranged in separate columns that use the available width without stretching individual controls across the full screen

### Requirement: Distinct volume domains
The Android application SHALL visually distinguish Windows volume controls from Android local playback controls.

#### Scenario: Windows master volume is displayed
- **WHEN** the main page displays Windows master volume
- **THEN** the control is labeled as a Windows control and includes the current value and mute action

#### Scenario: Local playback controls are displayed
- **WHEN** the main page displays Android playback volume and buffer settings
- **THEN** the controls are grouped under a local-device section that cannot be confused with Windows volume controls

### Requirement: Scannable application sessions
The Android application SHALL present each visible Windows application session as a compact, independently operable item.

#### Scenario: Session list is populated
- **WHEN** one or more visible Windows application sessions are available
- **THEN** each item displays the application identity, process identity, current volume, volume control, and mute state without nesting a card inside another card

#### Scenario: Session identity is long
- **WHEN** an application display name or process name exceeds the available width
- **THEN** the text is truncated without resizing or overlapping the volume and mute controls

#### Scenario: Session count exceeds the viewport
- **WHEN** the number of visible sessions exceeds the available screen area
- **THEN** the session region remains scrollable and all sessions remain reachable

### Requirement: Explicit runtime states
The Android application SHALL provide distinct visual and textual feedback for connection, playback, synchronization, and application-session states.

#### Scenario: Connection state changes
- **WHEN** the service is stopped, connecting, connected, playing, or disconnected
- **THEN** the current state is communicated by text and a supporting visual treatment rather than color alone

#### Scenario: Sessions are loading
- **WHEN** a Windows volume catalog refresh is in progress
- **THEN** the session region displays a loading state without presenting stale content as current

#### Scenario: No sessions are available
- **WHEN** synchronization succeeds but no visible Windows audio sessions exist
- **THEN** the session region displays an empty-state message and a refresh action

#### Scenario: Session synchronization fails
- **WHEN** the Windows volume catalog cannot be synchronized
- **THEN** the session region displays an error state with a recovery action

### Requirement: Structured settings and diagnostics
The Android application SHALL organize low-frequency settings and diagnostics separately from the primary volume workflow.

#### Scenario: Settings page is opened
- **WHEN** the user opens the settings and diagnostics page
- **THEN** lock-screen stability, runtime controls, connection methods, current session parameters, logs, and application filtering are grouped into identifiable sections

#### Scenario: Tablet settings page is displayed
- **WHEN** the settings and diagnostics page is displayed on a tablet-sized landscape viewport
- **THEN** independent sections use a multi-column arrangement while logs may span the available content width

#### Scenario: Diagnostic content is extensive
- **WHEN** logs or application-filter entries exceed the available page height
- **THEN** the page remains scrollable without obscuring its close or navigation action

### Requirement: Preserved typography and behavior
The Android application SHALL retain the existing Android and Material typography system and SHALL preserve existing audio, connection, volume, cache, and filtering behavior during the UI refinement.

#### Scenario: Refined UI is rendered
- **WHEN** the new theme and layouts are active
- **THEN** no custom font family is introduced and the HTML reference's serif and monospace font choices are not copied into the Android application

#### Scenario: Existing action is used
- **WHEN** the user reconnects, changes a volume, toggles mute, changes playback cache, selects a connection method, starts or stops the service, or filters an application
- **THEN** the action invokes the same existing callback and preserves its current state semantics

### Requirement: Accessible controls
The Android application SHALL keep interactive controls readable, reachable, and identifiable across supported layouts.

#### Scenario: Icon-only action is displayed
- **WHEN** an action is represented only by an icon
- **THEN** the action provides an accessibility description that names its purpose

#### Scenario: Touch control is displayed
- **WHEN** a button, slider, or icon action is available
- **THEN** it retains a touch target suitable for Android interaction and does not overlap adjacent content
