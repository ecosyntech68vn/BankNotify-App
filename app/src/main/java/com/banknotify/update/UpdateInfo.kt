package com.banknotify.update

data class UpdateInfo(
    val latestVersion: String,
    val latestBuild: Int,
    val minVersion: String,
    val minBuild: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseDate: String,
    val mandatory: Boolean = false,
    val checksumSha256: String? = null,
    val fileSize: Long? = null
) {
    fun isNewerThan(currentVersion: String, currentBuild: Int): Boolean {
        if (latestBuild > currentBuild) return true
        return compareVersions(latestVersion, currentVersion) > 0
    }

    fun isMandatoryUpgrade(currentVersion: String, currentBuild: Int): Boolean {
        if (mandatory) return true
        if (minBuild > 0 && currentBuild < minBuild) return true
        return compareVersions(minVersion, currentVersion) > 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val diff = (parts1.getOrElse(i) { 0 }) - (parts2.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}

data class UpdateCheckRequest(
    val currentVersion: String,
    val currentBuild: Int,
    val packageName: String,
    val deviceInfo: Map<String, String> = emptyMap()
)

data class UpdateCheckResponse(
    val hasUpdate: Boolean,
    val updateInfo: UpdateInfo? = null,
    val checkTimestamp: Long = System.currentTimeMillis()
)
