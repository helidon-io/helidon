/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testing configuration of {@link CommandScheduler}.
 */
class SchedulerConfigTest {

    /**
     * Loads config from application.yaml where the pool size is set to
     * 8 instead of 16 (default).
     */
    @Test
    void testNonDefaultConfig() {
        Server server = null;
        try {
            server = Server.builder().port(-1).build();
            server.start();

            CommandScheduler commandScheduler = CommandScheduler.instance();
            assertNotNull(commandScheduler);
            ScheduledThreadPoolSupplier poolSupplier = commandScheduler.poolSupplier();
            assertEquals(8, poolSupplier.get().getCorePoolSize());
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
