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

import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.PostConstruct;
import io.helidon.service.registry.Service.PreDestroy;

interface StartStopFixture {
    Queue<String> startUpQueue = new ArrayBlockingQueue<>(3);
    Queue<String> shutDownQueue = new ArrayBlockingQueue<>(3);

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP)
    static class Service1 implements StartStopFixture {
        @PostConstruct
        void startUp() {
            startUpQueue.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            shutDownQueue.add(getClass().getSimpleName());
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP + 1)
    static class Service2 implements StartStopFixture {
        @PostConstruct
        void startUp() {
            startUpQueue.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            shutDownQueue.add(getClass().getSimpleName());
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.STARTUP + 2)
    static class Service3 implements StartStopFixture {
        @PostConstruct
        void startUp() {
            startUpQueue.add(getClass().getSimpleName());
        }

        @PreDestroy
        void shutDown() {
            shutDownQueue.add(getClass().getSimpleName());
        }
    }

}
