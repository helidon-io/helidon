/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests to verify that the default thread pool sizes can be set via config.
 * Default size for the pools in MPT FT is 16, but the application.yaml file
 * in this test directory sets it to 8.
 *
 * See {@code test/resources/application.yaml}.
 */
public class ThreadPoolConfigTest extends FaultToleranceTest {

    private final FaultToleranceExtension extension;

    public ThreadPoolConfigTest() {
        extension = CDI.current().getBeanManager().getExtension(FaultToleranceExtension.class);
    }

    @Test
    public void testThreadPoolDefaultSize() {
        assertThat(extension.threadPoolSupplier().corePoolSize(), is(8));

    }

    @Test
    public void testScheduledThreadPool() {
        assertThat(extension.scheduledThreadPoolSupplier().corePoolSize(), is(8));
    }
}
