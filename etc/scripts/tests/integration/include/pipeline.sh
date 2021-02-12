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

# Pipeline environment setup

echo 'Running in pipeline mode'

source ${WS_DIR}/etc/scripts/pipeline-env.sh
JAVA_HOME='/tools/graalvm-ce-java11-21.0.0'
PATH="${PATH}:${JAVA_HOME}/bin"
