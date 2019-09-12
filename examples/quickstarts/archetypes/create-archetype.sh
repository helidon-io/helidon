#!/bin/bash -e
#
# Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
trap 'echo "ERROR: Error occurred at ${BASH_SOURCE}:${LINENO} command: ${BASH_COMMAND}"' ERR
set -eo pipefail

usage(){
    echo ""
    echo "Usage: `basename ${0}` [OPTIONS] PARAMETERS"
    echo ""
    echo "Create an archetype from an existing project."
    echo ""
    echo "Parameters:"
    echo "  --projectdir=PATH input project directory to create an archetype from"
    echo "  --groupid=XXX groupId of the input project"
    echo "  --artifactid=XXX artifactId of the input project"
    echo "  --version=XXX version of the input project"
    echo "  --name=XXX name of the input project"
    echo "  --package=XXX base Java package of the input project"
    echo "  --archetype-name=XXX name of the generated archetype"
    echo "  --archetype-description=XXX description of the generated archetype"
    echo "  --archetype-groupid=XXX groupId of the generated archetype"
    echo ""
    echo "Options:"
    echo "  --clean delete the generated archetype directory if it exists"
    echo "  --exludes=XXX regular expression for files to exclude"
    echo "  --maven-args arguments for the maven command to execute post generation (e.g. install)"
    echo "  --help print the usage and exit"
    echo ""
}

# parse command line arguments
for ((i=1;i<=${#*};i++))
{
    arg="${!i}"
    case "${arg}" in
    "--projectdir="*)
        readonly PROJECT_DIR="${arg#*=}"
        ;;
    "--groupid="*)
        readonly GROUPID="${arg#*=}"
        ;;
    "--artifactid="*)
        readonly ARTIFACTID="${arg#*=}"
        ;;
    "--version="*)
        readonly VERSION="${arg#*=}"
        ;;
    "--name="*)
        readonly NAME="${arg#*=}"
        ;;
    "--package="*)
        readonly PACKAGE="${arg#*=}"
        ;;
    "--archetype-name="*)
        readonly ARCHETYPE_NAME="${arg#*=}"
        ;;
    "--archetype-description="*)
        readonly ARCHETYPE_DESCRIPTION="${arg#*=}"
        ;;
    "--archetype-groupid="*)
        readonly ARCHETYPE_GROUPID="${arg#*=}"
        ;;
    "--excludes="*)
        readonly EXCLUDES="${arg#*=}"
        ;;
    "--maven-args="*)
        readonly MAVEN_ARGS="${arg#*=}"
        ;;
    "--clean")
        readonly CLEAN="true"
        ;;
    "--help")
        usage
        exit 0
        ;;
    *)
        echo ""
        echo "ERROR: Unknown option: ${arg}"
        usage
        exit 1
        ;;
    esac
}

echo ""
MISSING_PARAMS=false
if [ -z "${PROJECT_DIR}" ] ; then
    echo "ERROR: Missing required parameter --projectdir"
    MISSING_PARAMS=true
fi
if [ -z "${GROUPID}" ] ; then
    echo "ERROR: Missing required parameter --groupid"
    MISSING_PARAMS=true
fi
if [ -z "${ARTIFACTID}" ] ; then
    echo "ERROR: Missing required parameter --artifactid"
    MISSING_PARAMS=true
fi
if [ -z "${VERSION}" ] ; then
    echo "ERROR: Missing required parameter --version"
    MISSING_PARAMS=true
fi
if [ -z "${NAME}" ] ; then
    echo "ERROR: Missing required parameter --name"
    MISSING_PARAMS=true
fi
if [ -z "${PACKAGE}" ] ; then
    echo "ERROR: Missing required parameter --package"
    MISSING_PARAMS=true
fi
if [ -z "${ARCHETYPE_GROUPID}" ] ; then
    echo "ERROR: Missing required parameter --archetype-groupid"
    MISSING_PARAMS=true
fi
if [ -z "${ARCHETYPE_NAME}" ] ; then
    echo "ERROR: Missing required parameter --archetype-name"
    MISSING_PARAMS=true
fi
if [ -z "${ARCHETYPE_DESCRIPTION}" ] ; then
    echo "ERROR: Missing required parameter --archetype-description"
    MISSING_PARAMS=true
fi
if ${MISSING_PARAMS} ; then
    usage
    exit 1
fi

readonly DEFAULT_EXCLUDES="^[a-z]*\.gradle$|^\.idea$|^\.iml$"
if [ -z "${EXCLUDES}" ] ; then
    readonly EXCLUDES="${DEFAULT_EXCLUDES}"
fi

# verify project directory
if [ ! -d "${PROJECT_DIR}" ] ; then
    echo "ERROR: Invalid project directory: ${PROJECT_DIR}"
    exit 1
fi

# absolute path to project directory
PROJECT_DIR_PATH=$(cd "${PROJECT_DIR}" ; pwd -P)
PROJECT_DIRNAME=$(basename "${PROJECT_DIR_PATH}")
PROJECT_DIR_PARENT=$(cd "${PROJECT_DIR}/.." ; pwd -P)

if [ -h "${0}" ] ; then
  readonly SCRIPT_PATH="$(readlink "$0")"
else
  readonly SCRIPT_PATH="${0}"
fi

readonly MY_DIR=$(cd $(dirname -- "${SCRIPT_PATH}") ; pwd -P)
readonly ARCHETYPE_BASEDIR="${MY_DIR}"
readonly ARCHETYPE_DIRNAME="target/${ARTIFACTID}"
readonly ARCHETYPE_DIR="${ARCHETYPE_BASEDIR}/${ARCHETYPE_DIRNAME}"

if [ -d "${ARCHETYPE_DIR}" ] ; then
    if [ "${CLEAN}" = "true" ] ; then
        rm -rf ${ARCHETYPE_DIR}
    else
        echo "ERROR: Generated archetype directory exists ${ARCHETYPE_DIR}"
        exit 1
    fi
fi

echo "INFO: Generating archetype project at ${ARCHETYPE_DIR}"
mkdir -p ${ARCHETYPE_DIR}

echo "INFO: Generating archetype pom"
cat ${MY_DIR}/templates/pom.xml | sed \
    -e s@__GROUPID__@"${ARCHETYPE_GROUPID}"@g \
    -e s@__ARTIFACTID__@"${ARTIFACTID}"@g \
    -e s@__VERSION__@"${VERSION}"@g \
    -e s@__NAME__@"${ARCHETYPE_NAME}"@g \
    -e s@__DESCRIPTION__@"${ARCHETYPE_DESCRIPTION}"@g \
    > ${ARCHETYPE_DIR}/pom.xml

# Process a java file into a template.
# $1 input base directory
# $2 input filename
# $3 output base directory
# $4 output directory name
processJavaFile(){
    local input_basedir=${1}
    local input_filename=${2}
    local output_basedir=${3}
    local output_dirname=${4}
    local inputfile_basename=`basename ${input_basedir}/${input_filename}`
    local outputfile=${output_basedir}/${output_dirname}/${inputfile_basename}

    if [[ ${input_filename} =~ ${EXCLUDES} ]] ; then
        echo "INFO: Excluding ${input_filename}"
        return 0
    fi

    echo "INFO: Processing ${input_filename}"
    echo "INFO: Generating template at ${output_dirname}/${inputfile_basename}"

    echo "#set( \$symbol_pound = '#' )" >> ${outputfile}
    echo "#set( \$symbol_dollar = '$' )" >> ${outputfile}
    echo "#set( \$symbol_escape = '\' )" >> ${outputfile}

    cat ${input_basedir}/${input_filename}  | sed \
        -e s@"${PACKAGE}"@'${package}'@g \
        -e s@"${ARTIFACTID}"@'${artifactId}'@g \
        -e s@"${VERSION}"@'${version}'@g \
        >> ${outputfile}
}

# Process a top level file into a template.
# $1 input base directory
# $2 input filename
# $3 output base directory
# $4 output directory name
processFile(){
    local input_basedir=${1}
    local input_filename=${2}
    local output_basedir=${3}
    local output_dirname=${4}
    local outputfile=${output_basedir}/${output_dirname}/${input_filename}

    if [[ ${input_filename} =~ ${EXCLUDES} ]] ; then
        echo "INFO: Excluding ${input_filename}"
        return 0
    fi

    echo "INFO: Processing ${input_filename}"
    echo "INFO: Generating template at ${output_dirname}/${input_filename}"

    mkdir -p `dirname ${outputfile}`

    echo "#set( \$symbol_pound = '#' )" >>  ${outputfile}
    echo "#set( \$symbol_dollar = '$' )" >>  ${outputfile}
    echo "#set( \$symbol_escape = '\' )" >>  ${outputfile}

    cat ${input_basedir}/${input_filename} | sed \
        -e s@"${GROUPID}"@'${groupId}'@g \
        -e s@"${ARTIFACTID}"@'${artifactId}'@g \
        -e s@"${VERSION}"@'${version}'@g \
        -e s@'#'@'${symbol_pound}'@g \
        >> ${outputfile}
}

# Process a top level file into a template.
# $1 input base directory
# $2 input filename
# $3 output base directory
# $4 output directory name
processProjectPom(){
    local input_basedir=${1}
    local input_filename=${2}
    local output_basedir=${3}
    local output_dirname=${4}
    local outputfile=${output_basedir}/${output_dirname}/${input_filename}

    echo "INFO: Processing ${input_filename}"
    echo "INFO: Generating pom.xml at ${output_dirname}/${input_filename}"

    cat ${input_basedir}/${input_filename} | sed \
        -e s@"${PACKAGE}."@'${package}.'@g \
        -e s@"<groupId>${GROUPID}</groupId>"@'<groupId>${groupId}</groupId>'@g \
        -e s@"<artifactId>${ARTIFACTID}</artifactId>"@'<artifactId>${artifactId}</artifactId>'@g \
        -e s@"^    <version>${VERSION}</version>"@'    <version>${version}</version>'@g \
        -e s@"<name>${NAME}</name>"@'<name>${project.artifactId}</name>'@g \
        -e s@'<relativePath>.*</relativePath>'@'<relativePath/>'@g \
        > ${outputfile}
}

mkdir -p ${ARCHETYPE_DIR}/src/main/resources/archetype-resources

# process main sources
if [ -d "${PROJECT_DIR_PATH}/src/main/java" ] ; then
    echo "INFO: Processing Java files under src/main/java"
    mkdir -p ${ARCHETYPE_DIR}/src/main/resources/archetype-resources/src/main/java
    for javaFile in `find ${PROJECT_DIR_PATH}/src/main/java -type f -name "*.java" | sed s@"^${PROJECT_DIR_PATH}/"@@g` ; do
        processJavaFile \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${javaFile} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources/src/main/java
    done
fi

# process test sources
if [ -d "${PROJECT_DIR_PATH}/src/test/java" ] ; then
    echo "INFO: Processing Java files under src/test/java"
    mkdir -p ${ARCHETYPE_DIR}/src/main/resources/archetype-resources/src/test/java
    for javaFile in `find ${PROJECT_DIR_PATH}/src/test/java -type f -name "*.java" | sed s@"^${PROJECT_DIR_PATH}/"@@g` ; do
        processJavaFile \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${javaFile} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources/src/test/java
    done
fi

# process resources
if [ -d "${PROJECT_DIR_PATH}/src/main/resources" ] ; then
    echo "INFO: Processing resources under src/main/resources"
    mkdir -p ${ARCHETYPE_DIR}/src/main/resources/archetype-resources/src/main/resources/
    for resourceFile in `find ${PROJECT_DIR_PATH}/src/main/resources -type f | sed s@"^${PROJECT_DIR_PATH}"@@g` ; do
        processFile \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${resourceFile} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources
    done
fi

# process test resources
if [ -d "${PROJECT_DIR_PATH}/src/test/resources" ] ; then
    echo "INFO: Processing resources under src/test/resources"
    mkdir -p ${ARCHETYPE_DIR}/src/main/resources/archetype-resources/src/test/resources/
    for resourceFile in `find ${PROJECT_DIR_PATH}/src/test/resources -type f | sed s@"^${PROJECT_DIR_PATH}"@@g` ; do
        processFile \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${resourceFile} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources
    done
fi

# process all other files
echo "INFO: Processing all other files under ${PROJECT_DIR}"
for file in `find ${PROJECT_DIR_PATH} -type f | sed s@"^${PROJECT_DIR_PATH}/"@@g` ; do
    if [[ ${file} =~ ^target|^src/main/java|^src/test/java|src/main/resources|src/test/resources ]] ; then
        continue
    fi
    if [ `basename ${file}` != "pom.xml" ] ; then
        processFile \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${file} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources
    else
        processProjectPom \
            ${PROJECT_DIR_PARENT}/${PROJECT_DIRNAME} \
            ${file} \
            ${ARCHETYPE_DIR} \
            src/main/resources/archetype-resources
    fi
done

echo "INFO: Generating archetype-metadata.xml"
mkdir -p ${ARCHETYPE_DIR}/src/main/resources/META-INF/maven
cat ${MY_DIR}/templates/archetype-metadata.xml | sed \
    -e s@__ARTIFACTID__@"${ARTIFACTID}"@g \
    > ${ARCHETYPE_DIR}/src/main/resources/META-INF/maven/archetype-metadata.xml

# smoke test
mkdir -p ${ARCHETYPE_DIR}/src/test/resources/projects/basic
echo "package=it.pkg" >> ${ARCHETYPE_DIR}/src/test/resources/projects/basic/archetype.properties
echo "version=0.1-SNAPSHOT" >> ${ARCHETYPE_DIR}/src/test/resources/projects/basic/archetype.properties
echo "groupId=archetype.it" >> ${ARCHETYPE_DIR}/src/test/resources/projects/basic/archetype.properties
echo "artifactId=basic" >> ${ARCHETYPE_DIR}/src/test/resources/projects/basic/archetype.properties
touch ${ARCHETYPE_DIR}/src/test/resources/projects/basic/goal.txt

echo "DONE!"
echo ""

if [ ! -z "${MAVEN_ARGS}" ] ; then
    mvn -B -f ${ARCHETYPE_DIR}/pom.xml ${MAVEN_ARGS}
fi
