# Friday ProGuard Rules

# Keep all Friday classes
-keep class com.friday.assistant.** { *; }

# Speech recognition
-keep class android.speech.** { *; }

# TTS
-keep class android.speech.tts.** { *; }

# Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.Insert *;
    @androidx.room.Query *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
}
