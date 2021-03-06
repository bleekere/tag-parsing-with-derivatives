package nl.knaw.huc.di.rd.tag.tagml.tokenizer

import arrow.core.Either
import lambdada.parsec.extension.charsToString
import lambdada.parsec.parser.*
import lambdada.parsec.parser.Response.Reject
import nl.knaw.huc.di.rd.parsec.PositionalReader
import nl.knaw.huc.di.rd.parsec.ort
import nl.knaw.huc.di.rd.parsec.toLSPToken
import org.eclipse.lsp4j.Position
import java.net.URL

object TAGMLTokenizer {

    private val specialChar = charIn("""[]<>\|""")

    private val whitespace = charIn(""" \n\t""").optrep

    private val url = ((string("http://") ort string("https://") or string("file://")) then (charIn(CharRange('a', 'z')) or charIn("/.")).rep).map { listOfNotNull(it.first) + it.second }

    private val escapedSpecialChar = (char('\\') then specialChar)
            .map { "${it.first}${it.second}" }

    private val variableName = (
            charIn(CharRange('a', 'z')) then
                    charIn("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_").optrep
            ).map { (listOfNotNull(it.first) + it.second).charsToString() }

    private val tagName = variableName

    private val namespaceIdentifier = variableName

    private val markStart: Parser<Char, String> = {
        val p = (it as PositionalReader).read()
        if (p == null) {
            Reject(it.location(), false)
        } else {
            val r = p.second
            it.startPosition = r.lastPosition
            Response.Accept("", it, false)
        }
    }

    private val markEnd: Parser<Char, String> = {
        val r = it as PositionalReader
        r.endPosition = Position(r.lastPosition.line, r.lastPosition.character + 1)
        Response.Accept("", r, false)
    }

    // use `try` because schemaLocation and startTag both start with '['
    private val schemaLocation = `try`(string("[!schema ") thenRight url thenLeft char(']'))
            .toLSPToken { SchemaLocationToken(URL(it.toList().joinToString(separator = ""))) }

    private val namespaceDefinition = (string("[!ns ") then whitespace thenRight namespaceIdentifier then whitespace then url thenLeft char(']'))
            .toLSPToken { NameSpaceIdentifierToken(it.first.first, URL(it.second.toList().joinToString(separator = ""))) }

    public val startTag = (markStart then char('[') then tagName then char('>') then markEnd)
            .toLSPToken { StartMarkupToken(it.first.first.second) }

    private val resumeTag = (markStart then string("[+") then tagName then char('>') then markEnd)
            .toLSPToken { ResumeMarkupToken(it.first.first.second) }

    val endTag = (markStart then char('<') then tagName then char(']') then markEnd)
            .toLSPToken { EndMarkupToken(it.first.first.second) }

    fun specificEndTag(endTagName: String): Parser<Char, LSPToken> {
        return (markStart then char('<') then string(endTagName) then char(']') then markEnd)
            .toLSPToken { EndMarkupToken(endTagName) }
    }

    private val suspendTag = (markStart then string("<-") then tagName then char(']') then markEnd)
            .toLSPToken { SuspendMarkupToken(it.first.first.second) }

    val text = (markStart then (not(specialChar).map { it.toString() } or escapedSpecialChar).rep then markEnd)
            .toLSPToken { TextToken(it.first.second.joinToString(separator = "")) }

    val startTextVariation = string("<|").toLSPToken { StartTextVariationToken }
    private val textVariationSeparator = char('|').toLSPToken { TextVariationSeparatorToken }
    private val endTextVariation = string("|>").toLSPToken { EndTextVariationToken }

    val tagmlParser = (schemaLocation.opt then
            (startTag ort text ort endTag ort suspendTag ort resumeTag ort startTextVariation ort endTextVariation ort textVariationSeparator).rep
            thenLeft eos())
            .map { listOfNotNull(it.first) + it.second }

    fun tokenize(tagml: String): Either<Reject<Char, List<LSPToken>>, List<LSPToken>> {
        val tagmlReader = PositionalReader.string(tagml)
        return tagmlParser(tagmlReader)
                .fold(
                        { Either.Right(it.value) },
                        { Either.Left(it) }
                )
    }
//    fun tokenize0(tagml: String): Either<Response.Reject<Char, List<TAGMLToken>>, List<TAGMLToken>> {
//        val tagmlReader = Reader.string(tagml)
//        return tagmlParser(tagmlReader)
//                .fold(
//                        { Either.Right(it.value) },
//                        { Either.Left(it) }
//                )
//    }

}
