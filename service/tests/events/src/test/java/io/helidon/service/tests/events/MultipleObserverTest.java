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

package io.helidon.service.tests.events;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.registry.EventManager;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

class MultipleObserverTest {
    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;
    private EventManager events;

    @BeforeEach
    void initRegistry() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
        events = registry.get(EventManager.class);
    }

    @AfterEach
    void shutdownRegistry() {
        registryManager.shutdown();
    }

    @Test
    void differentEventTypes() {
        var listener = registry.get(MultipleObserverTypes.Listener.class);
        var created = new MultipleObserverTypes.LoanCreated("loan-1");
        var closed = new MultipleObserverTypes.LoanClosed("loan-1");

        events.emit(ResolvedType.create(MultipleObserverTypes.LoanCreated.class), created, Set.of());
        assertThat(listener.created, contains(sameInstance(created)));
        assertThat("Created event must not notify the closed observer", listener.closed, empty());

        events.emit(ResolvedType.create(MultipleObserverTypes.LoanClosed.class), closed, Set.of());
        assertThat(listener.created, contains(sameInstance(created)));
        assertThat(listener.closed, contains(sameInstance(closed)));
    }

    @Test
    void sameEventTypeAndQualifiers() {
        var listener = registry.get(MultipleObserverTypes.DuplicateListener.class);
        var event = new MultipleObserverTypes.DuplicateEvent("both methods");

        events.emit(ResolvedType.create(MultipleObserverTypes.DuplicateEvent.class),
                    event,
                    Set.of(Qualifier.createNamed("duplicate")));

        assertThat(listener.first, contains(sameInstance(event)));
        assertThat(listener.second, contains(sameInstance(event)));
    }

    @Test
    void qualifierValuesAndUnqualifiedEvents() {
        var listener = registry.get(MultipleObserverTypes.QualifiedListener.class);
        var type = ResolvedType.create(MultipleObserverTypes.QualifiedEvent.class);
        var first = new MultipleObserverTypes.QualifiedEvent("first payload");
        var second = new MultipleObserverTypes.QualifiedEvent("second payload");
        var unqualified = new MultipleObserverTypes.QualifiedEvent("unqualified payload");

        events.emit(type, first, Set.of(Qualifier.createNamed("first")));
        assertThat(listener.first, contains(sameInstance(first)));
        assertThat("Different qualifier value must not match", listener.second, empty());
        assertThat("Qualified event must not match an unqualified observer", listener.unqualified, empty());

        events.emit(type, second, Set.of(Qualifier.createNamed("second")));
        events.emit(type, unqualified, Set.of());
        events.emit(type,
                    new MultipleObserverTypes.QualifiedEvent("unmatched"),
                    Set.of(Qualifier.createNamed("other")));

        assertThat(listener.first, contains(sameInstance(first)));
        assertThat(listener.second, contains(sameInstance(second)));
        assertThat(listener.unqualified, contains(sameInstance(unqualified)));
    }

    @Test
    void synchronousAndAsynchronousObservers() throws InterruptedException, ExecutionException, TimeoutException {
        var listener = registry.get(MultipleObserverTypes.MixedListener.class);
        var type = ResolvedType.create(MultipleObserverTypes.MixedEvent.class);
        var syncEvent = new MultipleObserverTypes.MixedEvent("emit payload");
        var asyncEvent = new MultipleObserverTypes.MixedEvent("emitAsync payload");

        events.emit(type, syncEvent, Set.of());
        assertThat("Synchronous observer must finish before emit returns", listener.synchronous,
                   contains(sameInstance(syncEvent)));
        var returned = events.emitAsync(type, asyncEvent, Set.of()).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(returned, sameInstance(asyncEvent));
        boolean completed = listener.asyncReceived.await(10, TimeUnit.SECONDS);
        assertThat("Both asynchronous deliveries must complete; received: " + listener.asynchronous, completed, is(true));

        assertThat(listener.synchronous, contains(sameInstance(syncEvent), sameInstance(asyncEvent)));
        assertThat(listener.asynchronous, containsInAnyOrder(sameInstance(syncEvent), sameInstance(asyncEvent)));
    }

    @Test
    void overloadedMethodsAndSameSimpleTypeName() {
        var listener = registry.get(MultipleObserverTypes.OverloadedListener.class);
        var first = new MultipleObserverTypes.First.EventObject("first payload");
        var second = new MultipleObserverTypes.Second.EventObject("second payload");

        events.emit(ResolvedType.create(MultipleObserverTypes.First.EventObject.class), first, Set.of());
        assertThat(listener.first, contains(sameInstance(first)));
        assertThat("Same simple type name must not match a different event type", listener.second, empty());

        events.emit(ResolvedType.create(MultipleObserverTypes.Second.EventObject.class), second, Set.of());
        assertThat(listener.first, contains(sameInstance(first)));
        assertThat(listener.second, contains(sameInstance(second)));
    }
}
