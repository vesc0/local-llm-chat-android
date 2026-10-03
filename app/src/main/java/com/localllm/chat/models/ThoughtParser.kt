package com.localllm.chat.models

object ThoughtParser {
    data class Result(val thought: String?, val answer: String)

    val openers = listOf("<think", "<thought", "<|begin_of_thought|>")
    val closers = listOf("</think>", "</thought>", "<|end_of_thought|>")

    private val regex = Regex(
        """<(?:think|thought|\|begin_of_thought\|)>([\s\S]*?)(?:</(?:think|thought)>|<\|end_of_thought\|>|\z)""",
        RegexOption.IGNORE_CASE,
    )

    /** Splits a reply into its reasoning trace and its answer. */
    fun parse(content: String): Result {
        val matches = regex.findAll(content).toList()
        if (matches.isEmpty()) return Result(null, content)
        val thought = matches.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        return Result(thought.ifEmpty { null }, regex.replace(content, "").trim())
    }
}

/** Wraps reasoning delivered separately from the answer in the `<think>` tags the UI parses. */
class ThoughtTagger {
    private var isThinking = false

    fun render(thought: String?, content: String?) = buildString {
        if (!thought.isNullOrEmpty()) {
            if (!isThinking) append("<think>\n").also { isThinking = true }
            append(thought)
        }
        if (!content.isNullOrEmpty()) {
            if (isThinking) append("\n</think>\n").also { isThinking = false }
            append(content)
        }
    }

    /** Closes a thought left open when the stream ends mid-reasoning. */
    fun finish() = if (isThinking) "\n</think>\n".also { isThinking = false } else ""
}
