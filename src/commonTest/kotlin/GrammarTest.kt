
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.lexer.token
import com.github.h0tk3y.betterParse.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarTest {
    @Test
    fun simpleParse() {
        val digits = "0123456789"
        val g = object : Grammar<Int>() {
            val n by token { input, from ->
                var length = 0
                while (from + length < input.length && input[from + length] in digits)
                    length++
                length
            }
            val s by regexToken("\\-|\\+")
            val ws by regexToken("\\s+", ignore = true)

            override val rootParser: Parser<Int> = separated(n use { text.toInt() }, s use { text }).map {
                it.reduce { a, s, b ->
                    if (s == "+") a + b else a - b
                }
            }
        }

        val result = g.parseToEnd("1 + 2 + 3 + 4 - 11")
        assertEquals(-1, result)
    }

    @Test
    fun combiningGrammars() {
        val str = "aabb"

        val aGrammar = object : Grammar<String>() {
            val a by literalToken("a")

            override val rootParser: Parser<String> = (a and a).map {
                it.t1.text + it.t2.text
            }
        }

        val bGrammar = object : Grammar<String>() {
            val b by literalToken("b")
            val a by aGrammar

            override val rootParser: Parser<String> = (a and (b and b).map {
                it.t1.text + it.t2.text
            }).map {
                it.t1 + it.t2
            }
        }

        val result = bGrammar.parseToEnd(str)
        assertEquals(expected = "aabb", actual = result)
    }

    @Test
    fun combiningGrammarsInheritance() {
        data class Inner(val names: List<String>)
        data class Outer(val name: String, val inner: Inner)

        abstract class TestGrammarBase<T> : Grammar<T>() {
            val idToken by regexToken(name = "idToken", "\\w+")
            val spaceToken by regexToken("\\s*", true)
            val commaToken by literalToken(",")
            val lBraceToken by literalToken("{")
            val rBraceToken by literalToken("}")
        }

        class InnerTestGrammar : TestGrammarBase<Inner>() {
            override val tokens = super.tokens
            override val rootParser: Parser<Inner> by separatedTerms(idToken, commaToken, false) map inner@{ tokenMatches ->
                return@inner Inner(
                    names = tokenMatches.map(TokenMatch::text)
                )
            }
        }

        class OuterTestGrammar : TestGrammarBase<Outer>() {
            val innerTestParser by InnerTestGrammar()

            override val tokens = super.tokens
            override val rootParser: Parser<Outer> by idToken and skip(lBraceToken) and parser { innerTestParser } and skip(rBraceToken) map outer@{ (tokenMatch, inner) ->
                return@outer Outer(
                    name = tokenMatch.text,
                    inner = inner
                )
            }
        }

        val test = "A { X, Y, Z }"

        val result = OuterTestGrammar().parseToEnd(test)

        assertEquals(expected = listOf("X", "Y", "Z"), actual = result.inner.names)
    }
}