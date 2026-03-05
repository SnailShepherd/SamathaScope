This zip does NOT include gradle-wrapper.jar.

If Android Studio complains it's missing:
- easiest fix: create a fresh empty Android project in the same Android Studio install.
  It will create gradle/wrapper/gradle-wrapper.jar. Copy that file into:
    SamathaScope/gradle/wrapper/gradle-wrapper.jar
Then sync again.
