#!/bin/bash -e
#
# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

# This script has the following parameters:
# $1: the download URL (full)
# $2: the name of downloaded file withouth suffixes
# $3: the bin directory of the extracted installation
DOWNLOAD_URL=$1
FILE_NAME=$2
BIN_DIRECTORY=$3

current_dir="$(pwd)"

# If already downloaded, just return
gu_file=./graalvm/${BIN_DIRECTORY}/gu
if test -f "${gu_file}"; then
  echo GraalVM already downloaded
  exit
fi

mkdir -P graalvm
cd graalvm
wget "${DOWNLOAD_URL}"
gunzip ./"${FILE_NAME}.tar.gz"
tar -xvf ./"${FILE_NAME}.tar"
cd "${BIN_DIRECTORY}"
./gu install native-image
# Change to GRAAL_VM home
cd ..
GRAALVM_HOME="$(pwd)"
export GRAALVM_HOME

cd "${current_dir}"


