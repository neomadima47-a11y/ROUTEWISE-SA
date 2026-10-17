# Proguard rules for RouteWise SA
-keep class com.routewise.sa.data.model.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
