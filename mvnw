#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script (jar-based, simplified)
# ----------------------------------------------------------------------------
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$DIR" \
  "$WRAPPER_LAUNCHER" "$@"
