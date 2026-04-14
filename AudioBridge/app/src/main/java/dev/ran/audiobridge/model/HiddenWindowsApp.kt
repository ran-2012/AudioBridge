package dev.ran.audiobridge.model

data class HiddenWindowsApp(
    val processName: String,
    val displayName: String,
    val iconBase64: String? = null,
)

object HiddenWindowsAppSupport {
    fun filterVisibleSessions(
        sessions: List<WindowsAppVolumeSession>,
        hiddenProcessNames: Set<String>,
    ): List<WindowsAppVolumeSession> {
        return sessions.filterNot { hiddenProcessNames.contains(it.processName.trim()) }
    }

    fun distinctSessionsByProcessName(sessions: List<WindowsAppVolumeSession>): List<WindowsAppVolumeSession> {
        val selected = linkedMapOf<String, WindowsAppVolumeSession>()
        sessions.forEach { session ->
            val processName = session.processName.trim()
            if (processName.isBlank()) {
                return@forEach
            }

            val existing = selected[processName]
            if (existing == null || isBetterSessionCandidate(session, existing)) {
                selected[processName] = session
            }
        }
        return selected.values.toList()
    }

    fun mergeHiddenWindowsApp(existing: HiddenWindowsApp?, incoming: HiddenWindowsApp): HiddenWindowsApp {
        val processName = incoming.processName.trim()
        val displayName = incoming.displayName.trim().ifBlank {
            existing?.displayName?.takeIf { it.isNotBlank() } ?: processName
        }
        val iconBase64 = incoming.iconBase64?.takeIf { it.isNotBlank() } ?: existing?.iconBase64
        return HiddenWindowsApp(
            processName = processName,
            displayName = displayName,
            iconBase64 = iconBase64,
        )
    }

    fun buildHiddenWindowsApp(session: WindowsAppVolumeSession, existing: HiddenWindowsApp? = null): HiddenWindowsApp? {
        val processName = session.processName.trim()
        if (processName.isBlank()) {
            return null
        }

        return mergeHiddenWindowsApp(
            existing = existing,
            incoming = HiddenWindowsApp(
                processName = processName,
                displayName = session.displayName.trim().ifBlank {
                    existing?.displayName?.takeIf { it.isNotBlank() } ?: processName
                },
                iconBase64 = session.iconBase64?.takeIf { it.isNotBlank() } ?: existing?.iconBase64,
            ),
        )
    }

    private fun isBetterSessionCandidate(
        candidate: WindowsAppVolumeSession,
        current: WindowsAppVolumeSession,
    ): Boolean {
        return sessionScore(candidate) > sessionScore(current)
    }

    private fun sessionScore(session: WindowsAppVolumeSession): Int {
        var score = 0
        if (!session.iconBase64.isNullOrBlank()) {
            score += 8
        }
        if (session.displayName.isNotBlank()) {
            score += 4
        }
        if (session.state.equals("Active", ignoreCase = true)) {
            score += 2
        }
        score += session.displayName.length.coerceAtMost(32)
        return score
    }
}