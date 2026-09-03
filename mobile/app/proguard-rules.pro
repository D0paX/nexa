# NEXA release shrinking rules.
#
# The file the release build type has always pointed at, and which did not
# exist until R8 was switched on and asked for it.
#
# It is deliberately close to empty. Every library that needs its own names
# preserved ships the rules for them alongside itself — Compose, Firebase
# Messaging, Navigation3, the serialization runtime — and R8 reads those
# without being told. A keep rule written here would be this project claiming
# to know better than the library about which of its own classes survive.
#
# What is kept below is only what NEXA itself does reflectively, which is one
# thing. Anything added here later should name the path that failed and why,
# because a rule nobody can justify is a rule nobody will ever dare remove.

# --- Navigation keys ---
#
# The back stack is saved and restored through kotlinx.serialization: each
# destination is written into saved state as its serial name and read back on
# the other side of a process death or a configuration change. The generated
# serializers are found by name, so the key classes and their companions have
# to keep theirs.
#
# Without this the app still builds, still launches and still navigates —
# and then loses the operator's place the first time Android recreates the
# activity, which is the failure this project has already fixed once from the
# other direction.
-keepclassmembers class com.example.nexa.** {
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.nexa.**$$serializer { *; }

# --- Diagnostics ---
#
# Line numbers, so a crash from the field can be read at all, and the source
# file attribute renamed rather than kept, so the mapping is still needed to
# make sense of it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
