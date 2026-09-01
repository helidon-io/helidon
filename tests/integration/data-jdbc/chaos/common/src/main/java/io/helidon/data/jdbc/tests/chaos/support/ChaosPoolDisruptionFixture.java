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
package io.helidon.data.jdbc.tests.chaos.support;

import java.util.Objects;

/**
 * Composes a database disruption controller, pool-idle assertion, and pool close action.
 */
public final class ChaosPoolDisruptionFixture implements ChaosDisruptionFixture {
    private final ChaosDisruptionController controller;
    private final Runnable poolIdleAssertion;
    private final AutoCloseable pool;

    /**
     * Creates a disruption fixture from database-specific control and pool behavior.
     *
     * @param controller independent database controller
     * @param poolIdleAssertion assertion that the application pool has no active leases
     * @param pool application pool
     */
    public ChaosPoolDisruptionFixture(ChaosDisruptionController controller,
                                      Runnable poolIdleAssertion,
                                      AutoCloseable pool) {
        this.controller = Objects.requireNonNull(controller);
        this.poolIdleAssertion = Objects.requireNonNull(poolIdleAssertion);
        this.pool = Objects.requireNonNull(pool);
    }

    @Override
    public ChaosDisruptionController controller() {
        return controller;
    }

    @Override
    public void assertPoolIdle() {
        poolIdleAssertion.run();
    }

    @Override
    public void close() throws Exception {
        try {
            controller.close();
        } finally {
            pool.close();
        }
    }
}
