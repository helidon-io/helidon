#!/bin/bash

#
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

#
# Parameters:
#    type (blocking|reactive)

JMETER_HOME=~/bin/apache-jmeter-5.3
JMETER=$JMETER_HOME/bin

type=$1
time=$(date +"%Y-%m-%d_%H-%M")
directory=test/"${type}-load.test."${time}

$JMETER/jmeter.sh -n -t test/$1-test-plan.jmx -l "${directory}"/log.jtl -e -o "${directory}"