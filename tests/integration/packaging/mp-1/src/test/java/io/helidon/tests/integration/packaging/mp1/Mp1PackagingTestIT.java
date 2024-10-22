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
package io.helidon.tests.integration.packaging.mp1;

import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.ProcessMonitor;
import io.helidon.tests.integration.harness.WaitStrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

abstract class Mp1PackagingTestIT {

    abstract ExecMode execMode();

    void doTestApp() {
        try (ProcessMonitor process = process()
                .await(WaitStrategy.waitForCompletion())) {
            assertThat(process.get().exitValue(), is(0));
        }
    }

    private ProcessMonitor process(String... opts) {
        return ProcessRunner.of(execMode())
                .finalName("helidon-tests-integration-packaging-mp-1")
                .moduleName("io.helidon.tests.integration.packaging.mp.one")
                .mainClass("io.helidon.tests.integration.packaging.mp1.Mp1Main")
                .opts(opts)
                .start();
    }
}
