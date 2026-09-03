package com.example.nexa.ui.common

/**
 * How NEXA talks about resolving data.
 *
 * The availability vocabulary in [NexaAvailability] already has a Loading
 * value, and this file does not add a second one. What it adds is the
 * distinction the vocabulary deliberately does not carry: whether a load is
 * the *first* one, with nothing on screen yet, or a revalidation happening
 * over content the operator is already reading.
 *
 * That difference is presentational, not semantic — both are "NEXA is
 * resolving data" — but treating them identically produces two bad outcomes:
 *
 *  - A refresh blanks a screen someone was reading, and the list they had is
 *    replaced by a spinner for no reason they can see.
 *  - The search and filters they set are thrown away with it, so a retry
 *    quietly undoes their work at the moment they most want it kept.
 *
 * So content states carry a `refreshing` flag, and the presentation context —
 * query, filters, sort — is carried across a reload rather than reset by it.
 */

/**
 * What a screen was showing before a reload, so a reload can put it back.
 *
 * Held by the view model rather than by the state, because it has to survive
 * the state becoming an error: the whole point is that an operator who was
 * searching for a device when the inventory failed still has that search when
 * the retry succeeds.
 *
 * Deliberately presentation only. Nothing security-relevant is remembered
 * across a failed load — not a target, not an authorization, not an
 * eligibility. Restoring a query is restoring a question; restoring anything
 * else would be restoring an answer nobody re-checked.
 */
data class NexaPresentation<F, S>(
    val query: String,
    val filters: F,
    val sort: S
)
