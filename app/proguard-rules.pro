# ScrollKill R8 / ProGuard keep rules.
#
# STATUS: R8 is currently OFF (app/build.gradle.kts -> release.optimization.enable = false).
# These rules only take effect once it is switched on (checklist E4 / Session 11). When
# enabling R8, run a full pass against a device with a minified release APK and add a rule
# here for anything that gets stripped or renamed.
#
# Most dependencies (Room, Compose, DataStore, coroutines) ship their own consumer rules,
# so the list below is deliberately small - defensive keeps for the reflection this app
# actually does, plus the Room schema classes.

# --- Enums restored from DataStore / Room by name -------------------------------------
# SettingsRepository and SessionRecord do `runCatching { SomeEnum.valueOf(storedName) }`.
# proguard-android-optimize.txt already keeps values()/valueOf(String) on enums; pinned
# here so a future change to the default config cannot silently break settings parsing.
-keepclassmembers enum ** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Room ---------------------------------------------------------------------------
# room-runtime ships consumer rules for the @Database / @Dao / @Entity codegen. The
# schema types are only ever touched by generated code, so keep them and their members
# as a backstop against aggressive shrinking.
-keep class com.ikasle.scrollkill.data.session.SessionEntity { *; }
-keep class com.ikasle.scrollkill.data.session.ScrollKillDatabase { *; }
-keep class com.ikasle.scrollkill.data.session.** extends androidx.room.RoomDatabase { *; }

# --- Jetpack Compose --------------------------------------------------------------
# compose-runtime / compose-ui ship consumer rules; nothing extra is needed today.
# If release UI loses composables, add targeted -keep rules for the affected files here.

# --- DataStore Preferences ------------------------------------------------------------
# datastore-preferences ships consumer rules for its internal protobuf-lite types;
# this only suppresses a known reflective-access warning from that transitive code.
-dontwarn com.google.protobuf.**
