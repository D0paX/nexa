package com.example.nexa.ui.common

/**
 * One search language for the whole app.
 *
 * Five surfaces used to carry five copies of `query.trim()` plus a chain of
 * `contains(ignoreCase = true)`. They agreed by accident rather than by
 * construction, and none of them said what happens to a control character, a
 * fifty-thousand-character paste, or a query with two words in it.
 *
 * This file answers those once. It is deliberately small: the search box is a
 * plain-text box, and every temptation to make it more than that — operators,
 * wildcards, field prefixes, a syntax — is refused. A security console's
 * search field is a place where a hostile string arrives for free, so the
 * safest thing it can be is inert.
 *
 * What a query is NOT, and must never become:
 *
 *   a shell         a command interface      a URI router
 *   SQL             a deep-link parser       a query language
 *
 * Nothing here interprets the text. It is normalized, split on whitespace,
 * and used for substring comparison. That is the whole contract.
 */

/**
 * The longest query NEXA will consider.
 *
 * Long enough for a pasted identifier with room to spare, short enough that
 * an unbounded paste cannot turn every keystroke into a scan of a huge
 * string across every record.
 */
const val NEXA_QUERY_MAX_LENGTH = 128

/**
 * The most words a single query may carry.
 *
 * Each token costs one pass over each searchable field, so the cap keeps a
 * pathological query — a hundred words pasted in — from multiplying that
 * work. Tokens beyond the limit are dropped rather than the query being
 * rejected: an operator who pasted a paragraph gets a narrower result, not
 * an error.
 */
const val NEXA_QUERY_MAX_TOKENS = 8

/**
 * A search query, normalized once.
 *
 * [raw] is what the operator typed and is what the field renders — normalizing
 * the visible text under someone's cursor is hostile. [normalized] and
 * [tokens] are what matching uses.
 */
data class NexaQuery(
    val raw: String,
    val normalized: String,
    val tokens: List<String>
) {
    /** Whether this query narrows anything. Whitespace alone does not. */
    val isActive: Boolean get() = tokens.isNotEmpty()

    companion object {
        val Empty = NexaQuery(raw = "", normalized = "", tokens = emptyList())
    }
}

/**
 * Normalizes raw input into a query.
 *
 * Deterministic, and total: every possible string produces a query, and no
 * input path throws. In order:
 *
 *  1. Truncate. The cap is applied first so nothing downstream ever walks an
 *     unbounded string.
 *  2. Neutralize control characters — newline, tab, NUL, the bidi overrides —
 *     by turning them into spaces. They are not stripped silently, because
 *     "a\nb" is two words rather than the word "ab".
 *  3. Collapse whitespace runs and trim, so "  office   printer " and
 *     "office printer" are the same search.
 *  4. Lowercase, for case-insensitive comparison.
 *  5. Split into tokens, capped.
 *
 * Note what is *not* done: punctuation is left alone. A MAC address, an IP,
 * an alert id and a scope are the identifiers operators actually paste, and
 * stripping their separators would make matching surprising in exactly the
 * cases that matter most.
 */
fun nexaQuery(raw: String): NexaQuery {
    if (raw.isEmpty()) return NexaQuery.Empty

    val capped = if (raw.length > NEXA_QUERY_MAX_LENGTH) {
        raw.substring(0, NEXA_QUERY_MAX_LENGTH)
    } else {
        raw
    }

    val builder = StringBuilder(capped.length)
    var lastWasSpace = true
    capped.forEach { char ->
        // Control characters and every flavour of whitespace collapse to one
        // separator. isISOControl covers NUL and the C0/C1 ranges; the
        // explicit isWhitespace covers the exotic spaces.
        val separator = char.isWhitespace() || char.isISOControl()
        if (separator) {
            if (!lastWasSpace) {
                builder.append(' ')
                lastWasSpace = true
            }
        } else {
            builder.append(char)
            lastWasSpace = false
        }
    }

    val normalized = builder.toString().trim().lowercase()
    if (normalized.isEmpty()) return NexaQuery(raw = raw, normalized = "", tokens = emptyList())

    val tokens = normalized.split(' ')
        .filter { it.isNotEmpty() }
        .take(NEXA_QUERY_MAX_TOKENS)

    return NexaQuery(raw = raw, normalized = normalized, tokens = tokens)
}

/**
 * Whether a record whose searchable text is [fields] matches this query.
 *
 * Tokens are ANDed and fields are ORed: every word the operator typed has to
 * appear somewhere in the record, but not all in the same field. That is what
 * makes "printer office" find "Office Printer", and "laptop 00:1a" find a
 * device by name and address at once.
 *
 * An inactive query matches everything — search that is not being used must
 * never remove a record.
 *
 * Null fields are skipped rather than treated as empty strings, so an absent
 * IP is absent rather than matching the empty query case.
 */
fun NexaQuery.matches(fields: List<String?>): Boolean {
    if (!isActive) return true
    return tokens.all { token ->
        fields.any { field -> field != null && field.contains(token, ignoreCase = true) }
    }
}

/** Convenience for the common call shape. Same semantics as [matches]. */
fun NexaQuery.matches(vararg fields: String?): Boolean = matches(fields.asList())
