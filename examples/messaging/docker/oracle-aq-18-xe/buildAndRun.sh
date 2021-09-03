#!/bin/bash
#
# Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

CURR_DIR=$(pwd)
TEMP_DIR=../../target
IMAGES_DIR=${TEMP_DIR}/ora-images
COMMIT="a69fe9b08ff147bb746d16af76cc5279ea5baf7a";
IMAGES_ZIP_URL=https://github.com/oracle/docker-images/archive/${COMMIT:0:7}.zip
IMAGES_ZIP_DIR=docker-images-${COMMIT}/OracleDatabase/SingleInstance/dockerfiles
ORA_DB_VERSION=18.4.0
BASE_IMAGE_NAME=oracle/database:${ORA_DB_VERSION}-xe
IMAGE_NAME=helidon/oracle-aq-example
CONTAINER_NAME=oracle-aq-example
ORACLE_PWD=frank

printf "%-100s" "Checking if base image ${BASE_IMAGE_NAME} is available in local repository"
if [[ "$(docker images -q ${BASE_IMAGE_NAME} 2>/dev/null)" == "" ]]; then
  printf "NOK\n"

  echo Base image ${BASE_IMAGE_NAME} not found. Building ...

  # cleanup
  mkdir -p ${TEMP_DIR}
  rm -rf ${IMAGES_DIR}
  rm -f ${TEMP_DIR}/ora-images.zip

  # download official oracle docker images
  curl -LJ -o ${TEMP_DIR}/ora-images.zip ${IMAGES_ZIP_URL}
  # unzip only image for Oracle database 18.4.0
  unzip -qq ${TEMP_DIR}/ora-images.zip "${IMAGES_ZIP_DIR}/*" -d ${IMAGES_DIR}
  mv ${IMAGES_DIR}/${IMAGES_ZIP_DIR}/${ORA_DB_VERSION} ${IMAGES_DIR}/
  mv ${IMAGES_DIR}/${IMAGES_ZIP_DIR}/buildContainerImage.sh ${IMAGES_DIR}/

  # cleanup
  rm -rf ${IMAGES_DIR}/docker-images-${COMMIT}
  rm ${TEMP_DIR}/ora-images.zip

  # build base image
  # can take long(15 minutes or so)
  cd ${IMAGES_DIR} || exit
  bash ./buildContainerImage.sh -v ${ORA_DB_VERSION} -x || exit
  cd ${CURR_DIR} || exit
else
  printf "OK\n"
fi

printf "%-100s" "Checking if image ${IMAGE_NAME} is available in local repository"
if [[ "$(docker images -q ${IMAGE_NAME} 2>/dev/null)" == "" ]]; then
  printf "NOK\n"

  echo Image ${IMAGE_NAME} not found. Building ...
  docker build -t ${IMAGE_NAME} . || exit
else
  printf "OK\n"
fi

printf "%-100s" "Checking if container ${CONTAINER_NAME} is ready"
if [[ $(docker ps -a --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}') != "${CONTAINER_NAME}" ]]; then
  printf "NOK\n"

  echo "Container ${CONTAINER_NAME} not found. Running ..."
  echo "!!! Be aware first time database initialization can take tens of minutes."
  echo "!!! Follow docker logs -f ${CONTAINER_NAME} for 'DATABASE IS READY TO USE' message"

  docker run -d --name ${CONTAINER_NAME} \
    -p 1521:1521 \
    -p 5500:5500 \
    -e ORACLE_PWD=${ORACLE_PWD} \
    ${IMAGE_NAME} || exit
else
  printf "OK\n"
  printf "%-100s" "Checking if container ${CONTAINER_NAME}  is started"
  if [[ $(docker ps --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}') != "${CONTAINER_NAME}" ]]; then
    printf "NOK\n"

    echo "Container ${CONTAINER_NAME} not started. Starting ..."
    docker start ${CONTAINER_NAME} || exit
  else
    printf "OK\n"
  fi
fi

echo "Container ${CONTAINER_NAME} with Oracle database ${ORA_DB_VERSION} XE populated with example AQ queues is either started or starting."
echo "For more info about the state of the database investigate logs:"
echo "    docker logs -f ${CONTAINER_NAME}"
echo "Url:  jdbc:oracle:thin:@localhost:1521:XE"
echo "user: frank"
echo "pass: frank"
