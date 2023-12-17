/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Support for resetting (mostly from testing).
 * The intended behavior is to have a single service registry for the duration of the runtime of JVM.
 * Helidon applications are designed that way.
 */
public class ResettableHandler {
    private static final Lock RESETTABLES_LOCK = new ReentrantLock();
    private static final List<Resettable> RESETTABLES = new ArrayList<>();

    /**
     * Protected constructor to allow creation of subclasses that need to support reset.
     */
    protected ResettableHandler() {
    }

    /**
     * Resets the bootstrap state.
     */
    protected static void reset() {
        try {
            RESETTABLES_LOCK.lock();
            RESETTABLES.forEach(it -> it.reset(true));
            RESETTABLES.clear();
        } finally {
            RESETTABLES_LOCK.unlock();
        }
    }

    /**
     * Register a resettable instance. When {@link #reset()} is called, this instance is removed from the list.
     *
     * @param instance resettable type that can be reset during testing
     */
    protected static void addResettable(Resettable instance) {
        try {
            RESETTABLES_LOCK.lock();
            RESETTABLES.add(instance);
        } finally {
            RESETTABLES_LOCK.unlock();
        }
    }
}
