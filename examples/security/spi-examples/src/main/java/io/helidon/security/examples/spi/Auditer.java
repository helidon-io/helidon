/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.spi;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.security.spi.AuditProvider;

/**
 * Audit provider implementation.
 */
public class Auditer implements AuditProvider {
    // BEWARE this is a memory leak. Only for example purposes and for unit-tests
    private final List<TracedAuditEvent> messages = new LinkedList<>();

    public List<TracedAuditEvent> getMessages() {
        return messages;
    }

    @Override
    public Consumer<TracedAuditEvent> auditConsumer() {
        return event -> {
            // just dump to stdout and store in a list
            System.out.println(event.severity() + ": " + event.tracingId() + ": " + event);
            messages.add(event);
        };
    }

}
