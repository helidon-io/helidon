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

package io.helidon.declarative.tests.compatibility.app;

import io.helidon.declarative.tests.compatibility.v4.LegacyEventObserver;
import io.helidon.declarative.tests.compatibility.v4.LegacyEventService;
import io.helidon.declarative.tests.compatibility.v4.LegacyLifecycleService;
import io.helidon.declarative.tests.compatibility.v4.LegacyNamedService;
import io.helidon.declarative.tests.compatibility.v4.LegacyPerLookupService;
import io.helidon.service.registry.ServiceRegistry;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

final class ServiceCompatibilityChecks {
    private ServiceCompatibilityChecks() {
    }

    static LegacyLifecycleService verify(ServiceRegistry registry) {
        var lifecycle = registry.get(LegacyLifecycleService.class);
        assertThat("Legacy post-construction callback", lifecycle.initializations(), is(1));
        assertThat("Legacy singleton lookup", registry.get(LegacyLifecycleService.class), sameInstance(lifecycle));
        assertThat("Legacy service is still active", lifecycle.destructions(), is(0));

        var first = registry.get(LegacyPerLookupService.class);
        var second = registry.get(LegacyPerLookupService.class);
        assertThat("Legacy per-lookup scope", second, not(sameInstance(first)));

        assertThat("Legacy factory production and named injection",
                   registry.get(LegacyNamedService.class).values(), contains("first-product", "second-product"));

        var events = registry.get(LegacyEventService.class);
        var observer = registry.get(LegacyEventObserver.class);
        assertThat("No event delivered before emission", observer.received(), empty());
        events.emit("first-event");
        events.emit("second-event");
        assertThat("Legacy synchronous observer delivery", observer.received(), contains("first-event", "second-event"));
        return lifecycle;
    }

    static void verifyShutdown(LegacyLifecycleService lifecycle) {
        assertThat("Legacy pre-destruction callback", lifecycle.destructions(), is(1));
        assertThat("Shutdown must not reactivate legacy service", lifecycle.initializations(), is(1));
    }
}
