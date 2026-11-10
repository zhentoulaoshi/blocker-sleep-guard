#!/usr/bin/env sh
#
# Gradle wrapper start script (minimal, POSIX).
# The binary gradle-wrapper.jar is not stored in this archive; generate it once
# with `gradle wrapper --gradle-version 8.6` or use Android Studio / the
# GitHub Actions workflow in .github/workflows/build-apk.yml.
#
set -e

APP_HOME=$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing." >&2
    echo "Run:  gradle wrapper --gradle-version 8.6 --distribution-type bin" >&2
    echo "Or open the project in Android Studio (it restores the wrapper)." >&2
    echo "Or use .github/workflows/build-apk.yml to build the APK in the cloud." >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

exec "$JAVACMD" \
    ${DEFAULT_JVM_OPTS:-} ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
