/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.context.ContextAwareExecutorService;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Testing configuration of {@link CommandScheduler}.
 */
class SchedulerConfigTest {

    @Test
    void testNonDefaultConfig() {
        Server server = null;
        try {
            server = Server.builder().port(-1).build();
            server.start();

            CommandScheduler commandScheduler = CommandScheduler.create(8);
            assertThat(commandScheduler, notNullValue());
            ScheduledThreadPoolSupplier poolSupplier = commandScheduler.poolSupplier();

            ScheduledExecutorService service = poolSupplier.get();
            ContextAwareExecutorService executorService = ((ContextAwareExecutorService) service);
            ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) executorService.unwrap();
            assertThat(stpe.getCorePoolSize(), is(8));
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
