package com.example.nexa.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.example.nexa.ActionConfirmation
import com.example.nexa.Alerts
import com.example.nexa.Audit
import com.example.nexa.Devices
import com.example.nexa.Overview

/**
 * Where a navigation is going, spatially.
 *
 * Direction is resolved here as pure data — no strings compared, no animation
 * imported, nothing decided inside a composable. A transition then reads the
 * direction and picks a spec for it, which is what keeps NEXA's motion from
 * degenerating into a scatter of per-screen animations that disagree with one
 * another about which way "forward" is.
 */

// ============================================================
// TIERS — how deep into the application a destination sits
// ============================================================

/**
 * The three spatial planes of the application.
 *
 * Root destinations sit side by side; details sit in front of the root that
 * opened them; a confirmation sits in front of everything, because it is the
 * surface where an operator commits to something.
 */
enum class NavigationTier(val depth: Int) {
    Root(0),
    Detail(1),
    Modal(2)
}

// ============================================================
// DIRECTION
// ============================================================

enum class NavigationDirection {
    /** Nothing moved: first composition, or the same destination again. */
    None,

    /** Toward a higher-index root tab — Overview to Devices. */
    RootForward,

    /** Toward a lower-index root tab — Audit to Alerts. */
    RootReverse,

    /** Into a detail surface. */
    DrillForward,

    /** Back out of a detail surface. */
    DrillBack,

    /** Into an action confirmation. */
    ModalForward,

    /** Back out of an action confirmation. */
    ModalBack;

    /** Whether the destination is moving in from the leading edge. */
    val isForward: Boolean
        get() = this == RootForward || this == DrillForward || this == ModalForward

    /** Which motion family this direction belongs to. */
    val tier: NavigationTier
        get() = when (this) {
            RootForward, RootReverse, None -> NavigationTier.Root
            DrillForward, DrillBack -> NavigationTier.Detail
            ModalForward, ModalBack -> NavigationTier.Modal
        }
}

// ============================================================
// DESTINATION ORDERING
// ============================================================

/**
 * The canonical root order: Overview, Devices, Alerts, Audit.
 *
 * Used for nothing except deciding which way a root transition travels. It is
 * not a permission order, a priority order, or a security ordering of any
 * kind.
 *
 * Returns null for anything that is not a root destination.
 */
fun navigationIndex(key: NavKey?): Int? = when (key) {
    Overview -> 0
    Devices -> 1
    Alerts -> 2
    Audit -> 3
    else -> null
}

fun isRootDestination(key: NavKey?): Boolean = navigationIndex(key) != null

/**
 * Which plane a destination lives on.
 *
 * Anything that is neither a root tab nor a confirmation is a detail surface —
 * so a destination added later animates sensibly without being registered
 * here first.
 */
fun navigationTier(key: NavKey?): NavigationTier = when {
    isRootDestination(key) -> NavigationTier.Root
    key is ActionConfirmation -> NavigationTier.Modal
    else -> NavigationTier.Detail
}

// ============================================================
// DIRECTION RESOLUTION
// ============================================================

/**
 * The single decision about which way a navigation travels.
 *
 * Root-to-root is decided by index, so moving between tabs feels like moving
 * along a row whichever gesture caused it. Between planes, direction follows
 * depth: entering a deeper plane is forward, leaving it is back.
 *
 * [popping] resolves the one case depth cannot: a detail surface opening
 * another detail surface (Identities to Identity Detail) looks identical to
 * returning from one, so the back stack shrinking is what tells them apart.
 */
fun navigationDirection(
    from: NavKey?,
    to: NavKey?,
    popping: Boolean = false
): NavigationDirection {
    if (to == null) return NavigationDirection.None

    // First composition has nothing to travel from, and should not animate as
    // if it did.
    if (from == null) return NavigationDirection.None
    if (from == to) return NavigationDirection.None

    val fromTier = navigationTier(from)
    val toTier = navigationTier(to)

    // Same plane.
    if (fromTier == toTier) {
        if (fromTier == NavigationTier.Root) {
            val fromIndex = navigationIndex(from) ?: return NavigationDirection.None
            val toIndex = navigationIndex(to) ?: return NavigationDirection.None
            return if (toIndex > fromIndex) {
                NavigationDirection.RootForward
            } else {
                NavigationDirection.RootReverse
            }
        }
        val family = if (fromTier == NavigationTier.Modal) {
            NavigationDirection.ModalForward to NavigationDirection.ModalBack
        } else {
            NavigationDirection.DrillForward to NavigationDirection.DrillBack
        }
        return if (popping) family.second else family.first
    }

    // Changing plane. The deeper of the two planes owns the motion: entering a
    // confirmation from anywhere is a modal move, and leaving one is a modal
    // move back, whatever the other end happened to be.
    val deeper = if (toTier.depth > fromTier.depth) toTier else fromTier
    val forward = toTier.depth > fromTier.depth

    return when (deeper) {
        NavigationTier.Modal ->
            if (forward) NavigationDirection.ModalForward else NavigationDirection.ModalBack
        NavigationTier.Detail ->
            if (forward) NavigationDirection.DrillForward else NavigationDirection.DrillBack
        NavigationTier.Root -> NavigationDirection.None
    }
}

/**
 * Tracks where navigation came from so a direction can be resolved.
 *
 * Deliberately a plain class rather than composable state: the resolution is
 * ordinary logic and is unit-tested as such. [resolve] is called once per
 * destination change, and remembers what it saw for the next one.
 */
class NavigationDirectionResolver {

    private var previousKey: NavKey? = null
    private var previousDepth: Int = 0

    /** [depth] is the back stack size, which tells a pop from a push. */
    fun resolve(current: NavKey?, depth: Int): NavigationDirection {
        val direction = navigationDirection(
            from = previousKey,
            to = current,
            popping = depth < previousDepth
        )
        previousKey = current
        previousDepth = depth
        return direction
    }
}
