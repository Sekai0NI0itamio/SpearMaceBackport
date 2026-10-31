#!/usr/bin/env sh
# Forge 1.12.2 / ForgeGradle 2.3 note: this repo does NOT vendor
# gradle-wrapper.jar. Cloud builds use system Gradle 4.9
# (see .github/workflows/build.yml). Local builds are forbidden
# by repo rule anyway — everything compiles on GitHub Actions.
if [ -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"
else
  echo "No gradle-wrapper.jar in repo (by design). Use GitHub Actions, or: gradle build (Gradle 4.9, Java 8)." >&2
  if command -v gradle >/dev/None 2>&1; then
    exec gradle "$@"
  else
    exit 1
  fi
fi
