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
package io.helidon.metrics.api;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.spi.ExemplarService;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test(perMethod = true)
class TestExemplarServiceManager {

    @Test
    void retainsServiceRegistryExemplarServices() {
        AtomicInteger labelRequests = new AtomicInteger();
        Services.set(ExemplarService.class, () -> "trace_id=trace-" + labelRequests.incrementAndGet());

        ExemplarServiceManager manager = Services.get(ExemplarServiceManager.class);
        Sample.Labeled first = Sample.labeled(1);
        Sample.Labeled second = Sample.labeled(2);

        assertThat(Services.get(ExemplarServiceManager.class), sameInstance(manager));
        assertThat(first.label(), containsString("trace_id=trace-1"));
        assertThat(second.label(), containsString("trace_id=trace-2"));
        assertThat(labelRequests.get(), is(2));
    }
}
