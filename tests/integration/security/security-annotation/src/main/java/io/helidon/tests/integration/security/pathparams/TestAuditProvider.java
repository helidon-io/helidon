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

package io.helidon.tests.integration.security.pathparams;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.helidon.security.spi.AuditProvider;

/**
 * Audit provider used to verify resource annotation handling.
 */
public final class TestAuditProvider implements AuditProvider {
    private static final List<TracedAuditEvent> EVENTS = new CopyOnWriteArrayList<>();

    /**
     * Public constructor used by security provider configuration.
     */
    public TestAuditProvider() {
    }

    static void clear() {
        EVENTS.clear();
    }

    static TracedAuditEvent event(String eventType) {
        return EVENTS.stream()
                .filter(event -> eventType.equals(event.eventType()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Missing audit event: " + eventType));
    }

    @Override
    public Consumer<TracedAuditEvent> auditConsumer() {
        return EVENTS::add;
    }
}
