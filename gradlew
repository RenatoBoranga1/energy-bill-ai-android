#!/usr/bin/env sh
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION=8.2
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$DIR/.gradle-user}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$DIR/.android}"
mkdir -p "$ANDROID_USER_HOME"
export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.native=false -Dorg.gradle.vfs.watch=false}"
GRADLE_JVMARGS="${GRADLE_JVMARGS:--Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8}"
WRAPPER_DIR="$GRADLE_USER_HOME/wrapper/dists"
FALLBACK_WRAPPER_DIR="$HOME/.gradle/wrapper/dists"

GRADLE_CMD=$(find "$WRAPPER_DIR/gradle-${GRADLE_VERSION}-bin" -path "*/gradle-${GRADLE_VERSION}/bin/gradle" -type f 2>/dev/null | head -n 1)

if [ -z "$GRADLE_CMD" ]; then
  GRADLE_CMD=$(find "$WRAPPER_DIR" -path "*/gradle-*/bin/gradle" -type f 2>/dev/null | head -n 1)
fi

if [ -z "$GRADLE_CMD" ]; then
  GRADLE_CMD=$(find "$FALLBACK_WRAPPER_DIR/gradle-${GRADLE_VERSION}-bin" -path "*/gradle-${GRADLE_VERSION}/bin/gradle" -type f 2>/dev/null | head -n 1)
fi

if [ -z "$GRADLE_CMD" ]; then
  GRADLE_CMD=$(find "$FALLBACK_WRAPPER_DIR" -path "*/gradle-*/bin/gradle" -type f 2>/dev/null | head -n 1)
fi

if [ -z "$GRADLE_CMD" ]; then
  echo "Gradle distribution not found in ${WRAPPER_DIR} or ${FALLBACK_WRAPPER_DIR}."
  exit 1
fi

exec "$GRADLE_CMD" "-Dorg.gradle.jvmargs=${GRADLE_JVMARGS}" -Dkotlin.daemon.jvm.options=-Xmx2g -Dorg.gradle.workers.max=2 --no-daemon "$@"
