package tech.future.sleepanalyzer.sync

import android.content.Context
import org.json.JSONObject
import tech.future.sleepanalyzer.data.db.AppDatabase
import tech.future.sleepanalyzer.data.db.BackupDatabaseSnapshot
import tech.future.sleepanalyzer.data.db.DATABASE_SCHEMA_VERSION
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates and restores a portable archive containing the complete Room database, app preferences,
 * audio recordings, and voice-enrollment samples. Version-1 JSON backups remain importable.
 */
class LocalBackupManager(
    context: Context,
    private val repository: SleepRepository,
    private val preferences: AppPreferences
) {
    private val appContext = context.applicationContext

    suspend fun exportTo(output: OutputStream): Result<BackupSummary> = runCatching {
        val snapshotFile = File(appContext.cacheDir, "backup_export_${UUID.randomUUID()}.db")
        try {
            val databaseSnapshot = AppDatabase.createBackupSnapshot(appContext, snapshotFile)
            val recordingMedia = databaseSnapshot.recordingFiles.map { (id, sourcePath) ->
                prepareMedia(
                    databaseId = id,
                    sourcePath = sourcePath,
                    archiveDirectory = RECORDINGS_DIRECTORY,
                    filePrefix = "recording"
                )
            }
            val voiceProfileMedia = databaseSnapshot.voiceProfileFiles.map { (id, sourcePath) ->
                prepareMedia(
                    databaseId = id,
                    sourcePath = sourcePath,
                    archiveDirectory = VOICE_PROFILES_DIRECTORY,
                    filePrefix = "voice_profile"
                )
            }

            val manifest = createManifest(databaseSnapshot, recordingMedia, voiceProfileMedia)
            ZipOutputStream(output.buffered()).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                zip.writeBytes(
                    MANIFEST_ENTRY,
                    manifest.toJson().toString(2).toByteArray(Charsets.UTF_8)
                )
                zip.writeFile(DATABASE_ENTRY, databaseSnapshot.databaseFile)
                (recordingMedia + voiceProfileMedia).forEach { media ->
                    zip.writeFile(media.entry.archivePath, media.source)
                }
            }

            BackupSummary(
                databaseRecords = databaseSnapshot.databaseRecords,
                mediaFiles = recordingMedia.size + voiceProfileMedia.size
            )
        } finally {
            snapshotFile.delete()
        }
    }

    suspend fun importFrom(input: InputStream): Result<BackupSummary> = runCatching {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(ZIP_SIGNATURE.size)
        val signature = ByteArray(ZIP_SIGNATURE.size)
        val signatureLength = buffered.read(signature)
        buffered.reset()
        if (signatureLength == ZIP_SIGNATURE.size && signature.contentEquals(ZIP_SIGNATURE)) {
            importPortableArchive(buffered)
        } else {
            LegacyJsonBackupImporter(repository).importFrom(buffered)
        }
    }

    private suspend fun importPortableArchive(input: InputStream): BackupSummary {
        val stagingDirectory = File(
            appContext.cacheDir,
            "backup_restore_${UUID.randomUUID()}"
        ).apply { mkdirs() }
        try {
            extractArchive(input, stagingDirectory)
            val manifestFile = safeArchiveFile(stagingDirectory, MANIFEST_ENTRY)
            if (!manifestFile.isFile) throw IOException("Backup manifest is missing")
            val manifest = BackupManifest.fromJson(JSONObject(manifestFile.readText(Charsets.UTF_8)))
            require(manifest.formatVersion == BackupManifest.CURRENT_FORMAT_VERSION) {
                "Backup version ${manifest.formatVersion} is not supported"
            }
            require(manifest.databaseVersion == DATABASE_SCHEMA_VERSION) {
                "Backup database version ${manifest.databaseVersion} does not match app version $DATABASE_SCHEMA_VERSION"
            }

            val databaseFile = safeArchiveFile(stagingDirectory, DATABASE_ENTRY)
            if (!databaseFile.isFile) throw IOException("Backup database is missing")

            val recordingsDirectory = File(appContext.filesDir, RECORDINGS_DIRECTORY)
            val voiceProfilesDirectory = File(appContext.filesDir, VOICE_PROFILES_DIRECTORY)
            val previousPreferences = preferences.createBackupSnapshot()
            val installedMediaPaths = mutableListOf<String>()
            try {
                val recordingPaths = installMedia(
                    entries = manifest.recordings,
                    stagingDirectory = stagingDirectory,
                    targetDirectory = recordingsDirectory,
                    requiredArchiveDirectory = RECORDINGS_DIRECTORY
                ).also { installedMediaPaths += it.values }
                val voiceProfilePaths = installMedia(
                    entries = manifest.voiceProfiles,
                    stagingDirectory = stagingDirectory,
                    targetDirectory = voiceProfilesDirectory,
                    requiredArchiveDirectory = VOICE_PROFILES_DIRECTORY
                ).also { installedMediaPaths += it.values }

                preferences.restoreBackupSnapshot(manifest.preferences)
                val restoredRecords = try {
                    AppDatabase.restoreFromBackup(
                        context = appContext,
                        backupDatabase = databaseFile,
                        recordingPaths = recordingPaths,
                        voiceProfilePaths = voiceProfilePaths
                    )
                } catch (failure: Throwable) {
                    try {
                        preferences.restoreBackupSnapshot(previousPreferences)
                    } catch (rollbackFailure: Throwable) {
                        failure.addSuppressed(rollbackFailure)
                    }
                    throw failure
                }
                pruneUnreferencedFiles(recordingsDirectory, recordingPaths.values)
                pruneUnreferencedFiles(voiceProfilesDirectory, voiceProfilePaths.values)

                return BackupSummary(
                    databaseRecords = restoredRecords,
                    mediaFiles = recordingPaths.size + voiceProfilePaths.size
                )
            } catch (failure: Throwable) {
                installedMediaPaths.forEach { File(it).delete() }
                throw failure
            }
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }

    private fun prepareMedia(
        databaseId: Long,
        sourcePath: String,
        archiveDirectory: String,
        filePrefix: String
    ): PreparedMedia {
        val source = File(sourcePath)
        if (!source.isFile) {
            throw IOException("Referenced media file is missing: ${source.name.ifBlank { sourcePath }}")
        }
        val extension = source.extension
            .lowercase(Locale.ROOT)
            .takeIf { it.matches(SAFE_EXTENSION) }
            ?.let { ".$it" }
            .orEmpty()
        val archivePath = "$archiveDirectory/${filePrefix}_$databaseId$extension"
        return PreparedMedia(
            entry = BackupMediaEntry(databaseId = databaseId, archivePath = archivePath),
            source = source
        )
    }

    private suspend fun createManifest(
        databaseSnapshot: BackupDatabaseSnapshot,
        recordingMedia: List<PreparedMedia>,
        voiceProfileMedia: List<PreparedMedia>
    ): BackupManifest = BackupManifest(
        formatVersion = BackupManifest.CURRENT_FORMAT_VERSION,
        databaseVersion = DATABASE_SCHEMA_VERSION,
        exportedAt = System.currentTimeMillis(),
        databaseRecords = databaseSnapshot.databaseRecords,
        preferences = preferences.createBackupSnapshot(),
        recordings = recordingMedia.map(PreparedMedia::entry),
        voiceProfiles = voiceProfileMedia.map(PreparedMedia::entry)
    )

    private fun extractArchive(input: InputStream, stagingDirectory: File) {
        var entryCount = 0
        var extractedBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("Backup has too many files")
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (!isAllowedArchivePath(entry.name)) {
                    throw IOException("Unexpected backup entry: ${entry.name}")
                }
                val target = safeArchiveFile(stagingDirectory, entry.name)
                target.parentFile?.mkdirs()
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        extractedBytes += read
                        if (extractedBytes > MAX_UNCOMPRESSED_ARCHIVE_BYTES) {
                            throw IOException("Backup is larger than the supported limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun installMedia(
        entries: List<BackupMediaEntry>,
        stagingDirectory: File,
        targetDirectory: File,
        requiredArchiveDirectory: String
    ): Map<Long, String> {
        require(entries.map(BackupMediaEntry::databaseId).distinct().size == entries.size) {
            "Backup contains duplicate media ids"
        }
        targetDirectory.mkdirs()
        val installedFiles = mutableListOf<File>()
        try {
            return entries.associate { entry ->
                require(entry.archivePath.startsWith("$requiredArchiveDirectory/")) {
                    "Media entry is stored in the wrong backup directory"
                }
                val stagedFile = safeArchiveFile(stagingDirectory, entry.archivePath)
                if (!stagedFile.isFile) {
                    throw IOException("Backup media is missing: ${entry.archivePath}")
                }
                val target = File(targetDirectory, "${UUID.randomUUID()}_${stagedFile.name}")
                copyAtomically(stagedFile, target)
                installedFiles += target
                entry.databaseId to target.absolutePath
            }
        } catch (failure: Throwable) {
            installedFiles.forEach(File::delete)
            throw failure
        }
    }

    private fun copyAtomically(source: File, target: File) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        var completed = false
        try {
            source.copyTo(temporary, overwrite = true)
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace ${target.name}")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
            }
            completed = true
        } finally {
            temporary.delete()
            if (!completed) target.delete()
        }
    }

    private fun pruneUnreferencedFiles(directory: File, referencedPaths: Collection<String>) {
        val referenced = referencedPaths.mapTo(HashSet()) { File(it).absolutePath }
        directory.listFiles()
            ?.filter(File::isFile)
            ?.filterNot { it.absolutePath in referenced }
            ?.forEach(File::delete)
    }

    private fun safeArchiveFile(root: File, relativePath: String): File {
        if (
            relativePath.startsWith("/") ||
            relativePath.contains('\\') ||
            relativePath.split('/').any { it == ".." }
        ) {
            throw IOException("Unsafe backup path")
        }
        val file = File(root, relativePath)
        val rootPath = root.canonicalPath + File.separator
        if (!file.canonicalPath.startsWith(rootPath)) throw IOException("Unsafe backup path")
        return file
    }

    private fun isAllowedArchivePath(path: String): Boolean =
        path == MANIFEST_ENTRY ||
            path == DATABASE_ENTRY ||
            path.startsWith("$RECORDINGS_DIRECTORY/") ||
            path.startsWith("$VOICE_PROFILES_DIRECTORY/")

    private data class PreparedMedia(
        val entry: BackupMediaEntry,
        val source: File
    )

    companion object {
        const val MIME_TYPE = "application/zip"
        const val FILE_EXTENSION = "sleepbackup"

        private const val MANIFEST_ENTRY = "manifest.json"
        private const val DATABASE_ENTRY = "database/${AppDatabase.DATABASE_NAME}"
        private const val RECORDINGS_DIRECTORY = "recordings"
        private const val VOICE_PROFILES_DIRECTORY = "voice_profiles"
        private const val MAX_ARCHIVE_ENTRIES = 100_000
        private const val MAX_UNCOMPRESSED_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
    }
}

private fun ZipOutputStream.writeBytes(path: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(path))
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.writeFile(path: String, source: File) {
    putNextEntry(ZipEntry(path))
    source.inputStream().buffered().use { it.copyTo(this) }
    closeEntry()
}
