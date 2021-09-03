#!/bin/bash -e
#
# Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
# This script parses the Helidon pom.xml, and extracts informtion about
# external javadoc links. It then downloads the package-list files for
# those external javadocs so that we can use offlineLink instead of
# link in the javadoc configuration. 
#
# When you add a new external javadoc link to the Helidon pom, you
# run this script to generate new package-list files. You then check
# in the new one for your new external link.
#
# Why jump through these hoops? Because we want building the
# javadocs to run offline, and  not go fetching these package-list
# files at build time. Hopefully this makes the build
# faster and more sable. And helps us catch errors sooner.
#

# Path to this script
[ -h "${0}" ] && readonly SCRIPT_PATH="$(readlink "${0}")" || readonly SCRIPT_PATH="${0}"

# Path to the root of the workspace
readonly WS_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; cd ../.. ; pwd -P)

readonly POMFILE=${WS_DIR}/pom.xml

readonly tmpOutputDir=/tmp

# The project pom file has a set of properties that look like this:
#
# <javadoc.link.jaxrs>https://jax-rs.github.io/apidocs/${version.lib.jaxrs-api}</javadoc.link.jaxrs>
#
# We turn that into two arrays. The first is a list of javadoc.link property names (less the prefix)
# the second is a list of the property values which are URLs. So
# linkPropNames="jaxrs jackson-core jackson-databind ..."
# linkPropValues="https://jax-rs.github.io/... https://fasterxml.github.io/... ..."

# We create the array of propery names by parsing the pom.xml. Yes, ickey.
linkPropNames=()
execArgs=""
i=0
for linkProp in `grep "<javadoc.link." ${POMFILE}  | cut -d '>' -f1 | cut -d '.' -f3`
do
    linkPropNames[${i}]=${linkProp}
    execArgs+="\${javadoc.link.$linkProp} "
    i=$((i+1))
done

# We create the array of propery values by asking maven to echo the properties
linkPropValues=(`mvn -B -q -f ${POMFILE} -Dexec.executable="echo" -Dexec.args="${execArgs//$/\\$}" \
    --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec`)

# Now that we have the two arrays, we can download package-list files
for ((i=0;i<${#linkPropNames[@]};i++))
{
    name=${linkPropNames[${i}]}
    value=${linkPropValues[${i}]}

    outputDir=${WS_DIR}/etc/javadoc/${name}
    mkdir -p ${outputDir}

    # Go get package-list file! We save in a temp file so  we don't overwrite
    # anything in the workspace until we  know the request is good
    code=`curl -L -s --user-agent '' -o ${tmpOutputDir}/package-list -w "%{http_code}" ${value}/package-list`

    if [ "$code" -ne "200" ]; then
        # No package-list. Try element-list
        rm -f ${tmpOutputDir}/package-list
        code=`curl -L -s --user-agent '' -o ${tmpOutputDir}/element-list -w "%{http_code}" ${value}/element-list`
        if [ "$code" -ne "200" ]; then
            rm -f ${tmpOutputDir}/element-list
            echo ${code} ${name} ${value}
            echo "WARNING! Could not download package-list nor element-list for" >&2
            echo "${value}" >&2
            echo "You will need to find that yourself and put it in" >&2
            echo "${outputDir}/" >&2
        else
            echo ${code} ${name} ${value}/element-list
            mv ${tmpOutputDir}/element-list ${outputDir}/
        fi
    else
        echo ${code} ${name} ${value}/package-list
        mv ${tmpOutputDir}/package-list ${outputDir}/
    fi
}

echo "Done! You can find the package list files in ${WS_DIR}/etc/javadoc"
