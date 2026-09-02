package com.example.nexa.ui.workflow

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INSTRUMENTED — the attack surface the operating system sees.
 *
 * Everything else about this app's security is a property of its own code.
 * This is the part the platform enforces on its behalf: which components
 * another application can reach, what leaves the device in a backup, and
 * whether anything is allowed to talk in the clear.
 *
 * It has to run on a device because there is no manifest on the JVM. Reading
 * the file as XML would test a file; reading it through [PackageManager]
 * tests what was actually installed, after the merge, which is where a
 * library's own manifest contributions show up too.
 */
class ManifestSurfaceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val packageInfo
        get() = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS
        )

    /** Components NEXA declares itself, as opposed to those a library added. */
    private fun ours(name: String?) = name != null && name.startsWith("com.example.nexa")

    // ============================================================
    // WHAT IS REACHABLE FROM OUTSIDE
    // ============================================================

    /**
     * One activity is exported, and it is the one with a launcher icon. An
     * exported activity is a door; this asserts NEXA has exactly the door it
     * meant to have.
     */
    @Test
    fun only_the_launcher_activity_is_exported() {
        val exported = packageInfo.activities.orEmpty()
            .filter { ours(it.name) && it.exported }
            .map { it.name }

        assertEquals(
            "the exported activity surface changed: $exported",
            listOf("com.example.nexa.MainActivity"),
            exported
        )
    }

    /**
     * The messaging service is bound by the Firebase library inside this same
     * process. Nothing outside needs to reach it, so nothing outside can.
     */
    @Test
    fun the_messaging_service_is_not_exported() {
        val service = packageInfo.services.orEmpty()
            .firstOrNull { it.name == "com.example.nexa.push.NexaMessagingService" }
        assertNotNull("the messaging service is missing entirely", service)
        assertFalse("the messaging service was exported", service!!.exported)
    }

    /**
     * Every exported receiver is gated on a permission an ordinary
     * application cannot hold.
     *
     * The debug test receiver is the reason this exists. It injects push
     * fixtures, replays realtime scenarios and switches the app between
     * availability conditions, and it shipped exported with no permission at
     * all — reachable by any application on the same device. It is a debug
     * build's component and never reaches a release, but a security console
     * is a strange place to leave that lying about even so.
     *
     * DUMP is the gate: signature-or-privileged, so a third-party app cannot
     * be granted it, while the adb shell already holds it and the test route
     * stays open.
     */
    @Test
    fun every_exported_receiver_of_ours_requires_a_privileged_permission() {
        val exported = packageInfo.receivers.orEmpty().filter { ours(it.name) && it.exported }

        exported.forEach { receiver ->
            val permission = receiver.permission
            assertNotNull(
                "${receiver.name} is exported with no permission at all",
                permission
            )
            val info = context.packageManager.getPermissionInfo(permission!!, 0)
            val protection = info.protection
            val flags = info.protectionFlags
            val privileged =
                protection == android.content.pm.PermissionInfo.PROTECTION_SIGNATURE ||
                    (flags and android.content.pm.PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0
            assertTrue(
                "${receiver.name} is gated on $permission, which an ordinary app can hold",
                privileged
            )
        }
    }

    /** NEXA declares no content provider, so none of its data has a URI. */
    @Test
    fun we_publish_no_content_provider() {
        val provider = packageInfo.providers.orEmpty().filter { ours(it.name) }
        assertTrue("NEXA declared a content provider: ${provider.map { it.name }}", provider.isEmpty())
    }

    // ============================================================
    // WHAT LEAVES THE DEVICE
    // ============================================================

    /**
     * Nothing is backed up, because nothing is stored. A prepared action
     * confirmation that came back after a restore would be one nobody
     * re-authorized.
     */
    @Test
    fun backup_is_off() {
        val flags = context.applicationInfo.flags
        assertEquals(
            "the application allows backup",
            0,
            flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        )
    }

    /**
     * And no cleartext, stated rather than inherited. The platform default
     * for this target level already refuses it; the point of asserting it is
     * that a future transport cannot quietly arrive without one.
     */
    @Test
    fun cleartext_traffic_is_refused() {
        assertFalse(
            "the application permits cleartext traffic",
            context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0
        )
    }

    // ============================================================
    // WHAT IS WRITTEN DOWN
    // ============================================================

    /**
     * The app stores nothing. Every piece of state it holds lives in memory
     * and dies with the process, which is what makes process death fail
     * closed rather than merely inconvenient.
     *
     * Asserted against the real directories rather than by reading the source,
     * because the interesting version of this failure is a library quietly
     * writing something.
     */
    @Test
    fun no_security_state_is_written_to_disk_by_us() {
        val dataDir = context.applicationInfo.dataDir
        val onDisk = listOf(context.filesDir, context.cacheDir)
            .flatMap { it.walkTopDown().filter { file -> file.isFile }.toList() }
            // Relative, so the assertion is about what was written rather than
            // about the package name every path under the data directory has.
            .map { it.absolutePath.removePrefix(dataDir).trimStart('/') }

        // androidx.profileinstaller writes a zero-byte marker recording that
        // the baseline profile was applied. It is the only file the app has,
        // it carries no NEXA state, and naming it here means a second file
        // appearing fails this test rather than widening a filter.
        val allowed = setOf("files/profileInstalled")

        assertEquals(
            "something other than the profile marker was written to disk",
            emptyList<String>(),
            (onDisk - allowed).sorted()
        )
    }

    /** And no shared preferences file of ours exists either. */
    @Test
    fun we_keep_no_shared_preferences() {
        val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        val files = prefsDir.listFiles().orEmpty().map { it.name }
            .filterNot { it.contains("profileinstaller", ignoreCase = true) }
        assertTrue("NEXA wrote shared preferences: $files", files.isEmpty())
    }
}
