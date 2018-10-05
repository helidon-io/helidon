#!/usr/bin/env bash
#
# Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

# run this script from project directory
source ./etc/graal/env.sh

# Configuration of reflection, needed for custom classes that should be instantiated or access by reflection
GRAAL_OPTIONS="-H:ReflectionConfigurationResources=./etc/graal/reflection-config.json"

# Configure all resources that should be available in runtime (except for META-INF/services - those are added
# by Helidon SVM Extension)
INCLUDE_RES="application.yaml"
INCLUDE_RES="${INCLUDE_RES}|logging.properties"
GRAAL_OPTIONS="${GRAAL_OPTIONS} -H:IncludeResources=${INCLUDE_RES}"

# This should be "set in stone" - this is to prevent compilation errors due to incomplete classpath for optional features of
# Netty.
DELAY_INIT="io.netty.handler.codec.http.HttpObjectEncoder"
DELAY_INIT="${DELAY_INIT},io.netty.handler.ssl.ReferenceCountedOpenSslEngine"
GRAAL_OPTIONS="${GRAAL_OPTIONS} --delay-class-initialization-to-runtime=${DELAY_INIT}"

# And this is to prevent compilation errors that are caused by some specific Netty classes (io/netty/internal/tcnative/SSL)
GRAAL_OPTIONS="${GRAAL_OPTIONS} --report-unsupported-elements-at-runtime"

echo "Graal options: ${GRAAL_OPTIONS}"

native-image -jar target/helidon-graal-vm-full.jar ${GRAAL_OPTIONS}

# then run
# ./helidon-graal-vm-full