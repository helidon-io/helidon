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

    readonly WS_DIR=$(cd $(dirname -- "${1}") ; cd "${2}" ; pwd -P)

fi

# Multiple definition protection.
if [ -z "${__OCI_INCLUDED__}" ]; then
    readonly __OCI_INCLUDED__='true'

    #
    # Downloads and installs the OCI Java SDK's shaded full jar into
    # the local Maven repository if it is not already present.
    #
    # This function is idempotent.
    #
    install_oci_shaded_full_jar() {

        # The presumed artifact ID of the shaded full jar, as defined
        # in, e.g.,
        # https://github.com/oracle/oci-java-sdk/blob/v2.35.0/bmc-shaded/bmc-shaded-full/pom.xml#L10,
        # although that that pom.xml file seems to be a stub of sorts.
        # We anticipate this will be the artifact ID of the shaded
        # full jar that OCI will eventually push to Maven Central,
        # rendering this workaround moot.  See also
        # https://github.com/oracle/oci-java-sdk/issues/371#issuecomment-1086331705.
        #
        # Note carefully that this is different from the shaded jar's
        # location in the .zip file for some reason.
        local OCI_SHADED_FULL_ARTIFACT_ID
        readonly OCI_SHADED_FULL_ARTIFACT_ID=oci-java-sdk-shaded-full

        # The version of OCI in use.
        if [ -z "${OCI_VERSION}" ]; then
            local OCI_VERSION
            readonly OCI_VERSION="$(mvn ${MAVEN_ARGS} --file "${WS_DIR}/pom.xml" --non-recursive --quiet org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=version.lib.oci -DforceStdout)"
        fi

        # The relative name of the shaded full jar as implied by the
        # artifact ID above.
        local OCI_SHADED_FULL_JAR
        readonly OCI_SHADED_FULL_JAR="${OCI_SHADED_FULL_ARTIFACT_ID}-${OCI_VERSION}.jar"

        # Figure out where the local Maven repository (cache) is.
        if [ -z "${MAVEN_REPO_LOCAL}" ]; then
            local MAVEN_REPO_LOCAL
            readonly MAVEN_REPO_LOCAL="$(mvn ${MAVEN_ARGS} --file "${WS_DIR}/pom.xml" --non-recursive --quiet help:evaluate -Dexpression=settings.localRepository -DforceStdout)"
        fi

        if [ ! -e "${MAVEN_REPO_LOCAL}/com/oracle/oci/sdk/${OCI_SHADED_FULL_ARTIFACT_ID}/${OCI_VERSION}/${OCI_SHADED_FULL_JAR}" ]; then

            # If the local Maven repository (cache) doesn't already
            # have the shaded full jar, it's time to install it.

            # Set where to download it (if downloading turns out to be
            # necessary).  The containing directory is assumed to be
            # writeable.  In the Helidon pipelines environment, this
            # assumption will always be true.
            if [ -z "${CACHED_OCI_SHADED_FULL_JAR}" ]; then
                local CACHED_OCI_SHADED_FULL_JAR
                readonly CACHED_OCI_SHADED_FULL_JAR="/cache/${OCI_SHADED_FULL_JAR}"
            fi

            if [ ! -e "${CACHED_OCI_SHADED_FULL_JAR}" ]; then

                # The downloaded shaded full jar does not exist.  It
                # is time to download it.  To do this, we have to
                # download the all-in-one .zip file that OCI publishes
                # to their Github repository, and then extract the
                # shaded full jar from that.  See
                # https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdkgettingstarted.htm#:~:text=Oracle%20development%20tools.-,Downloading%20the%20SDK%20from%20GitHub,-You%20can%20download
                # to start, and then
                # https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdkexamples.htm#:~:text=your%20own%20environment.-,Running%20Examples,-Download%20the%20SDK
                # and
                # https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdkexamples.htm#:~:text=Third%2DParty%20Dependencies%20and%20Shading.

                # The relative name of the OCI SDK zip file once it
                # has been downloaded.
                local OCI_ZIP
                readonly OCI_ZIP="oci-java-sdk-${OCI_VERSION}.zip"

                # The (usually Github) URI identifying the full OCI
                # SDK zip file to be downloaded.
                if [ -z "${OCI_ZIP_URI}" ]; then
                    local OCI_ZIP_URI
                    readonly OCI_ZIP_URI="https://github.com/oracle/oci-java-sdk/releases/download/v${OCI_VERSION}/${OCI_ZIP}"
                fi

                # The temporary directory into which ${OCI_ZIP_URI}'s
                # referent will be downloaded.
                #
                # For storage planning purposes, note that this file
                # is about 721 MB.  It is (remotely) conceivable that
                # n jobs could be running that reference different n
                # different OCI versions, so there could be the need
                # for n * 721 MB of space for these downloads.
                local OCI_ZIP_TEMPDIR
                readonly OCI_ZIP_TEMPDIR="$(mktemp -d)"

                # Download the all-in-one zip file, and, if that
                # works, unzip the
                # oci-java-sdk-full-shaded-${OCI_VERSION}.jar file
                # contained inside it quietly (-q) to stdout (-p)
                # discarding path information (-j), and making sure
                # not to ever query or overwrite (-n) redirect its
                # contents into ${CACHED_OCI_SHADED_FULL_JAR}, and, if
                # that works, remove the .zip file we downloaded and
                # any temporary directories created along the way.
                # The end result will be
                # ${CACHED_OCI_SHADED_FULL_JAR}.
                #
                # For storage planning purposes, the shaded full jar
                # is approximately 106 MB in size.
                #
                # Note that for some reason the shaded full jar is
                # present in the all-in-one zip file as
                # shaded/lib/oci-java-sdk-full-shaded..., not, as you
                # might expect,
                # shaded/lib/oci-java-sdk-shaded-full....  That is not
                # a mistake or a typo.
                curl --location --no-progress-meter --output "${OCI_ZIP_TEMPDIR}/${OCI_ZIP}" "${OCI_ZIP_URI}" && \
                    unzip -j -n -p -q "${OCI_ZIP_TEMPDIR}/${OCI_ZIP}" "shaded/lib/oci-java-sdk-full-shaded-${OCI_VERSION}.jar" > "${CACHED_OCI_SHADED_FULL_JAR}" && \
                    rm "${OCI_ZIP_TEMPDIR}/${OCI_ZIP}" && \
                    rmdir "${OCI_ZIP_TEMPDIR}"

            fi

            # Install the cached shaded full jar into the local Maven
            # repository (cache) since we determined that it did not
            # exist prior to all this.
            mvn ${MAVEN_ARGS} --file "${WS_DIR}/pom.xml" --non-recursive --quiet org.apache.maven.plugins:maven-install-plugin:install-file \
                -Dfile="${CACHED_OCI_SHADED_FULL_JAR}" \
                -DlocalRepositoryPath="${MAVEN_REPO_LOCAL}" \
                -DgroupId=com.oracle.oci.sdk \
                -DartifactId="${OCI_SHADED_FULL_ARTIFACT_ID}" \
                -Dversion="${OCI_VERSION}" \
                -Dpackaging=jar

        fi
    }
else
    echo "WARNING: ${WS_DIR}/etc/scripts/includes/oci.sh included multiple times."
fi
