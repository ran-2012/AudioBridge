## 1. Protocol and Documentation

- [x] 1.1 Define `AndroidPlaybackStatus` and `AndroidPlaybackStatusAck` message types on Windows and Android with matching numeric values.
- [x] 1.2 Define JSON payload models for Android playback status and Windows status ACK.
- [x] 1.3 Update protocol documentation with message directions, fields, timing, timeout behavior, and compatibility notes.

## 2. Android Playback Status Reporting

- [x] 2.1 Extend `AudioPlaybackManager` or a small status model to expose `isPlaying`, last sequence, last audio age, and best-effort latency data.
- [x] 2.2 Add a connection-scoped coroutine in `AudioBridgeService` that periodically sends Android playback status while a Windows client is connected.
- [x] 2.3 Add encoder support for status packets and parser support for Windows status ACK packets.
- [x] 2.4 Log first status, periodic summaries, ACK receipt, and abnormal playback states without flooding logs.

## 3. Windows Liveness Handling

- [x] 3.1 Add send support for `AndroidPlaybackStatusAck` in `AudioTransportService`.
- [x] 3.2 Handle `AndroidPlaybackStatus` in `StreamingCoordinator`, parse payload, update a dedicated playback-status timestamp, and send ACK.
- [x] 3.3 Add pure liveness helpers for status timeout and consecutive playback abnormality detection.
- [x] 3.4 Integrate status timeout detection with the existing fault and automatic reconnect path.
- [x] 3.5 Ensure status timeout only triggers reconnect when a device is currently connected, being prepared, or eligible through existing device monitoring state.

## 4. Manual Audio Restart

- [x] 4.1 Add a `RestartAudioAsync` or equivalent public method on `StreamingCoordinator` that reuses the lifecycle lock and existing stop/start core flow.
- [x] 4.2 Add a “重启音频” item to the Windows tray right-click menu.
- [x] 4.3 Wire the tray menu callback in `App.xaml.cs` so it triggers restart asynchronously and logs the manual action.
- [x] 4.4 Verify existing tray entries for main window, settings, and exit keep their current behavior.

## 5. Tests

- [x] 5.1 Add Windows unit tests for status timeout helper behavior, including no timeout before first status, timeout after threshold, and no refresh from outbound-only activity.
- [x] 5.2 Add Windows unit tests for consecutive abnormal playback status detection.
- [x] 5.3 Add Windows tests for tray restart callback wiring or coordinator restart lifecycle behavior.
- [x] 5.4 Add Android JVM tests for status payload encoding and ACK packet parsing.
- [x] 5.5 Add Android tests for playback status snapshot generation from playback manager state where feasible.

## 6. Validation

- [x] 6.1 Run Windows tests with `dotnet test .\WinAudioBridge.sln /p:UseAppHost=false` from `WinAudioBridge/`.
- [x] 6.2 Run Android unit tests with `.\gradlew.bat testDebugUnitTest` from `AudioBridge/`.
- [ ] 6.3 Perform a manual smoke test: start both ends, disconnect/kill Android receiver, verify Windows detects missing status and reconnects.
- [ ] 6.4 Perform a manual smoke test: use the tray “重启音频” action during playback and verify audio resumes.
- [x] 6.5 Update `doc/开发状态.md` and `doc/测试方案.md` with the completed behavior and validation results.