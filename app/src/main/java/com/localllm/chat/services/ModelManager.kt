package com.localllm.chat.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.localllm.chat.models.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

data class LocalModel(val repoId: String, val file: File) {
    val name get() = repoId.substringAfter('/')
    val namespace get() = repoId.substringBefore('/')
    val sizeBytes get() = file.length()
}

/** Downloads `.litertlm` models from Hugging Face and manages them on disk. */
class ModelManager(private val dir: File) {
    var models by mutableStateOf(emptyList<LocalModel>())
        private set
    var downloadJob by mutableStateOf<Job?>(null)
        private set
    var downloadedBytes by mutableLongStateOf(0)
        private set
    var totalBytes by mutableLongStateOf(0)
        private set
    var status by mutableStateOf("")
        private set

    fun file(repoId: String) = repoId.takeIf { it.isNotEmpty() }?.let { folder(it).modelFile() }

    fun refresh() {
        models = dir.listFiles().orEmpty().mapNotNull { folder ->
            folder.modelFile()?.let { LocalModel(folder.name.replaceFirst("--", "/"), it) }
        }.sortedBy { it.repoId }
    }

    /** Drops the outcome of a finished download so it can't resurface later. */
    fun clearStatus() {
        if (downloadJob == null) status = ""
    }

    fun download(scope: CoroutineScope, repoId: String) {
        val repo = repoId.trim()
        if (!repo.matches(Regex("""[\w.-]+/[\w.-]+"""))) {
            status = "\"$repo\" is not a valid repository id."
            return
        }
        downloadJob = scope.launch {
            downloadedBytes = 0
            totalBytes = 0
            try {
                withContext(Dispatchers.IO) { fetch(repo) }
                status = "Download complete."
            } catch (e: CancellationException) {
                status = "Download canceled."
                throw e
            } catch (e: Exception) {
                status = "Error: ${e.message}"
            } finally {
                folder(repo).listFiles { file -> file.extension == "part" }?.forEach(File::delete)
                folder(repo).delete() // Only succeeds when the download left nothing behind.
                downloadJob = null
                refresh()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    suspend fun delete(model: LocalModel) {
        LiteRtRuntime.unload() // Releases the memory-mapped file so it can be removed.
        withContext(Dispatchers.IO) { model.file.parentFile?.deleteRecursively() }
        refresh()
    }

    suspend fun deleteAll() {
        LiteRtRuntime.unload()
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
        refresh()
    }

    private suspend fun fetch(repo: String) {
        val info = get("https://huggingface.co/api/models/$repo").use {
            AppJson.decodeFromString<RepoInfo>(it.body.string())
        }
        val name = info.siblings.map { it.rfilename }.firstOrNull { it.endsWith(".$EXTENSION") && "/" !in it }
            ?: throw IOException("$repo has no .$EXTENSION file.")
        val folder = folder(repo).apply { mkdirs() }
        val part = File(folder, "$name.part")

        get("https://huggingface.co/$repo/resolve/main/$name").use { response ->
            totalBytes = response.body.contentLength()
            response.body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer).takeIf { it >= 0 } ?: break
                        output.write(buffer, 0, count)
                        done += count
                        if (done - downloadedBytes >= PROGRESS_STEP) downloadedBytes = done
                    }
                }
            }
        }
        if (!part.renameTo(File(folder, name))) throw IOException("Couldn't save $name.")
    }

    private fun get(url: String): Response {
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (response.isSuccessful) return response
        response.close()
        throw IOException(
            if (response.code in setOf(401, 403)) "This repository doesn't exist or requires a Hugging Face login."
            else "Hugging Face responded with status ${response.code}."
        )
    }

    private fun folder(repoId: String) = File(dir, repoId.replace("/", "--"))

    private fun File.modelFile() = listFiles()?.firstOrNull { it.extension == EXTENSION }

    @Serializable
    private data class RepoInfo(val siblings: List<Sibling> = emptyList()) {
        @Serializable
        data class Sibling(val rfilename: String)
    }

    private companion object {
        const val EXTENSION = "litertlm"
        const val PROGRESS_STEP = 1L shl 20
    }
}
