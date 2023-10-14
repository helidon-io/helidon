/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.Context;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.tracing.Span;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * HTTP filter that registers security context.
 */
class SecurityContextFilter implements Filter {
    private static final AtomicInteger SECURITY_COUNTER = new AtomicInteger();

    private final Security security;
    private final SecurityHandler defaultHandler;

    SecurityContextFilter(Security security, SecurityHandler defaultHandler) {
        this.security = security;
        this.defaultHandler = defaultHandler;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        Map<String, List<String>> allHeaders = new TreeMap<>(String::compareToIgnoreCase);
        allHeaders.putAll(req.headers().toMap());

        Context context = req.context();
        Optional<Map> newHeaders = context.get(SecurityHttpFeature.CONTEXT_ADD_HEADERS, Map.class);
        newHeaders.ifPresent(allHeaders::putAll);

        //make sure there is no context
        if (context.get(SecurityContext.class).isEmpty()) {
            SecurityEnvironment env = security.environmentBuilder()
                    .targetUri(req.requestedUri().toUri())
                    .path(req.path().path())
                    .method(req.prologue().method().text())
                    .addAttribute("remotePeer", req.remotePeer())
                    .addAttribute("userIp", req.remotePeer().host())
                    .addAttribute("userPort", req.remotePeer().port())
                    .transport(req.isSecure() ? "https" : "http")
                    .headers(allHeaders)
                    .build();
            EndpointConfig ec = EndpointConfig.builder()
                    .build();

            SecurityContext.Builder contextBuilder = security.contextBuilder(String.valueOf(SECURITY_COUNTER.incrementAndGet()))
                    .env(env)
                    .endpointConfig(ec);

            // only register if exists
            Span.current().ifPresent(it -> contextBuilder.tracingSpan(it.context()));

            SecurityContext securityContext = contextBuilder.build();

            context.register(securityContext);
            context.register(defaultHandler);
        }

        chain.proceed();
    }
}
