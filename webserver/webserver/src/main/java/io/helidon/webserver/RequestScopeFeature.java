/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.Default;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Configuration;
import io.helidon.http.Headers__ServiceDescriptor;
import io.helidon.http.HttpPrologue__ServiceDescriptor;
import io.helidon.http.ServerRequestHeaders__ServiceDescriptor;
import io.helidon.service.registry.Scope;
import io.helidon.service.registry.Scopes;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest__ServiceDescriptor;
import io.helidon.webserver.http.ServerResponse__ServiceDescriptor;
import io.helidon.webserver.spi.ServerFeature;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 900) // very high weight, so injection runs within request scope, but lower than context
class RequestScopeFeature implements ServerFeature {
    private final boolean enabled;
    private final Supplier<Scopes> scopes;

    @Service.Inject
    RequestScopeFeature(@Configuration.Value("server.inject.request-scope.enabled")
                        @Default.Boolean(false) boolean enabled,
                        Supplier<Scopes> scopes) {
        this.enabled = enabled;
        this.scopes = scopes;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (enabled) {
            Set<String> sockets = featureContext.sockets();
            for (String socket : sockets) {
                featureContext.socket(socket)
                        .httpRouting()
                        .addFilter(new RequestScopeFilter(scopes.get()));
            }
        }
    }

    @Override
    public String name() {
        return "request-scope";
    }

    @Override
    public String type() {
        return "request-scope";
    }

    private static class RequestScopeFilter implements Filter {
        private final Scopes scopes;

        private RequestScopeFilter(Scopes scopes) {
            this.scopes = scopes;
        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            try (Scope ignored = startRequestScope(req, res)) {
                chain.proceed();
            }
        }

        private Scope startRequestScope(RoutingRequest req, RoutingResponse res) {
            return scopes.createScope(Service.PerRequest.TYPE,
                                      "http_" + req.id(),
                                      Map.of(
                                              HttpPrologue__ServiceDescriptor.INSTANCE, req.prologue(),
                                              Headers__ServiceDescriptor.INSTANCE, req.headers(),
                                              ServerRequestHeaders__ServiceDescriptor.INSTANCE, req.headers(),
                                              ServerRequest__ServiceDescriptor.INSTANCE, req,
                                              ServerResponse__ServiceDescriptor.INSTANCE, res
                                      ));
        }
    }
}
