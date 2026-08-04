package com.vineyard.fastgit.app.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

object SyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFFFF7B72) // Coral red
    private val STRING_COLOR = Color(0xFFA5D6FF)  // Soft light blue
    private val COMMENT_COLOR = Color(0xFF8B949E) // Gray
    private val NUMBER_COLOR = Color(0xFF79C0FF)  // Cyan
    private val ANNOTATION_COLOR = Color(0xFFD2A8FF) // Purple
    private val DEFAULT_TEXT_COLOR = Color(0xFFC9D1D9) // Light off-white

    private val KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
        "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
        "long", "native", "new", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "fun", "val", "var", "when", "sealed",
        "data", "object", "typealias", "override", "open", "internal", "companion", "lateinit",
        "by", "in", "is", "where", "suspend", "coroutine", "flow", "state", "recompose", "true", "false", "null"
    )

    // Precompiled high-speed parsing pattern executing sequentially across the document stream
    private val COMBINED_PATTERN = Pattern.compile(
        "(//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\r\\n]*)" +                      // Group 1: Comments
        "|(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')" +  // Group 2: Strings
        "|\\b(" + KEYWORDS.joinToString("|") + ")\\b" +                         // Group 3: Keywords
        "|(@\\w+)" +                                                            // Group 4: Annotations
        "|(\\b\\d+\\b)"                                                         // Group 5: Numbers
    )

    fun highlight(code: String, fileName: String = ""): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val matcher = COMBINED_PATTERN.matcher(code)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Append unstyled text segments prior to matching index
            if (start > lastIndex) {
                builder.append(code.substring(lastIndex, start))
            }

            // Style matching segments based on capture group indices
            when {
                matcher.group(1) != null -> { // Comments
                    builder.withStyle(SpanStyle(color = COMMENT_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(2) != null -> { // Strings
                    builder.withStyle(SpanStyle(color = STRING_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(3) != null -> { // Keywords
                    builder.withStyle(SpanStyle(color = KEYWORD_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(4) != null -> { // Annotations
                    builder.withStyle(SpanStyle(color = ANNOTATION_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(5) != null -> { // Numbers
                    builder.withStyle(SpanStyle(color = NUMBER_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                else -> {
                    builder.append(code.substring(start, end))
                }
            }
            lastIndex = end
        }

        // Append remaining unstyled text segments
        if (lastIndex < code.length) {
            builder.append(code.substring(lastIndex))
        }

        return builder.toAnnotatedString()
    }
}