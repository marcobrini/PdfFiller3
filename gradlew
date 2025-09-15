#!/usr/bin/env sh
# Gradle wrapper startup script
APP_BASE_NAME=${0##*/}
DIRNAME=$(cd "$(dirname "$0")" && pwd)
DEFAULT_JVM_OPTS=""
CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar
JAVACMD=java
exec "$JAVACMD" $DEFAULT_JVM_OPTS -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
