package com.exteragram.messenger.translator

import com.exteragram.messenger.translator.core.BaseTranslator
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.TranslationDispatcher
import com.exteragram.messenger.translator.core.TranslationError
import org.telegram.messenger.AndroidUtilities

object ChatTranslator {

    private const val CACHE_SIZE = 300

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > CACHE_SIZE
    }

    fun interface Callback {
        fun onResult(texts: List<String?>, error: TranslationError?)
    }

    class Piece(val messageIndex: Int, val text: String) {
        var translated: String? = null
    }

    @JvmStatic
    fun translate(tag: Long, texts: List<String>, toLang: String, callback: Callback) {
        translate(tag, texts, toLang, TranslatorUtils.getCurrentTranslator(), callback)
    }

    @JvmStatic
    fun translate(tag: Long, texts: List<String>, toLang: String, translator: BaseTranslator, callback: Callback) {
        if (translator !is HttpTranslator) {
            AndroidUtilities.runOnUIThread { callback.onResult(arrayOfNulls<String>(texts.size).toList(), TranslationError.Fatal) }
            return
        }
        val limits = translator.limits
        val results = arrayOfNulls<String>(texts.size)
        val pieces = ArrayList<Piece>()
        texts.forEachIndexed { index, text ->
            if (text.isBlank()) {
                results[index] = text
                return@forEachIndexed
            }
            val cached = synchronized(cache) { cache[cacheKey(translator, toLang, text)] }
            if (cached != null) {
                results[index] = cached
            } else {
                split(text, limits.maxCharsPerText).forEach { pieces.add(Piece(index, it)) }
            }
        }
        if (pieces.isEmpty()) {
            AndroidUtilities.runOnUIThread { callback.onResult(results.toList(), null) }
            return
        }

        val chunks = chunk(pieces, limits)
        var remaining = chunks.size
        var firstError: TranslationError? = null
        for (chunk in chunks) {
            TranslationDispatcher.enqueue(translator, tag, chunk.map { it.text }, "auto", toLang) { translated, error ->
                if (translated != null && translated.size == chunk.size) {
                    chunk.forEachIndexed { i, piece -> piece.translated = translated[i] }
                } else if (error is TranslationError.Cancelled || firstError == null) {
                    firstError = error ?: TranslationError.Transient
                }
                if (--remaining == 0) {
                    assemble(translator, toLang, texts, pieces, results)
                    callback.onResult(results.toList(), firstError)
                }
            }
        }
    }

    @JvmStatic
    fun cancel(tag: Long) {
        TranslationDispatcher.cancel(tag)
    }

    @JvmStatic
    fun cancelAll() {
        TranslationDispatcher.cancelAll()
    }

    @JvmStatic
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun assemble(translator: HttpTranslator, toLang: String, texts: List<String>, pieces: List<Piece>, results: Array<String?>) {
        val sb = StringBuilder()
        var i = 0
        while (i < pieces.size) {
            val messageIndex = pieces[i].messageIndex
            var complete = true
            var end = i
            while (end < pieces.size && pieces[end].messageIndex == messageIndex) {
                if (pieces[end].translated == null) {
                    complete = false
                }
                end++
            }
            if (complete) {
                sb.setLength(0)
                for (j in i until end) {
                    sb.append(pieces[j].translated)
                }
                val text = sb.toString()
                results[messageIndex] = text
                synchronized(cache) { cache[cacheKey(translator, toLang, texts[messageIndex])] = text }
            }
            i = end
        }
    }

    private fun split(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) {
            return listOf(text)
        }
        val parts = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            if (text.length - start <= maxLength) {
                parts.add(text.substring(start))
                break
            }
            val limit = start + maxLength
            var breakAt = text.lastIndexOf('\n', limit - 1)
            if (breakAt <= start) {
                breakAt = text.lastIndexOf(' ', limit - 1)
            }
            val end = if (breakAt <= start) limit else breakAt + 1
            parts.add(text.substring(start, end))
            start = end
        }
        return parts
    }

    private fun chunk(pieces: List<Piece>, limits: ProviderLimits): List<List<Piece>> {
        val chunks = ArrayList<List<Piece>>()
        var current = ArrayList<Piece>()
        var length = 0
        for (piece in pieces) {
            if (current.isNotEmpty() && (current.size >= limits.maxTextsPerRequest || length + piece.text.length > limits.maxCharsPerRequest)) {
                chunks.add(current)
                current = ArrayList()
                length = 0
            }
            current.add(piece)
            length += piece.text.length
        }
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        return chunks
    }

    private fun cacheKey(translator: BaseTranslator, toLang: String, text: String): String =
        "${translator.javaClass.simpleName} $toLang $text"
}
