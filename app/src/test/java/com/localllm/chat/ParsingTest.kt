package com.localllm.chat

import com.localllm.chat.models.AppJson
import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Attachment
import com.localllm.chat.models.AttachmentType
import com.localllm.chat.models.Conversation
import com.localllm.chat.models.InferenceEngine
import com.localllm.chat.models.Message
import com.localllm.chat.models.Role
import com.localllm.chat.models.TeX
import com.localllm.chat.models.ThoughtParser
import com.localllm.chat.models.ThoughtTagger
import com.localllm.chat.models.prompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtParserTest {
    @Test
    fun splitsReasoningFromAnswer() {
        val result = ThoughtParser.parse("<think>weighing options</think>The answer is 4.")
        assertEquals("weighing options", result.thought)
        assertEquals("The answer is 4.", result.answer)
    }

    @Test
    fun plainTextIsLeftAlone() {
        val result = ThoughtParser.parse("Just an answer.")
        assertNull(result.thought)
        assertEquals("Just an answer.", result.answer)
    }

    @Test
    fun unterminatedThoughtIsStillExtracted() {
        val result = ThoughtParser.parse("<think>still going")
        assertEquals("still going", result.thought)
        assertTrue(result.answer.isEmpty())
    }

    @Test
    fun alternateTagSyntax() {
        val result = ThoughtParser.parse("<|begin_of_thought|>hmm<|end_of_thought|>Done")
        assertEquals("hmm", result.thought)
        assertEquals("Done", result.answer)
    }
}

class ThoughtTaggerTest {
    @Test
    fun separateReasoningIsWrappedInTags() {
        val tagger = ThoughtTagger()
        val output = tagger.render("hm", null) + tagger.render("m", null) + tagger.render(null, "Hi") + tagger.finish()
        assertEquals("<think>\nhmm\n</think>\nHi", output)
    }

    @Test
    fun aStreamEndingMidThoughtIsClosed() {
        val tagger = ThoughtTagger()
        val output = tagger.render("hmm", null) + tagger.finish()
        assertEquals("hmm", ThoughtParser.parse(output).thought)
    }
}

class TeXTest {
    @Test
    fun fractionsRootsAndSymbols() {
        assertEquals("1⁄2", TeX.unicode("\\frac{1}{2}"))
        assertEquals("(a+b)⁄2", TeX.unicode("\\frac{a+b}{2}"))
        assertEquals("√2", TeX.unicode("\\sqrt{2}"))
        assertEquals("α ≤ β", TeX.unicode("\\alpha \\leq \\beta"))
    }

    @Test
    fun scripts() {
        assertEquals("x²", TeX.unicode("x^2"))
        assertEquals("aᵢⱼ", TeX.unicode("a_{ij}"))
        assertEquals("x¹⁰", TeX.unicode("x^{10}"))
    }

    @Test
    fun inlineSpansAreConvertedButCurrencyAndCodeAreNot() {
        assertEquals("area is r² wide", TeX.substitute("area is \$r^2\$ wide"))
        assertEquals("costs \$5 and \$10 total", TeX.substitute("costs \$5 and \$10 total"))
        assertEquals("`\$x^2\$`", TeX.substitute("`\$x^2\$`"))
    }
}

class ModelsTest {
    @Test
    fun documentsAreAppendedToThePrompt() {
        val message = Message(
            role = Role.User,
            content = "Summarize",
            attachments = listOf(Attachment(type = AttachmentType.Pdf, extractedText = "Report")),
        )
        assertEquals("Summarize\n\nAttached documents:\n\nReport", message.prompt)
    }

    @Test
    fun conversationsSavedByEarlierVersionsStillLoad() {
        val legacy = """
            [{"id":"1","title":"Old","messages":[{"id":"m","role":"user","content":"Hi","timestamp":1,"isStreaming":false}],
              "createdAt":1,"updatedAt":1,"model":"llama3"}]
        """
        val conversation = AppJson.decodeFromString<List<Conversation>>(legacy).single()
        assertEquals(Role.User, conversation.messages.single().role)
        assertTrue(conversation.messages.single().attachments.isEmpty())
    }

    @Test
    fun activeModelNameDropsTheNamespace() {
        val settings = AppSettings(engine = InferenceEngine.LiteRt, localModel = "litert-community/Gemma3-1B-IT")
        assertEquals("Gemma3-1B-IT", settings.activeModelName)
        assertNull(settings.copy(localModel = "").activeModelName)
    }
}
