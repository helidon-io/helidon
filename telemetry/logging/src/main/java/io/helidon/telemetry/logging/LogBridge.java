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
package io.helidon.telemetry.logging;

import io.helidon.common.Api;

/**
 * Caller-owned logging hooks that forward records to a supplied OpenTelemetry instance.
 * Stopping detaches hooks without closing resources used by admitted publications. The caller
 * drains those publications before closing this bridge. Neither operation may shut down the SDK
 * or the application's logging context. Both operations must be idempotent and retryable after failure.
 */
@Api.Preview
public interface LogBridge extends AutoCloseable {
    /**
     * Attach integration-owned hooks. Called once, after the caller retains this bridge for cleanup.
     * If startup fails, {@link #stop()} and {@link #close()} must clean up any partially attached hooks.
     */
    void start();

    /**
     * Whether this registration is active. A backend can terminate its registration when its logging
     * context stops. In particular, after restarting a stopped Logback context, close the old
     * registration and create a new bridge.
     * The caller's publication boundary may still decline work while a bridge is active.
     *
     * @return whether this registration is active
     */
    boolean active();

    /**
     * Detach integration-owned hooks, retaining resources needed by publications already in progress.
     */
    void stop();

    /**
     * Release integration-owned resources after stopping and draining publications.
     */
    @Override
    void close();
}
