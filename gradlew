#!/usr/bin/env sh

#
# UploadGo build launcher.
#
# This is a self-sufficient Gradle wrapper: it uses the checked-in
# gradle-wrapper.jar when it exists, and otherwise downloads the Gradle
# distribution named in gradle/wrapper/gradle-wrapper.properties and runs it
# directly. This keeps `./gradlew` working even on fresh checkouts that have
# not yet regenerated the binary wrapper jar.
#

set -eu

# Resolve the application home directory.
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$PRG")" && pwd -P)

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
PROPERTIES_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# ---------------------------------------------------------------------------
# Path 1: standard wrapper jar is present — behave exactly like Gradle's script.
# ---------------------------------------------------------------------------
if [ -f "$WRAPPER_JAR" ]; then
  # Determine the Java command to use to start the JVM.
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
  else
    JAVACMD=java
  fi
  if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    echo "Please install a JDK (17+) and set JAVA_HOME." >&2
    exit 1
  fi
  exec "$JAVACMD" \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
fi

# ---------------------------------------------------------------------------
# Path 2: no wrapper jar — download and run the Gradle distribution directly.
# ---------------------------------------------------------------------------
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | tr -d '\r' | tr -d '\\')
if [ -z "$DIST_URL" ]; then
  echo "ERROR: could not read distributionUrl from $PROPERTIES_FILE" >&2
  exit 1
fi

DIST_VERSION=$(basename "$DIST_URL" | sed -E 's/^gradle-(.*)-(bin|all)\.zip$/\1/')
if [ -z "$DIST_VERSION" ]; then
  echo "ERROR: could not determine Gradle version from $DIST_URL" >&2
  exit 1
fi

GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/uploadgo-bootstrap/$DIST_VERSION"
GRADLE_BIN="$DIST_DIR/gradle-$DIST_VERSION/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  ZIP_PATH="$DIST_DIR/gradle.zip"
  echo "gradlew: downloading Gradle $DIST_VERSION from $DIST_URL"
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --silent --show-error "$DIST_URL" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$ZIP_PATH" "$DIST_URL"
  else
    echo "ERROR: neither curl nor wget is available to download Gradle." >&2
    exit 1
  fi
  echo "gradlew: extracting Gradle distribution"
  (cd "$DIST_DIR" && unzip -q -o "$ZIP_PATH")
fi

if [ ! -x "$GRADLE_BIN" ]; then
  echo "ERROR: Gradle extraction failed; expected $GRADLE_BIN" >&2
  exit 1
fi

exec "$GRADLE_BIN" "$@"
