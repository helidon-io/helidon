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

# Parent pom
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-"$VERSION".pom -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck -Dversion="$VERSION" -Dpackaging=pom

# pom
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-common-"$VERSION".pom -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-common -Dversion="$VERSION" -Dpackaging=pom

# jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-common-"$VERSION".jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-common -Dversion="$VERSION" -Dpackaging=jar

# sources jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-common-"$VERSION"-sources.jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-common-sources -Dversion="$VERSION" -Dpackaging=jar

# pom
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-"$VERSION".pom -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests -Dversion="$VERSION" -Dpackaging=pom

# jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-"$VERSION".jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests -Dversion="$VERSION" -Dpackaging=jar

# sources jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-"$VERSION"-sources.jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests-sources -Dversion="$VERSION" -Dpackaging=jar

# pom
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-pluggability-"$VERSION".pom -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests-pluggability -Dversion="$VERSION" -Dpackaging=pom

# jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-pluggability-"$VERSION".jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests-pluggability -Dversion="$VERSION" -Dpackaging=jar

# sources jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
-Dfile=jakarta.json-tck-tests-pluggability-"$VERSION"-sources.jar -DgroupId=jakarta.json \
-DartifactId=jakarta.json-tck-tests-pluggability-sources -Dversion="$VERSION" -Dpackaging=jar
