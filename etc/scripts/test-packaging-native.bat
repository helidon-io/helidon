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

if "%GRAALVM_HOME%"=="" (
    echo "ERROR: GRAALVM_HOME is not set";
    exit 1
)

if not exist %GRAALVM_HOME%\bin\native-image.cmd (
    echo "ERROR: %GRAALVM_HOME%\bin\native-image.cmd does not exist or is not executable";
    exit 1
)

call mvn %MAVEN_ARGS% --version

echo "GRAALVM_HOME=%GRAALVM_HOME%"
call %GRAALVM_HOME%\bin\native-image.cmd --version

@REM Run native image tests
cd %WS_DIR%\tests\integration\native-image

@REM Prime build all native-image tests
call mvn %MAVEN_ARGS% -e clean install

@REM Build native images
for %%v in (se-1 mp-1 mp-3) do (
    echo %%v
    cd %WS_DIR%\tests\integration\native-image\%%v
    call mvn %MAVEN_ARGS% -e clean package -Pnative-image
)

@REM Run this one because it has no pre-reqs and self-tests
@REM Uses relative path to read configuration
cd %WS_DIR%\tests\integration\native-image\mp-1
call %WS_DIR%\tests\integration\native-image\mp-1\target\helidon-tests-native-image-mp-1 || true

@REM Run se-1 exiting on started
cd %WS_DIR%\tests\integration\native-image\se-1
call %WS_DIR%\tests\integration\native-image\se-1\target\helidon-tests-native-image-se-1 -Dexit.on.started=! || true
