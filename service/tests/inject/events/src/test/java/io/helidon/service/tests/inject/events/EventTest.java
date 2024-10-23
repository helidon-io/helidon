/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.events;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class EventTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void initRegistry() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    static void tearDownRegistry() {
        registryManager.shutdown();
    }

    @Test
    void testEvent() {
        var emitter = registry.get(EventTypes.EventEmitter.class);
        emitter.emit(new EventTypes.EventObject("unit-test-event"));

        var eventObserver = registry.get(EventTypes.EventObserver.class);
        var eventObject = eventObserver.eventObject();
        assertThat("Event should have been received in observer", eventObject, notNullValue());
        assertThat(eventObject.message(), is("unit-test-event"));
    }

    @Test
    void testQualifiedEvent() {
        var emitter = registry.get(QualifiedEventTypes.EventEmitter.class);
        emitter.emit(new QualifiedEventTypes.EventObject("unit-test-event"));

        var eventObserver = registry.get(QualifiedEventTypes.EventObserver.class);
        var eventObject = eventObserver.eventObject();
        assertThat("Event should have been received in observer", eventObject, notNullValue());
        assertThat(eventObject.message(), is("unit-test-event"));

        eventObject = eventObserver.unqualifiedEventObject();
        assertThat("Event should have been received in observer", eventObject, notNullValue());
        assertThat(eventObject.message(), is("unit-test-event"));
    }

    @Test
    void testAsyncEvent() throws ExecutionException, InterruptedException, TimeoutException {
        var emitter = registry.get(AsyncEventTypes.EventEmitter.class);
        var future = emitter.emit(new AsyncEventTypes.EventObject("unit-test-event"));
        var returnedObject = future.toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(returnedObject.message(), is("unit-test-event"));

        var eventObserver = registry.get(AsyncEventTypes.EventObserver.class);
        var eventObject = eventObserver.eventObject();
        assertThat("Event should have been received in observer", eventObject, notNullValue());
        assertThat(eventObject.message(), is("unit-test-event"));
        // make sure it was really an asynchronous delivery
        var threadName = eventObserver.threadName();
        assertThat(threadName, startsWith("inject-event-manager-"));

        var syncObserver = registry.get(AsyncEventTypes.SyncEventObserver.class);
        var syncEventObject = syncObserver.eventObject();
        assertThat("Event should have been received in observer", syncEventObject, notNullValue());
        assertThat(syncEventObject, sameInstance(eventObject));
        // make sure it was really an asynchronous delivery
        var syncThreadName = syncObserver.threadName();
        assertThat(syncThreadName, startsWith("inject-event-manager-"));
        assertThat(syncThreadName, not(threadName));
    }
}
