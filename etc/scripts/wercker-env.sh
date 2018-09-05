#!/bin/bash
#
# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

# Cleanup pipeline environment
# Set the Maven repository to the cache
if [ "${WERCKER}" = "true" ] ; then
    export MAVEN_OPTS="-Dmaven.repo.local=${WERCKER_CACHE_DIR}/local_repository"
    rm -rf ~/.m2/settings* ~/.gitconfig ~/.ssh ${WERCKER_CACHE_DIR}/local_repository/io/helidon
fi
