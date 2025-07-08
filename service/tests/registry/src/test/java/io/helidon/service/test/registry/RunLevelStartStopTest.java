/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import org.junit.jupiter.api.Test;

class RunLevelStartStopTest {

    @Test
    void test() {
        var config = ServiceRegistryConfig.create();
        var manager = ServiceRegistryManager.start(config);
        var services = manager.registry().all(RunLevelStartStopFixture.class);
        assertThat(services, hasSize(3));
        var state = manager.registry().get(RunLevelStartStopFixture.State.class);
        assertThat(state.startUpSequence, contains("Service1", "Service2", "Service3"));
        assertThat(state.shutDownSequence, hasSize(0));

        manager.shutdown();

        assertThat(state.shutDownSequence, contains("Service3", "Service2", "Service1"));
    }
}
