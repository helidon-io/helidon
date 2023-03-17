@REM
@REM Copyright (c) 2024 Oracle and/or its affiliates.
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

set WS_DIR=%~dp0\..\..

@REM Run native image tests
cd %WS_DIR%\tests\integration\native-image

@REM Prime build all native-image tests
call mvn %MAVEN_ARGS% -e clean install

@REM Run tests with classpath and then module path

@REM Run MP-1
cd %WS_DIR%\tests\integration\native-image\mp-1

@REM Classpath
call java -jar target\helidon-tests-native-image-mp-1.jar

@REM Module Path
call java --module-path target\helidon-tests-native-image-mp-1.jar;target\libs ^
  --module helidon.tests.nimage.mp/io.helidon.tests.integration.nativeimage.mp1.Mp1Main

@REM Run MP-3 (just start and stop)
cd %WS_DIR%\tests\integration\native-image\mp-3
@REM Classpath
call java -Dexit.on.started=! -jar target\helidon-tests-native-image-mp-3.jar

@REM Module Path
call java -Dexit.on.started=! ^
  --module-path target\helidon-tests-native-image-mp-3.jar;target\libs ^
  --add-modules helidon.tests.nimage.quickstartmp ^
  --module io.helidon.microprofile.cdi/io.helidon.microprofile.cdi.Main
