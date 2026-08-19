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

package io.helidon.messaging;

import io.helidon.common.Api;

/**
 * Incoming connector for one configured binding.
 * <p>
 * The messaging runtime invokes {@link #run(IncomingConnectorContext)} on an owned virtual thread. The connector
 * establishes its transport resources, calls {@link IncomingConnectorContext#awaitRunning()}, and starts acquiring
 * deliveries only when that method returns {@code true}.
 */
@Api.Preview
public interface IncomingConnector extends Connector {
    /**
     * Run this connector until it is drained or closed.
     * <p>
     * Before acquiring the first delivery, the connector must synchronously establish the transport resources needed
     * to run and call {@link IncomingConnectorContext#awaitRunning()}. It must return without acquiring deliveries when
     * that method returns {@code false}. Normal return after {@link #drain()} must include final transport settlement
     * and checkpointing.
     *
     * @param context incoming connector context
     */
    void run(IncomingConnectorContext context);

    /**
     * Stop acquiring new transport deliveries while allowing already acquired deliveries to settle. Once those
     * delivery handoffs finish, {@link #run(IncomingConnectorContext)} must finish its transport checkpoint and return.
     */
    void drain();
}
