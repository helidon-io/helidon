# Copyright (c) 2021 Oracle and/or its affiliates.
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

# Docker helper functions

# Docker container start
# Arguments: $1 - name:version of the image
#            $2 - name of the container (--name ${2})
#            $3 - container port publishing (--publish ${3})
#            $4 - additional docker run command arguments
#            $5 - name of variable with container running status
#            $6 - container start trigger variable name (optional, default true)
docker_start() {
    if [ -z "${6}" ] || [ -n "${6}" -a -n "${!6}" ]; then
        echo -n 'Starting Docker container: '
        docker run -d \
           --name "${2}" \
           --publish "${3}" \
           ${4} \
           --rm ${1} && \
        [ -n "${5}" ] && eval "${5}='1'"
    fi
}

# Docker container stop
# Arguments: $1 - name of the container
#            $2 - docker trigger variable name (optional, default true)
docker_stop() {
    if [ -z "${2}" ] || [ -n "${2}" -a -n "${!2}" ]; then
        echo -n 'Stopping Docker container: '
        docker stop "${1}"
    fi
}
