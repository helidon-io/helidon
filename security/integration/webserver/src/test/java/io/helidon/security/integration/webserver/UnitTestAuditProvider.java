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

package io.helidon.security.integration.webserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.security.AuditEvent;
import io.helidon.security.spi.AuditProvider;

/**
 * Audit provider that expects exactly one event and caches it.
 */
class UnitTestAuditProvider implements AuditProvider {
    private volatile AuditEvent theEvent;
    private final CountDownLatch cdl = new CountDownLatch(1);

    @Override
    public Consumer<TracedAuditEvent> auditConsumer() {
        return item -> {
            if ("unit_test".equals(item.eventType())) {
                theEvent = item;
                cdl.countDown();
            }
        };
    }

    AuditEvent getAuditEvent() throws InterruptedException {
        cdl.await(5, TimeUnit.SECONDS);
        return theEvent;
    }
}
