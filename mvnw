#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Required ENV vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# -----------------
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true ;;
  Darwin*) darwin=true
    # Use /usr/libexec/java_home if available, otherwise fall back to /Library/Java/Home
    # See https://developer.apple.com/library/mac/qa/qa1170/_index.html
    if [ -z "$JAVA_HOME" ]; then
      if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME="$(/usr/libexec/java_home)"; export JAVA_HOME
      else
        JAVA_HOME="/Library/Java/Home"; export JAVA_HOME
      fi
    fi
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=$(java-config --jre-home)
  fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --unix "$JAVA_HOME")
fi

# For Mingw, ensure paths are in UNIX format before anything is touched
if $mingw ; then
  [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME" ] &&
    JAVA_HOME="$(cd "$JAVA_HOME" || (echo "cannot cd to $JAVA_HOME."; exit 1); pwd -W)"
fi

if [ -z "$JAVA_HOME" ]; then
  javaExecutable="$(which javac)"
  if [ -n "$javaExecutable" ] && ! [ "$(expr "\"$javaExecutable\"" : '\([^ ]*\)')" = "no" ]; then
    # readlink(1) is not available as standard on Solaris 10.
    readLink=$(which readlink)
    if [ ! "$(expr "$readLink" : '\([^ ]*\)')" = "no" ]; then
      if $darwin ; then
        javaHome="$(dirname "\"$javaExecutable\"")"
        javaExecutable="$(cd "\"$javaHome\"" && pwd -P)/javac"
      else
        javaExecutable="$(readlink -f "\"$javaExecutable\"")"
      fi
      javaHome="$(dirname "\"$javaExecutable\"")"
      javaHome=$(expr "$javaHome" : '\(.*\)/bin')
      JAVA_HOME="$javaHome"
      export JAVA_HOME
    fi
  fi
fi

if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/javac" ]; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/javac"
    fi
    if [ ! -x "$JAVACMD" ] ; then
      echo "Error: JAVA_HOME is set to an invalid directory: $JAVA_HOME

We need $JAVA_HOME/bin/javac and $JAVA_HOME/bin/java to exist.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation." >&2
      exit 1
    fi
  else
    JAVACMD="$(\which javac)"
    if [ -z "$JAVACMD" ] ; then
      JAVACMD="$(\which java)"
    fi
    if [ ! -x "$JAVACMD" ] ; then
      echo "Error: JAVA_HOME is not set and 'javac' or 'java' could not be found in the PATH." >&2
      exit 1
    fi
  fi
fi

# increase maximum file descriptors if possible
if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] && [ "$mingw" = "false" ] ; then
  MAX_FD_LIMIT="$(ulimit -H -n)"
  if [ $? -eq 0 ] ; then
    if [ -n "$MAVEN_OPTS" ] && [ "${MAVEN_OPTS#*-Xmx}" != "$MAVEN_OPTS" ] ; then
      # MAVEN_OPTS already contains -Xmx, no need to set max FD
      :
    else
      MAX_FD="maximum"
    fi
    if [ -n "$MAX_FD" ] && [ "$MAX_FD" != "maximum" ] ; then
      :
    elif [ -n "$MAX_FD_LIMIT" ] && [ "$MAX_FD_LIMIT" -gt 0 ] ; then
      MAX_FD="$MAX_FD_LIMIT"
    fi
    if [ -n "$MAX_FD" ] ; then
      ulimit -n $MAX_FD
      if [ $? -ne 0 ] ; then
        echo "Could not set maximum file descriptor limit: $MAX_FD" >&2
      fi
    fi
  fi
fi

# Resolve the project base directory, i.e. the directory containing the .mvn folder.
# The MAVEN_PROJECTBASEDIR is used to locate the maven-wrapper.jar and to set the
# maven.multiModuleProjectDirectory system property.
find_maven_basedir() {
  if [ -z "$1" ]; then
    echo "Path cannot be empty" >&2
    exit 1
  fi
  local basedir="$1"
  local wdir="$basedir"
  while [ "$wdir" != '/' ] ; do
    if [ -d "$wdir"/.mvn ] ; then
      basedir=$wdir
      break
    fi
    wdir=$(cd "$wdir/.." || exit 1; pwd)
  done
  printf '%s' "$basedir"
}

# Concatenate the MAVEN_OPTS and MAVEN_ARGS environment variables into the java command.
MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $*"

# Provide a "BASEDIR" that is the directory of this script, resolving symlinks.
# Use of printf is necessary because the path could contain spaces.
BASEDIR=$(find_maven_basedir "$(pwd)")
# Resolve the directory of this script (handles symlinks)
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")"/$link"
  fi
done
SAVED="`pwd`"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

MAVEN_PROJECTBASEDIR=${MAVEN_BASEDIR:-"$(find_maven_basedir "$(pwd)")"}
export MAVEN_PROJECTBASEDIR

# OS specific support.  $var _must_ be set to either true or false.
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Download the maven-wrapper.jar if it does not exist yet.
if [ ! -e "$MAVEN_WRAPPER_JAR" ] ; then
  if [ ! -e "$MAVEN_WRAPPER_PROPERTIES" ] ; then
    echo "Error: Cannot find $MAVEN_WRAPPER_PROPERTIES" >&2
    exit 1
  fi
  # Read the wrapperUrl from the properties file
  wrapperUrl=$(sed -n 's/^wrapperUrl=\(.*\)/\1/p' "$MAVEN_WRAPPER_PROPERTIES" | head -n1)
  if [ -z "$wrapperUrl" ] ; then
    echo "Error: wrapperUrl is not set in $MAVEN_WRAPPER_PROPERTIES" >&2
    exit 1
  fi
  echo "Downloading Maven Wrapper from $wrapperUrl ..."
  if command -v curl > /dev/null 2>&1 ; then
    curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$wrapperUrl"
    if [ $? -ne 0 ] ; then
      echo "Error: Failed to download Maven Wrapper jar." >&2
      exit 1
    fi
  elif command -v wget > /dev/null 2>&1 ; then
    wget -q -O "$MAVEN_WRAPPER_JAR" "$wrapperUrl"
    if [ $? -ne 0 ] ; then
      echo "Error: Failed to download Maven Wrapper jar." >&2
      exit 1
    fi
  else
    echo "Error: Need curl or wget to download the Maven Wrapper jar." >&2
    exit 1
  fi
fi

# shellcheck disable=SC2086
exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
