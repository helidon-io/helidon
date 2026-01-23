/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.tests.shutdown.from.startup;

import java.time.Duration;

import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.WaitStrategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ShutdownIT {
    @Test
    void testShutdown() {
        var runner = ProcessRunner.of(ProcessRunner.ExecMode.CLASS_PATH)
                .finalName("helidon-service-tests-system-exit");

        try (var monitor = runner.start()) {
            WaitStrategy strategy = WaitStrategy.waitForCompletion();
            strategy.timeout(Duration.ofSeconds(10));

            monitor.await(strategy);
            assertThat(monitor.get().exitValue(), is(191));
        }
    }
}
