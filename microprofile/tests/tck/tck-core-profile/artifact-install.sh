#!/usr/bin/env bash
#
# Copyright (c) 2023 Oracle and/or its affiliates.
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

##script to install the artifact directory contents into a local maven repository

VERSION="$1"

# pom
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=target/core-profile-tck-"$VERSION"/artifacts/core-tck-parent-"$VERSION".pom -DgroupId=jakarta.ee.tck.coreprofile \
-DartifactId=core-tck-parent -Dversion="$VERSION" -Dpackaging=pom

# jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=target/core-profile-tck-"$VERSION"/artifacts/core-profile-tck-impl-"$VERSION".jar -DgroupId=jakarta.ee.tck.coreprofile \
-DartifactId=core-profile-tck-impl -Dversion="$VERSION" -Dpackaging=jar
