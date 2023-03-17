@REM
@REM Copyright (c) 2023 Oracle and/or its affiliates.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off

set WS_DIR=%~dp0..\..

@REM Run native image tests
cd %WS_DIR%\tests\integration\native-image

echo Skipping jlink tests, until we switch to Smallrye based Jandex in build tools - org.jboss fails with NPE on Java 21
exit 0

@REM Prime build all native-image tests
call mvn %MAVEN_ARGS% -e clean install

@REM Build jlink images
@REM mp-2 fails because of https://github.com/oracle/helidon-build-tools/issues/478
cd %WS_DIR%\tests\integration\native-image\mp-1
call mvn %MAVEN_ARGS% package -e -Pjlink-image,staging -Djlink.image.addClassDataSharingArchive=false -Djlink.image.testImage=false

cd %WS_DIR%\tests\integration\native-image\mp-3
call mvn %MAVEN_ARGS% package -e -Pjlink-image,staging -Djlink.image.addClassDataSharingArchive=false -Djlink.image.testImage=false

@REM Run tests with classpath and then module path

@REM Run MP-1
cd %WS_DIR%\tests\integration\native-image\mp-1
set jri_dir=%WS_DIR%\tests\integration\native-image\mp-1\target\helidon-tests-native-image-mp-1-jri

@REM Classpath
call %jri_dir%\bin\start --jvm

@REM Module Path
call %jri_dir%\bin\java ^
  --module-path %jri_dir%\app\helidon-tests-native-image-mp-1.jar;%jri_dir%\app\libs ^
  --module helidon.tests.nimage.mp/io.helidon.tests.integration.nativeimage.mp1.Mp1Main

@REM Run MP-3 (just start and stop)
cd %WS_DIR%\tests\integration\native-image\mp-3
set jri_dir=%WS_DIR%\tests\integration\native-image\mp-3\target\helidon-tests-native-image-mp-3-jri

@REM Classpath
call %jri_dir%\bin\start --test --jvm

@REM Module Path
call %jri_dir%\bin\java -Dexit.on.started=! ^
   --module-path %jri_dir%\app\helidon-tests-native-image-mp-3.jar;%jri_dir%\app\libs ^
   --add-modules helidon.tests.nimage.quickstartmp ^
   --module io.helidon.microprofile.cdi/io.helidon.microprofile.cdi.Main
