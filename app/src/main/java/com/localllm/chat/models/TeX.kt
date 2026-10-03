package com.localllm.chat.models

/** Best-effort LaTeX to Unicode for the notation models emit in chat. Code is left untouched. */
object TeX {
    private val code = Regex("""```[\s\S]*?(?:```|\z)|`[^`\n]*`""")
    private val span = Regex("""\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\]|\\\((.+?)\\\)|\$([^\s$](?:[^$\n]{0,198}[^\s$])?)\$""")
    private val command = Regex("""\\([A-Za-z]+)""")
    private const val ARG = """\{((?:[^{}]|\{[^{}]*\})*)\}"""
    private val structure = Regex("""\\frac$ARG$ARG|\\sqrt$ARG""")
    private val script = Regex("""([_^])(?:\{([^{}]*)\}|([^\s{}(]))""")
    private val superscripts = "0123456789+-=()abcdefghijklmnoprstuvwxyz".zip("⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ").toMap()
    private val subscripts = "0123456789+-=()aehijklmnoprstuvx".zip("₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ").toMap()
    private val symbols =
        ("alpha beta gamma delta epsilon varepsilon zeta eta theta iota kappa lambda mu nu xi pi rho sigma tau upsilon phi " +
            "varphi chi psi omega Gamma Delta Theta Lambda Sigma Phi Psi Omega times div cdot pm mp leq le geq ge neq approx " +
            "equiv propto sim infty partial nabla sum prod int forall exists emptyset subseteq subset cup cap notin in " +
            "Rightarrow Leftarrow leftrightarrow rightarrow leftarrow to ldots cdots degree")
            .split(' ').zip("αβγδεεζηθικλμνξπρστυφφχψωΓΔΘΛΣΦΨΩ×÷·±∓≤≤≥≥≠≈≡∝∼∞∂∇∑∏∫∀∃∅⊆⊂∪∩∉∈⇒⇐↔→←→…⋯°".map(Char::toString)).toMap() +
            listOf("left", "right", "text", "mathrm", "mathbf", "displaystyle").associateWith { "" } + ("quad" to "  ")

    /** Rewrites `$…$`, `\(…\)`, `$$…$$` and `\[…\]` outside code. `$5 and $10` stays currency. */
    fun substitute(text: String): String {
        var start = 0
        return buildString {
            for (match in code.findAll(text)) {
                append(math(text.substring(start, match.range.first))).append(match.value)
                start = match.range.last + 1
            }
            append(math(text.substring(start)))
        }
    }

    private fun math(text: String) = span.replace(text) { match ->
        val (display, bracket, inline, dollar) = match.destructured
        when {
            display.isNotBlank() || bracket.isNotBlank() -> "\n\n${unicode(display + bracket)}\n\n"
            inline.isNotEmpty() -> unicode(inline)
            dollar.any { it in "\\^_=+<>" } -> unicode(dollar)
            else -> match.value
        }
    }

    fun unicode(latex: String): String {
        var text = command.replace(latex) { symbols[it.groupValues[1]] ?: it.value }
        text = structure.replace(text) {
            val (top, bottom, root) = it.destructured
            if (it.value.startsWith("\\sqrt")) "√${wrap(unicode(root))}" else "${wrap(unicode(top))}⁄${wrap(unicode(bottom))}"
        }
        text = script.replace(text) { script(it.groupValues[1], it.groupValues[2] + it.groupValues[3]) }
        return text.replace("\\,", " ").replace("\\;", " ").replace("\\!", "").filterNot { it in "{}\\" }.trim()
    }

    private fun script(marker: String, body: String): String {
        val table = if (marker == "^") superscripts else subscripts
        return body.map { table[it] ?: return "$marker($body)" }.joinToString("")
    }

    private fun wrap(term: String) = if (term.length > 1) "($term)" else term
}
