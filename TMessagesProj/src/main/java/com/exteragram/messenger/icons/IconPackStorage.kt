package com.exteragram.messenger.icons

import com.exteragram.messenger.export.output.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.files.OverwritePolicy
import org.simplifiles.files.SimpliDirectory
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File

object IconPackStorage {

    private const val METADATA_FILE = "metadata.json"
    private const val MAX_METADATA_SIZE = 256L * 1024
    private const val SCHEMA_VERSION = 1

    @Volatile
    private var packsDirectory: File? = null
    private var cachedCustomPacks: Map<String, IconPack>? = null
    private val cachedPacksById = HashMap<String, IconPack?>()
    private val resourceNameRegex = Regex("[A-Za-z0-9_]+")

    private val iconPackArchivePolicy = SecurityPolicy.builder()
        .maxEntries(3000)
        .maxTotalUncompressedSize(100L * 1024 * 1024)
        .maxSingleFileSize(10L * 1024 * 1024)
        .maxCompressionRatio(250.0)
        .build()

    private val iconPackArchiveSaveOptions = ArchiveSaveOptions.builder()
        .compressionLevel(0)
        .build()

    class IconPackStorageException(val error: IconPackStorageError) : Exception(error.name)

    val iconPacksDirectory: File
        get() {
            packsDirectory?.let { return it }
            val dir = File(ApplicationLoader.applicationContext.filesDir, "icon_packs")
            SimpliFiles.directory(dir).create()
            packsDirectory = dir
            return dir
        }

    private fun isPlainFileName(name: String): Boolean {
        if (name.isEmpty() || name == "." || name == "..") {
            return false
        }
        return name.none { it == '\u0000' || it == '/' || it == '\\' }
    }

    private fun isValidPackId(packId: String): Boolean {
        return !packId.isBlank() && !packId.contains('/') && !packId.contains('\\') && packId != "." && packId != ".."
    }

    private fun parseMetadata(json: JSONObject, baseDir: File? = null, location: File? = null): IconPack {
        val name = json.getString("packName")
        val id = json.getString("packId")
        if (!isValidPackId(id)) {
            throw SecurityException("Invalid icon pack id: $id")
        }
        val author = json.optString("author", "Unknown")
        val version = json.optString("version", "1.0")
        val iconsJson = json.optJSONObject("icons")
        val icons = LinkedHashMap<String, String>()
        val canonicalBaseDir = baseDir?.canonicalFile
        iconsJson?.keys()?.forEach { key ->
            var path = iconsJson.getString(key)
            if (baseDir != null && canonicalBaseDir != null) {
                // throws if the path escapes the pack directory
                SimpliFiles.directory(baseDir).resolveInside(path)
                if (!isPlainFileName(path)) {
                    path = File(baseDir, path).canonicalFile.relativeTo(canonicalBaseDir).invariantSeparatorsPath
                }
            }
            icons[key] = path
        }
        return IconPack(id, name, author, version, icons, location = location)
    }

    private fun parseMetadataFile(file: File): IconPack? {
        return try {
            parseMetadata(JSONObject(SimpliFiles.file(file).readText(MAX_METADATA_SIZE)), file.parentFile)
        } catch (e: Exception) {
            FileLog.e("Error parsing metadata.json", e)
            null
        }
    }

    private fun extractPackArchive(archive: File, targetDir: File): JSONObject {
        val metadata = SimpliFiles.archive(archive)
            .withPolicy(iconPackArchivePolicy)
            .extractTo(targetDir)
            .file(METADATA_FILE)
        if (!metadata.exists()) {
            FileLog.e("Icon pack archive does not contain metadata.json")
            throw IconPackStorageException(IconPackStorageError.MISSING_METADATA)
        }
        if (metadata.size > MAX_METADATA_SIZE) {
            FileLog.e("Icon pack metadata.json is too large")
            throw IconPackStorageException(IconPackStorageError.METADATA_TOO_LARGE)
        }
        return JSONObject(metadata.readText())
    }

    private fun errorFromValidationException(e: ArchiveValidationException): IconPackStorageError {
        return when (e.report.issues.firstOrNull()?.code) {
            "archive.entries.too_many" -> IconPackStorageError.TOO_MANY_FILES
            "archive.total_size.too_large" -> IconPackStorageError.ARCHIVE_TOO_LARGE
            "archive.entry.size.too_large" -> IconPackStorageError.FILE_TOO_LARGE
            "archive.entry.compression_ratio.too_high" -> IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH
            else -> IconPackStorageError.INVALID_ARCHIVE
        }
    }

    private fun errorFromException(e: Exception): IconPackStorageError {
        return when (e) {
            is IconPackStorageException -> e.error
            is ArchiveValidationException -> errorFromValidationException(e)
            is CorruptedArchiveException -> IconPackStorageError.INVALID_ARCHIVE
            is SecurityException, is JSONException -> IconPackStorageError.INVALID_METADATA
            else -> IconPackStorageError.UNKNOWN
        }
    }

    private fun createTempCacheDirectory(prefix: String): SimpliDirectory {
        return SimpliFiles.directory(File(ApplicationLoader.applicationContext.cacheDir, "${prefix}_${System.currentTimeMillis()}")).create()
    }

    private fun deleteDirectoryIfExists(directory: SimpliDirectory) {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    @Synchronized
    fun findPackById(packId: String): IconPack? {
        if (!isValidPackId(packId)) {
            return null
        }
        cachedCustomPacks?.get(packId)?.let { return it }
        if (cachedPacksById.containsKey(packId)) {
            return cachedPacksById[packId]
        }
        var pack: IconPack? = null
        val packDir = SimpliFiles.directory(File(iconPacksDirectory, packId))
        if (packDir.exists()) {
            val metadata = packDir.file(METADATA_FILE)
            if (metadata.exists()) {
                pack = parseMetadataFile(metadata.file)
            }
        }
        cachedPacksById[packId] = pack
        return pack
    }

    private fun invalidatePack(packId: String) {
        cachedCustomPacks = null
        cachedPacksById.remove(packId)
    }

    private fun invalidateAllPacks() {
        cachedCustomPacks = null
        cachedPacksById.clear()
    }

    fun resolveIconFile(packId: String, resourceName: String): File? {
        if (!isValidPackId(packId) || !resourceNameRegex.matches(resourceName)) {
            return null
        }
        try {
            val fileName = findPackById(packId)?.icons?.get(resourceName) ?: return null
            val packDir = File(iconPacksDirectory, packId).canonicalFile
            val file = File(packDir, fileName).canonicalFile
            if (file.path.startsWith(packDir.path + File.separator) && file.isFile) {
                return file
            }
        } catch (e: Exception) {
            FileLog.e("Failed to resolve icon file for pack $packId", e)
        }
        return null
    }

    suspend fun bundlePack(packId: String): File? = withContext(Dispatchers.IO) {
        if (!isValidPackId(packId)) {
            return@withContext null
        }
        val pack = findPackById(packId) ?: return@withContext null
        val packDir = SimpliFiles.directory(File(iconPacksDirectory, packId))
        if (!packDir.exists()) {
            return@withContext null
        }
        val sharedDir = SimpliFiles.directory(File(ApplicationLoader.applicationContext.cacheDir, "shared_packs"))
        try {
            if (sharedDir.exists()) {
                sharedDir.deleteRecursively()
            }
            sharedDir.create()
            val target = sharedDir.file(FileManager.fileNameFromUserString(pack.name) + ".icons").file
            packDir.zipTo(target, iconPackArchiveSaveOptions).file
        } catch (e: Exception) {
            FileLog.e("Failed to bundle pack: $packId", e)
            null
        }
    }

    fun bundlePackBlocking(packId: String): File? = runBlocking { bundlePack(packId) }

    @Synchronized
    fun getCustomPacks(): List<IconPack> {
        cachedCustomPacks?.let { return ArrayList(it.values) }
        val packs = ArrayList<IconPack>()
        iconPacksDirectory.listFiles()?.forEach { dir ->
            val metadata = SimpliFiles.directory(dir).file(METADATA_FILE)
            if (metadata.exists()) {
                parseMetadataFile(metadata.file)?.let { packs.add(it) }
            }
        }
        cachedCustomPacks = packs.associateBy { it.id }
        cachedPacksById.clear()
        return packs
    }

    @Synchronized
    fun saveIconPackMetadata(pack: IconPack): Boolean {
        if (!isValidPackId(pack.id)) {
            FileLog.e("Invalid icon pack id: ${pack.id}")
            return false
        }
        return try {
            val metadata = SimpliFiles.directory(File(iconPacksDirectory, pack.id)).create().file(METADATA_FILE)
            val json = JSONObject()
            json.put("schemaVersion", SCHEMA_VERSION)
            json.put("packName", pack.name)
            json.put("packId", pack.id)
            json.put("author", pack.author)
            json.put("version", pack.version)
            val icons = JSONObject()
            for ((name, file) in pack.icons) {
                icons.put(name, file)
            }
            json.put("icons", icons)
            metadata.writeTextAtomic(json.toString(4))
            invalidatePack(pack.id)
            true
        } catch (e: Exception) {
            FileLog.e("Error saving metadata", e)
            false
        }
    }

    @Synchronized
    fun deletePack(packId: String) {
        if (!isValidPackId(packId)) {
            return
        }
        val packDir = SimpliFiles.directory(File(iconPacksDirectory, packId))
        if (packDir.exists()) {
            deleteDirectoryIfExists(packDir)
            invalidatePack(packId)
        }
    }

    suspend fun installPack(file: File): IconPackStorageResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val tempDir = createTempCacheDirectory("install")
            try {
                val packId = parseMetadata(extractPackArchive(file, tempDir.file), tempDir.file).id
                val targetFile = File(iconPacksDirectory, packId)
                val trashFile = File(iconPacksDirectory, "${packId}_trash_${System.currentTimeMillis()}")
                val targetDir = SimpliFiles.directory(targetFile)
                val trashDir = SimpliFiles.directory(trashFile)

                if (targetDir.exists()) {
                    val movedToTrash = runCatching { targetDir.moveTo(trashFile, OverwritePolicy.ERROR) }
                    if (movedToTrash.isFailure && !targetDir.deleteRecursively()) {
                        return@withContext IconPackStorageResult.Failure(IconPackStorageError.STORAGE_ERROR)
                    }
                }

                val installed = runCatching { tempDir.moveTo(targetFile, OverwritePolicy.ERROR) }.isSuccess ||
                    runCatching { tempDir.copyTo(targetFile, OverwritePolicy.ERROR) }.isSuccess
                if (!installed) {
                    // restore the previous version
                    if (trashDir.exists()) {
                        if (targetDir.exists()) {
                            targetDir.deleteRecursively()
                        }
                        trashDir.moveTo(targetFile, OverwritePolicy.ERROR)
                    }
                    return@withContext IconPackStorageResult.Failure(IconPackStorageError.STORAGE_ERROR)
                }

                if (trashDir.exists()) {
                    trashDir.deleteRecursively()
                }
                synchronized(this@IconPackStorage) {
                    invalidateAllPacks()
                }
                IconPackStorageResult.Success(Unit)
            } finally {
                deleteDirectoryIfExists(tempDir)
            }
        } catch (e: IconPackStorageException) {
            FileLog.e("Pack installation failed: ${e.error}")
            IconPackStorageResult.Failure(e.error)
        } catch (e: Exception) {
            FileLog.e("Pack installation failed", e)
            IconPackStorageResult.Failure(errorFromException(e))
        }
    }

    suspend fun parsePackFromZip(file: File): IconPackStorageResult<IconPack> = withContext(Dispatchers.IO) {
        val tempDir = createTempCacheDirectory("preview")
        try {
            val json = extractPackArchive(file, tempDir.file)
            IconPackStorageResult.Success(parseMetadata(json, tempDir.file, tempDir.file))
        } catch (e: IconPackStorageException) {
            FileLog.e("Failed to parse pack for preview: ${e.error}")
            deleteDirectoryIfExists(tempDir)
            IconPackStorageResult.Failure(e.error)
        } catch (e: Exception) {
            FileLog.e("Failed to parse pack for preview", e)
            deleteDirectoryIfExists(tempDir)
            IconPackStorageResult.Failure(errorFromException(e))
        }
    }
}
