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

package io.helidon.webserver.http;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.helidon.common.Api;

/**
 * Alternative service runtimes owned by one server listener.
 */
@Api.Internal
public final class AltSvcRuntimeRegistry {
    private final ConcurrentMap<AltSvcConfig, AltSvc> runtimes = new ConcurrentHashMap<>();

    private AltSvcRuntimeRegistry() {
    }

    /**
     * Create an empty listener runtime registry.
     *
     * @return a new registry
     */
    public static AltSvcRuntimeRegistry create() {
        return new AltSvcRuntimeRegistry();
    }

    /**
     * Resolve the runtime for a configuration.
     *
     * @param config alternative service configuration
     * @return listener-owned runtime
     */
    public AltSvc resolve(AltSvcConfig config) {
        return runtimes.computeIfAbsent(config, AltSvc::create);
    }
}
