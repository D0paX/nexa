package com.example.nexa.ui.common

/**
 * One filter language for the whole app.
 *
 * The composition rules are the important part, and they are written down
 * here rather than re-derived in each domain's predicate:
 *
 *   BETWEEN FACETS:            AND
 *   WITHIN A MULTI-SELECT:     OR
 *
 * So "Presence = Present" and "Trust = Trusted" together mean both must hold,
 * while "Severity = Critical or Danger" means either will do. An operator who
 * learns this on Devices knows it on Alerts.
 *
 * The second rule that matters is what an *empty* selection means. It means
 * "this facet is not narrowing anything" — never "nothing matches". A filter
 * nobody has touched must not be able to empty a list.
 *
 * None of this is a security boundary. A record excluded by a filter still
 * exists, is still whatever it was, and is still subject to exactly the same
 * authorization rules if it is acted upon. Filtering changes what is visible.
 * It never changes what is true.
 */

/**
 * OR within a facet.
 *
 * An empty selection matches everything; a non-empty one matches only its
 * members. A null value cannot satisfy a narrowing facet — a record with no
 * scope is not in "scope = VLAN_SECURE".
 */
fun <T> Set<T>.facetMatches(value: T?): Boolean =
    isEmpty() || (value != null && value in this)

/**
 * Adds or removes one value from a facet selection.
 *
 * Shared so every sheet toggles identically — four private copies of this had
 * grown across the filter sheets, and a fifth was about to.
 */
fun <T> Set<T>.toggleFacet(value: T): Set<T> =
    if (value in this) this - value else this + value

/**
 * How an active filter set is announced above a list.
 *
 * One wording everywhere: bare when nothing is selected, and carrying the
 * number of individual selections when something is. Counting *selections*
 * rather than facets is what an operator expects — picking two severities
 * reads as two filters, because two things were chosen.
 *
 * Search is deliberately not counted here. Search has its own visible field
 * and its own clear affordance, and folding it into this number would make
 * "Clear filters" look like it should also clear the text.
 */
fun filterButtonLabel(activeCount: Int): String =
    if (activeCount <= 0) "Filters" else "Filters · $activeCount"
