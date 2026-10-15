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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import io.helidon.service.registry.Event;
import io.helidon.service.registry.Service;

class MultipleObserverTypes {
    private MultipleObserverTypes() {
    }

    record LoanCreated(String id) {
    }

    record LoanClosed(String id) {
    }

    record DuplicateEvent(String message) {
    }

    record QualifiedEvent(String message) {
    }

    record MixedEvent(String message) {
    }

    @Service.Singleton
    static class Listener {
        final List<LoanCreated> created = new ArrayList<>();
        final List<LoanClosed> closed = new ArrayList<>();

        @Event.Observer
        void onCreated(LoanCreated event) {
            created.add(event);
        }

        @Event.Observer
        void onClosed(LoanClosed event) {
            closed.add(event);
        }
    }

    @Service.Singleton
    static class DuplicateListener {
        final List<DuplicateEvent> first = new ArrayList<>();
        final List<DuplicateEvent> second = new ArrayList<>();

        @Event.Observer
        @Service.Named("duplicate")
        void first(DuplicateEvent event) {
            first.add(event);
        }

        @Event.Observer
        @Service.Named("duplicate")
        void second(DuplicateEvent event) {
            second.add(event);
        }
    }

    @Service.Singleton
    static class QualifiedListener {
        final List<QualifiedEvent> first = new ArrayList<>();
        final List<QualifiedEvent> second = new ArrayList<>();
        final List<QualifiedEvent> unqualified = new ArrayList<>();

        @Event.Observer
        @Service.Named("first")
        void first(QualifiedEvent event) {
            first.add(event);
        }

        @Event.Observer
        @Service.Named("second")
        void second(QualifiedEvent event) {
            second.add(event);
        }

        @Event.Observer
        void unqualified(QualifiedEvent event) {
            unqualified.add(event);
        }
    }

    @Service.Singleton
    static class MixedListener {
        final List<MixedEvent> synchronous = new CopyOnWriteArrayList<>();
        final List<MixedEvent> asynchronous = new CopyOnWriteArrayList<>();
        final CountDownLatch asyncReceived = new CountDownLatch(2);

        @Event.Observer
        void synchronous(MixedEvent event) {
            synchronous.add(event);
        }

        @Event.AsyncObserver
        void asynchronous(MixedEvent event) {
            asynchronous.add(event);
            asyncReceived.countDown();
        }
    }

    @Service.Singleton
    static class OverloadedListener {
        final List<First.EventObject> first = new ArrayList<>();
        final List<Second.EventObject> second = new ArrayList<>();

        @Event.Observer
        void onEvent(First.EventObject event) {
            first.add(event);
        }

        @Event.Observer
        void onEvent(Second.EventObject event) {
            second.add(event);
        }
    }

    static class First {
        record EventObject(String message) {
        }
    }

    static class Second {
        record EventObject(String message) {
        }
    }
}
