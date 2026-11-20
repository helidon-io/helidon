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
package io.helidon.telemetry.logging.spi;

import io.helidon.common.Api;
import io.helidon.logging.common.spi.LoggingProvider;
import io.helidon.telemetry.logging.LogBridge;
import io.helidon.telemetry.logging.LogBridgeContext;

/**
 * Service-loaded integration with an explicitly selected logging backend.
 * Callers select at most one adapter for the active backend. Creating a bridge must not attach hooks;
 * the caller retains the returned bridge before starting it, so partial startup can be cleaned up and retried.
 */
@Api.Preview
public interface LogBridgeProvider {
    /**
     * Check backend compatibility without installing or reconfiguring logging hooks.
     *
     * @param provider logging provider selected using Helidon's provider ordering
     * @return whether the active backend matches this adapter
     */
    boolean supports(LoggingProvider provider);

    /**
     * Create an unstarted bridge without attaching hooks to the logging backend.
     *
     * @param context caller-supplied OpenTelemetry instance and publication boundary
     * @return unstarted bridge, never null
     */
    LogBridge create(LogBridgeContext context);
}
