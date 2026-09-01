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

/**
 * Owns deterministic lock and single-connection-pool controls for bounded concurrency failures.
 */
public interface ChaosConcurrencyFixture extends AutoCloseable {
    /**
     * Locks the gate row until the returned scope closes.
     *
     * @return gate lock scope
     * @throws Exception when the gate cannot be locked
     */
    AutoCloseable lockGate() throws Exception;

    /**
     * Borrows the sole application-pool lease until the returned scope closes.
     *
     * @return held lease scope
     * @throws Exception when the lease cannot be acquired
     */
    AutoCloseable holdOnlyPoolLease() throws Exception;

    /**
     * Asserts that the application pool has no active connection leases.
     */
    void assertPoolIdle();

    /**
     * Closes the fixture-owned pool and control resources.
     *
     * @throws Exception when cleanup fails
     */
    @Override
    void close() throws Exception;
}
