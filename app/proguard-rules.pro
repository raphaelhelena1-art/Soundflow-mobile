# ProGuard / R8 Rules for SoundFlow

# Ignore missing Java SE desktop and scripting classes referenced by Rhino (Mozilla JavaScript engine)
# Rhino is used by NewPipeExtractor for signature / stream extraction on Android.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**

# Keep Mozilla Rhino JavaScript Engine classes
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.tools.**

# Keep NewPipeExtractor models and extractor classes
-keep class org.schabi.newpipe.extractor.** { *; }
