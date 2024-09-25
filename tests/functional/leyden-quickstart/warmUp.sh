#!/bin/bash -e

#
# Copyright (c) 2024 Oracle and/or its affiliates.
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


curl --retry 10 --retry-all-errors --retry-delay 1 http://localhost:7001
printf "\n==== Warming up ...\n"
wrk -c 16 -t 16 -d 10s http://localhost:7001
printf "\n==== Warmup complete\n"
kill $(jps | grep jar | awk '{print $1}')