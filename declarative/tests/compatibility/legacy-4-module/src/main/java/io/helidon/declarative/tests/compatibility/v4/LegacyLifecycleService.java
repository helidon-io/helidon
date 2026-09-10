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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.service.registry.Service;

/**
 * Lifecycle callbacks dispatched through a Helidon 4 service descriptor.
 */
@Service.Singleton
public class LegacyLifecycleService {
    private final AtomicInteger initializations = new AtomicInteger();
    private final AtomicInteger destructions = new AtomicInteger();

    /**
     * Number of post-construction callbacks.
     *
     * @return initialization count
     */
    public int initializations() {
        return initializations.get();
    }

    /**
     * Number of pre-destruction callbacks.
     *
     * @return destruction count
     */
    public int destructions() {
        return destructions.get();
    }

    @Service.PostConstruct
    void initialize() {
        initializations.incrementAndGet();
    }

    @Service.PreDestroy
    void destroy() {
        destructions.incrementAndGet();
    }
}
