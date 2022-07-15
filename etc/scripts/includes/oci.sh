#
# Copyright (c) 2022 Oracle and/or its affiliates.
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


#
# OCI-related functions.
#


# WS_DIR variable verification.
if [ -z "${WS_DIR}" ]; then

    if [ -z "${1}" ]; then
        echo "ERROR: Missing required script path, exiting"
        exit 1
    fi

    if [ -z "${2}" ]; then
        echo "ERROR: Missing required cd to Helidon root directory from script path, exiting"
        exit 1
    fi

    readonly WS_DIR=$(cd "$(dirname -- ${1})" ; cd "${2}" ; pwd -P)

fi

# Multiple definition protection.
if [ -z "${__OCI_INCLUDED__}" ]; then
    readonly __OCI_INCLUDED__='true'

    # The presumed artifact ID of the shaded full jar
    # as defined in https://github.com/oracle/oci-java-sdk/blob/v2.35.0/bmc-shaded/bmc-shaded-full/pom.xml
    readonly OCI_SHADED_FULL_ARTIFACT_ID="oci-java-sdk-shaded-full"

    # evaluate a maven property
    # arg: property to evaluate
    mvn_eval() {
      mvn ${MAVEN_ARGS} -q -N -f "${WS_DIR}/pom.xml" help:evaluate -Dexpression="${1}" -DforceStdout
    }

    # download the oci shaded jar
    # arg1: oci_version
    # arg2: target path
    download_shaded_jar() {

      # See
      # https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdkgettingstarted.htm
      # https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdkexamples.htm

      local oci_version
      oci_version="${1}"

      local target_path
      target_path="${2}"

      local zip_filename
      zip_filename="oci-java-sdk-${oci_version}.zip"

      local oci_zip_url
      oci_zip_url="https://github.com/oracle/oci-java-sdk/releases/download/v${oci_version}/${zip_filename}"

      local jar_path
      jar_path="shaded/lib/oci-java-sdk-full-shaded-${oci_version}.jar"

      local oci_zip_file
      oci_zip_file="$(mktemp -t XXX-oci-sdk)"

      # Download the all-in-one zip file
      # if that works, unzip oci-java-sdk-full-shaded-${oci_version}.jar contained inside it.
      curl -L -s -S -o "${oci_zip_file}" "${oci_zip_url}" && \
      unzip -j -n -p -q "${oci_zip_file}" "${jar_path}" > "${target_path}" && \
      rm "${oci_zip_file}"
    }

    # install a jar in the local maven repository
    # arg1: file
    # arg2: groupId
    # arg3: artifactId
    # arg4: version
    mvn_install_jar() {
      mvn ${MAVEN_ARGS} -N -q \
        -f "${WS_DIR}/pom.xml" \
        install:install-file \
        -Dfile="${1}" \
        -DgroupId="${2}" \
        -DartifactId="${3}" \
        -Dversion="${4}" \
        -Dpackaging="jar"
    }

    # download and install the oci shaded jar
    # arg1: artifact_file
    # arg2: oci_version
    _install_oci_shaded_full_jar() {
        local artifact_file
        artifact_file="${1}"

        local oci_version
        oci_version="${2}"

        if [ -e "${artifact_file}" ]; then
          # already installed
          return 0
        fi

        local cache_dir
        if [ -n "${JENKINS_HOME}" ] ; then
          cache_dir="/cache"
        else
          cache_dir="$(mktemp -d)"
        fi

        local jar_file
        jar_file="${cache_dir}/$(basename ${artifact_file})"
        if [ ! -e "${jar_file}" ]; then
            echo "Downloading OCI SDK v${oci_version}"
            download_shaded_jar "${oci_version}" "${jar_file}"
        fi

        echo "Installing OCI SDK v${oci_version}"
        mvn_install_jar "${jar_file}" "com.oracle.oci.sdk" "${OCI_SHADED_FULL_ARTIFACT_ID}" "${oci_version}"
    }

    #
    # Downloads and installs the OCI Java SDK's shaded full jar into
    # the local Maven repository if it is not already present.
    #
    # This function is idempotent.
    #
    install_oci_shaded_full_jar() {

        local oci_version
        oci_version="$(mvn_eval 'version.lib.oci')"

        local jar_filename
        readonly jar_filename="${OCI_SHADED_FULL_ARTIFACT_ID}-${oci_version}.jar"

        local maven_repo_local
        maven_repo_local="$(mvn_eval 'settings.localRepository')"

        local artifact_dir
        artifact_dir="${maven_repo_local}/com/oracle/oci/sdk/${OCI_SHADED_FULL_ARTIFACT_ID}/${oci_version}"

        if [ -e "${artifact_dir}/${jar_filename}" ]; then
          return 0
        fi

        if [ -n "${JENKINS_HOME}" ] ; then
            (
              flock -w 600 200
              _install_oci_shaded_full_jar "${artifact_dir}/${jar_filename}" "${oci_version}"
            ) 200>"${artifact_dir}/lock"
        else
            _install_oci_shaded_full_jar "${artifact_dir}/${jar_filename}" "${oci_version}"
        fi
    }
else
    echo "WARNING: ${WS_DIR}/etc/scripts/includes/oci.sh included multiple times."
fi
