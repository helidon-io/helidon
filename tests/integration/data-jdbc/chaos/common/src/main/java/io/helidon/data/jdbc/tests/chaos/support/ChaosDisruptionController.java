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

import java.time.Duration;

/**
 * Uses a database control connection to synchronize and terminate one application JDBC session.
 */
public interface ChaosDisruptionController extends AutoCloseable {
    /**
     * Locks the gate row until the returned scope closes.
     *
     * @return gate lock scope
     * @throws Exception when the control connection cannot acquire the lock
     */
    AutoCloseable lockGate() throws Exception;

    /**
     * Waits until the selected database session is executing the gate update and blocked by the control transaction.
     *
     * @param sessionId database session identifier
     * @param timeout maximum observation duration
     * @throws Exception when observation fails or the deadline expires
     */
    void awaitGateWait(long sessionId, Duration timeout) throws Exception;

    /**
     * Terminates the selected database session through an independent control connection.
     *
     * @param sessionId database session identifier
     * @throws Exception when termination fails
     */
    void terminateSession(long sessionId) throws Exception;

    /**
     * Closes controller-owned resources.
     *
     * @throws Exception when controller cleanup fails
     */
    @Override
    void close() throws Exception;
}
