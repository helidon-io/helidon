/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.packaging.mp3;

import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;

import org.junit.jupiter.api.Test;

class Mp3JlinkTestIT extends Mp3PackagingTestIT {

    @Override
    ExecMode execMode() {
        return ExecMode.JLINK_CLASS_PATH;
    }

    @Test
    void testExitOnStarted() {
        doTestExitOnStarted();
    }

    @Test
    void testGreetResource() {
        doTestGreetResource();
    }
}
