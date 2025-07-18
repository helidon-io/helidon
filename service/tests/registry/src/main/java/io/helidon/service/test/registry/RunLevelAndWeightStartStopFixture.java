/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import io.helidon.common.Weight;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.PostConstruct;
import io.helidon.service.registry.Service.PreDestroy;

interface RunLevelAndWeightStartStopFixture {

    @Service.Singleton
    static class State {
        final Queue<String> startUpSequence = new ArrayBlockingQueue<>(4);
        final Queue<String> shutDownSequence = new ArrayBlockingQueue<>(4);
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP) // more significant
    @Weight(1) // less significant
    static class Service1 implements RunLevelAndWeightStartStopFixture {
        final State state;

        @Service.Inject
        Service1(State state) {
            this.state = state;
        }

        @PostConstruct
        void startUp() {
            state.startUpSequence.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            state.shutDownSequence.add(getClass().getSimpleName());
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP) // more significant
    @Weight(2) // more significant
    static class Service2 implements RunLevelAndWeightStartStopFixture {
        final State state;

        @Service.Inject
        Service2(State state) {
            this.state = state;
        }

        @PostConstruct
        void startUp() {
            state.startUpSequence.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            state.shutDownSequence.add(getClass().getSimpleName());
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP + 1) // less significant
    @Weight(1) // less significant
    static class Service3 implements RunLevelAndWeightStartStopFixture {
        final State state;

        @Service.Inject
        Service3(State state) {
            this.state = state;
        }

        @PostConstruct
        void startUp() {
            state.startUpSequence.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            state.shutDownSequence.add(getClass().getSimpleName());
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP + 1) // less significant
    @Weight(2) // more significant
    static class Service4 implements RunLevelAndWeightStartStopFixture {
        final State state;

        @Service.Inject
        Service4(State state) {
            this.state = state;
        }

        @PostConstruct
        void startUp() {
            state.startUpSequence.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            state.shutDownSequence.add(getClass().getSimpleName());
        }
    }

}
