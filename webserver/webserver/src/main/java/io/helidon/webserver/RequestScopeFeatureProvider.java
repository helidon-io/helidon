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

package io.helidon.webserver;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Scopes;
import io.helidon.service.registry.Service;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * A Java {@link java.util.ServiceLoader} provider for adding declarative request scope to Helidon WebServer.
 * Note that request scope support is disabled by default.
 * <p>
 * To enable, use the following configuration (when configured via yaml):
 * <pre>
 * server:
 *   features:
 *     request-scope:
 *       enabled: true
 * </pre>
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 900) // very high weight, so injection runs within request scope, but lower than context
public class RequestScopeFeatureProvider implements ServerFeatureProvider<RequestScopeFeature> {
    private final Supplier<Scopes> scopes;

    RequestScopeFeatureProvider(Supplier<Scopes> scopes) {
        this.scopes = scopes;
    }

    @Override
    public String configKey() {
        return "request-scope";
    }

    @Override
    public RequestScopeFeature create(Config config, String name) {
        return RequestScopeFeature.create(scopes, config.get("enabled").asBoolean().orElse(false));
    }
}
