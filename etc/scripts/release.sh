#!/bin/bash
#
# Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -o pipefail || true  # trace ERR through pipes
set -o errtrace || true # trace ERR through commands and functions
set -o errexit || true  # exit the script if any statement returns a non-true return value

on_error(){
    CODE="${?}" && \
    set +x && \
    printf "[ERROR] Error(code=%s) occurred at %s:%s command: %s\n" \
        "${CODE}" "${BASH_SOURCE}" "${LINENO}" "${BASH_COMMAND}"
}
trap on_error ERR

usage(){
  cat <<EOF

DESCRIPTION: Helidon Release Script

USAGE:

$(basename ${0}) [ --promoted ] [ --build-number=N ] CMD

  --promoted
        Perform a promoted release.
        The version will include a build number qualifier in the of '-b.N'
        See also --build-number=N

  --build-number=N
        Set the build number for promoted release.
        Works only with --promoted

  --version=V
        Override the version to use.
        This trumps --build-number=N

  --help
        Prints the usage and exits.

  CMD:

    update_version
        Update the version in the workspace

    release_build
        Perform a release build
        This will create a local branch, deploy artifacts and push a tag

EOF
}

# parse command line args
ARGS=( "${@}" )
for ((i=0;i<${#ARGS[@]};i++))
{
  ARG=${ARGS[${i}]}
  case ${ARG} in
  "--promoted")
    readonly PROMOTED=true
    ;;
  "--build-number="*)
    readonly BUILD_NUMBER=${ARG#*=}
    ;;
  "--version="*)
    VERSION=${ARG#*=}
    ;;
  "--help")
    usage
    exit 0
    ;;
  *)
    if [ "${ARG}" = "update_version" ] || [ "${ARG}" = "release_build" ] ; then
      readonly COMMAND="${ARG}"
    else
      echo "ERROR: unknown argument: ${ARG}"
      exit 1
    fi
    ;;
  esac
}

if [ -z "${COMMAND}" ] ; then
  echo "ERROR: no command provided"
  usage
  exit 1
fi

# Path to this script
if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "${0}")"
else
  readonly SCRIPT_PATH="${0}"
fi

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

# Hooks for version substitution work
readonly PREPARE_HOOKS=( )

# Hooks for deployment work
readonly PERFORM_HOOKS=( ${WS_DIR}/examples/quickstarts/archetypes/deploy-archetypes.sh )

source ${WS_DIR}/etc/scripts/pipeline-env.sh

if [ "${WERCKER}" = "true" -o "${GITLAB}" = "true" ] ; then
  apt-get update && apt-get -y install graphviz
fi

# Resolve FULL_VERSION
if [ -z ${VERSION+x} ]; then

    # get maven version
    MVN_VERSION=$(mvn \
        -q \
        -f ${WS_DIR}/pom.xml \
        -Dexec.executable="echo" \
        -Dexec.args="\${project.version}" \
        --non-recursive \
        org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

    # strip qualifier
    readonly VERSION=${MVN_VERSION%-*}

    # resolve promoted qualifier
    if [ -n "${PROMOTED}" ] ; then

      # resolve build number
      if [ -z "${BUILD_NUMBER}" ] ; then

        # x.y.z-b.<number> pattern
        LAST_BUILD_NUMBER=$(git tag -l | \
                            (grep ${VERSION}-b || echo "${VERSION}-b.0";) | \
                            cut -f2- -db | \
                            cut -f2- -d '.' | \
                            sort -g | \
                            tail -n1)
        # next build number
        BUILD_NUMBER=$(( ${LAST_BUILD_NUMBER} + 1 ))
      fi

      readonly FULL_VERSION=${VERSION}-b.${BUILD_NUMBER}
    else
      readonly FULL_VERSION=${VERSION}
    fi
  else
    readonly FULL_VERSION=${VERSION}
fi

export FULL_VERSION
printf "\n%s: FULL_VERSION=%s\n\n" "$(basename ${0})" "${FULL_VERSION}"

update_version(){
  # Update version
  mvn -f ${WS_DIR}/parent/pom.xml versions:set versions:set-property \
    -DgenerateBackupPoms=false \
    -DnewVersion="${FULL_VERSION}" \
    -Dproperty=helidon.version \
    -DprocessAllModules=true

  # Hack to update helidon.version
  for pom in `egrep "<helidon.version>.*</helidon.version>" -r . --include pom.xml | cut -d ':' -f 1 | sort | uniq `
  do
      cat ${pom} | \
          sed -e s@'<helidon.version>.*</helidon.version>'@"<helidon.version>${FULL_VERSION}</helidon.version>"@g \
          > ${pom}.tmp
      mv ${pom}.tmp ${pom}
  done

  # Hack to update helidon.version in build.gradle files
  for bfile in `egrep "helidonversion = .*" -r . --include build.gradle | cut -d ':' -f 1 | sort | uniq `
  do
      cat ${bfile} | \
          sed -e s@'helidonversion = .*'@"helidonversion = \'${FULL_VERSION}\'"@g \
          > ${bfile}.tmp
      mv ${bfile}.tmp ${bfile}
  done

  # Invoke prepare hook
  if [ -n "${PREPARE_HOOKS}" ]; then
    for prepare_hook in ${PREPARE_HOOKS} ; do
      bash "${prepare_hook}"
    done
  fi
}

release_build(){
    # Inject credentials in CI env
    inject_credentials

    # Do the release work in a branch
    local GIT_BRANCH="release/${FULL_VERSION}"
    git branch -D "${GIT_BRANCH}" > /dev/null 2>&1 || true
    git checkout -b "${GIT_BRANCH}"

    # Invoke update_version
    update_version

    # Update scm/tag entry in the parent pom
    cat parent/pom.xml | \
        sed -e s@'<tag>HEAD</tag>'@"<tag>${FULL_VERSION}</tag>"@g \
        > parent/pom.xml.tmp
    mv parent/pom.xml.tmp parent/pom.xml

    # Git user info
    git config user.email || git config --global user.email "info@helidon.io"
    git config user.name || git config --global user.name "Helidon Robot"

    # Commit version changes
    git commit -a -m "Release ${FULL_VERSION} [ci skip]"

    # Create the nexus staging repository
    local STAGING_DESC="Helidon v${FULL_VERSION}"
    mvn nexus-staging:rc-open \
      -DstagingProfileId=6026dab46eed94 \
      -DstagingDescription="${STAGING_DESC}"

    export STAGING_REPO_ID=$(mvn nexus-staging:rc-list | \
      egrep "\[INFO\] iohelidon\-[0-9]+[ ]+OPEN[ ]+${STAGING_DESC}" | \
      sed -E s@'.*(iohelidon-[0-9]*).*'@'\1'@g | head -1)
    echo "Nexus staging repository ID: ${STAGING_REPO_ID}"

    # Perform deployment
    mvn -B clean deploy -Prelease,archetypes -DskipTests \
      -Dgpg.passphrase="${GPG_PASSPHRASE}" \
      -DstagingRepositoryId=${STAGING_REPO_ID} \
      -DretryFailedDeploymentCount=10

    # Invoke perform hooks
    if [ -n "${PERFORM_HOOKS}" ]; then
      for perform_hook in ${PERFORM_HOOKS} ; do
        bash "${perform_hook}"
      done
    fi

    # Close the nexus staging repository
    mvn nexus-staging:rc-close \
      -DstagingRepositoryId=${STAGING_REPO_ID} \
      -DstagingDescription="${STAGING_DESC}"

    # Create and push a git tag
    # Note this may not be required for Github
    local GIT_REMOTE=$(git config --get remote.origin.url | \
                       sed "s,https://[^@]*@\([^/]*\)/,git@\1:,")

    git remote add release "${GIT_REMOTE}" > /dev/null 2>&1 || \
    git remote set-url release "${GIT_REMOTE}"

    git tag -f "${FULL_VERSION}"
    git push --force origin refs/tags/"${FULL_VERSION}":refs/tags/"${FULL_VERSION}"
}

# Invoke command
${COMMAND}
