# AGENTS.md

## Project Overview

This repository contains a two-end audio bridge system:

- `WinAudioBridge/`: Windows desktop sender/control app built with .NET WPF
- `AudioBridge/`: Android receiver/control app built with Kotlin + Jetpack Compose
- `doc/`: project documentation, plans, status, and test strategy
- `logo/`: shared branding assets

The product streams Windows system audio to Android over an ADB-forwarded TCP connection and also lets Android view and control Windows master volume and per-application volume.

## Architecture Summary

### Windows side

Main responsibilities:

- detect Android devices through ADB
- create ADB port forwarding
- capture Windows loopback audio with NAudio
- send binary protocol packets over TCP
- expose Windows master/session volume state
- handle remote volume control commands
- auto-connect and auto-reconnect when enabled

Main project path:

- `WinAudioBridge/AudioBridge/`

### Android side

Main responsibilities:

- accept and parse protocol packets
- play PCM audio with `AudioTrack`
- render Windows volume catalog UI
- send Windows volume control requests
- keep local playback and Windows-volume state synchronized

Main project path:

- `AudioBridge/app/`

## Important Build and Test Commands

### Windows

Run from `WinAudioBridge/`:

- build: `dotnet build .\WinAudioBridge.sln /p:UseAppHost=false`
- test: `dotnet test .\WinAudioBridge.sln /p:UseAppHost=false`

### Android

Run from `AudioBridge/`:

- debug build: `.\gradlew.bat assembleDebug`
- unit tests: `.\gradlew.bat testDebugUnitTest`

## Current Testing Strategy

Use a layered approach:

1. protocol contract tests
2. local pure-logic unit tests on each side
3. a small number of real cross-end smoke tests

Avoid duplicating Windows-mocks-Android and Android-mocks-Windows tests.

Current coverage already includes:

- Windows pure-logic unit tests for settings normalization, device selection, and icon helpers
- Android unit tests for JSON codec and binary protocol parsing

See:

- `doc/测试方案.md`
- `doc/开发状态.md`

## Coding Guidelines

- Prefer minimal, targeted changes.
- Do not reformat unrelated files.
- Preserve existing public APIs unless a task explicitly requires changes.
- Keep Windows and Android protocol behavior consistent.
- When changing protocol fields, update both ends and tests together.
- Prefer pure functions and isolated logic for new testable behavior.

## Practical Notes

- Windows project namespace is still `WpfApp1` in source code even though the active project file is `AudioBridge.csproj`.
- The protocol carries both audio data and control/status messages on the same TCP connection.
- Android local unit tests use a JVM `org.json` dependency, because Android framework JSON stubs are not sufficient for host-side unit tests.
- For documentation updates, keep `doc/开发状态.md` in sync with important milestones.

## When Adding New Features

If a feature spans both ends, update in this order:

1. documentation and protocol definition
2. Windows implementation
3. Android implementation
4. unit tests on both sides
5. status/test documentation

## High-Value Next Test Targets

Recommended next additions:

- Android `PlaybackStateRepository` state merge tests
- Android ViewModel behavior tests
- Windows transport packet encode/decode tests
- Windows auto-reconnect state transition tests
- shared protocol sample fixtures
