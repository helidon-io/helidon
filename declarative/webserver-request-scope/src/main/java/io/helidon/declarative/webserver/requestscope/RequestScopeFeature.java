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

package io.helidon.declarative.webserver.requestscope;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Context__ServiceDescriptor;
import io.helidon.http.Headers__ServiceDescriptor;
import io.helidon.http.Prologue__ServiceDescriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.RequestScopeControl;
import io.helidon.service.inject.api.Scope;
import io.helidon.webserver.ServerRequest__ServiceDescriptor;
import io.helidon.webserver.ServerResponse__ServiceDescriptor;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

@Injection.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 900) // very high weight, so injection runs within request scope, but lower than context
class RequestScopeFeature implements HttpFeature {
    private final RequestScopeControl requestCtrl;

    @Injection.Inject
    RequestScopeFeature(RequestScopeControl requestCtrl) {
        this.requestCtrl = requestCtrl;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(new RequestScopeFilter(requestCtrl));
    }

    private static class RequestScopeFilter implements Filter {
        private final RequestScopeControl requestCtrl;

        private RequestScopeFilter(RequestScopeControl requestCtrl) {
            this.requestCtrl = requestCtrl;
        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            try (Scope ignored = startRequestScope(req, res)) {
                chain.proceed();
            }
        }

        private Scope startRequestScope(RoutingRequest req, RoutingResponse res) {
            return requestCtrl.startRequestScope("http_" + req.id(),
                                                 Map.of(
                                                         Context__ServiceDescriptor.INSTANCE, (Supplier<Context>) req::context,
                                                         Prologue__ServiceDescriptor.INSTANCE, req.prologue(),
                                                         Headers__ServiceDescriptor.INSTANCE, req.headers(),
                                                         ServerRequest__ServiceDescriptor.INSTANCE, req,
                                                         ServerResponse__ServiceDescriptor.INSTANCE, res
                                                 ));
        }
    }
}
