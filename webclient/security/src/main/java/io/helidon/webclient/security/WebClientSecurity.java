/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.webclient.security;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityClientBuilder;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client service for security propagation.
 */
public class WebClientSecurity implements WebClientService {
    private static final System.Logger LOGGER = System.getLogger(WebClientSecurity.class.getName());

    private static final String PROVIDER_NAME = "io.helidon.security.rest.client.security.providerName";

    private final Security security;

    private WebClientSecurity() {
        this(null);
    }

    private WebClientSecurity(Security security) {
        this.security = security;
    }

    /**
     * Creates new instance of client security service.
     *
     * @return client security service
     */
    public static WebClientSecurity create() {
        Context context = Contexts.context().orElseGet(Contexts::globalContext);

        return context.get(Security.class)
                .map(WebClientSecurity::new) // if available, use constructor with Security parameter
                .orElseGet(WebClientSecurity::new); // else use constructor without Security parameter
    }

    /**
     * Creates new instance of client security service base on {@link io.helidon.security.Security}.
     *
     * @param security security instance
     * @return client security service
     */
    public static WebClientSecurity create(Security security) {
        // if we have one more configuration parameter, we need to switch to builder based pattern
        return new WebClientSecurity(security);
    }

    @Override
    public String type() {
        return "security";
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        if ("true".equalsIgnoreCase(request.properties().get(OutboundConfig.PROPERTY_DISABLE_OUTBOUND))) {
            return chain.proceed(request);
        }

        Context requestContext = request.context();
        // context either from request or create a new one
        Optional<SecurityContext> maybeContext = requestContext.get(SecurityContext.class);

        SecurityContext context;

        if (security == null) {
            if (maybeContext.isEmpty()) {
                return chain.proceed(request);
            } else {
                context = maybeContext.get();
            }
        } else {
            // we have our own security - we need to use this instance for outbound,
            // so we cannot re-use the context
            context = createContext(request);
        }

        Tracer tracer = context.tracer();
        Span span;
        if (tracer == null) {
            span = null;
        } else {
            SpanContext parentSpanContext = context.tracingSpan();
            span = tracer.spanBuilder("security:outbound")
                    .update(builder -> {
                                if (parentSpanContext != null) {
                                    builder.parent(parentSpanContext);
                                }
                            }
                    )
                    .start();

        }
        String explicitProvider = request.properties().get(PROVIDER_NAME);

        OutboundSecurityClientBuilder clientBuilder;

        try {
            SecurityEnvironment.Builder outboundEnv = context.env()
                    .derive()
                    .clearHeaders()
                    .clearQueryParams();

            outboundEnv.method(request.method().text())
                    .path(request.uri().path().path())
                    .targetUri(request.uri().toUri())
                    .queryParams(request.uri().query());

            request.headers()
                    .stream()
                    .forEach(headerValue -> outboundEnv.header(headerValue.name(), headerValue.values()));

            EndpointConfig.Builder outboundEp = context.endpointConfig().derive();
            Map<String, String> propMap = request.properties();

            for (String name : propMap.keySet()) {
                Optional.ofNullable(request.properties().get(name))
                        .ifPresent(property -> outboundEp.addAtribute(name, property));
            }

            clientBuilder = context.outboundClientBuilder()
                    .outboundEnvironment(outboundEnv)
                    .outboundEndpointConfig(outboundEp)
                    .explicitProvider(explicitProvider);

        } catch (Exception e) {
            traceError(span, e, null);

            throw e;
        }

        OutboundSecurityResponse providerResponse = clientBuilder.submit();
        return processResponse(request, span, providerResponse, chain);
    }

    private WebClientServiceResponse processResponse(WebClientServiceRequest request,
                                                     Span span,
                                                     OutboundSecurityResponse providerResponse,
                                                     Chain chain) {
        try {
            switch (providerResponse.status()) {
            case FAILURE:
            case FAILURE_FINISH:
                traceError(span,
                           providerResponse.throwable().orElse(null),
                           providerResponse.description()
                                   .orElse(providerResponse.status().toString()));
                break;
            case ABSTAIN:
            case SUCCESS:
            case SUCCESS_FINISH:
            default:
                break;
            }

            Map<String, List<String>> newHeaders = providerResponse.requestHeaders();

            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Client filter header(s). SIZE: " + newHeaders.size());
            }

            ClientRequestHeaders clientHeaders = request.headers();
            for (Map.Entry<String, List<String>> entry : newHeaders.entrySet()) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "    + Header: " + entry.getKey() + ": " + entry.getValue());
                }

                //replace existing
                HeaderName headerName = HeaderNames.create(entry.getKey());
                clientHeaders.set(headerName, entry.getValue().toArray(new String[0]));
            }
            if (span != null) {
                span.end();
            }
            return chain.proceed(request);
        } catch (Exception e) {
            traceError(span, e, null);
            throw e;
        }
    }

    private SecurityContext createContext(WebClientServiceRequest request) {
        SecurityContext.Builder builder = security.contextBuilder(UUID.randomUUID().toString())
                .endpointConfig(EndpointConfig.builder()
                                        .build())
                .env(SecurityEnvironment.builder()
                             .path(request.uri().path().path())
                             .build());
        request.context().get(Tracer.class).ifPresent(builder::tracingTracer);
        request.context().get(SpanContext.class).ifPresent(builder::tracingSpan);
        return builder.build();
    }

    static void traceError(Span span, Throwable throwable, String description) {
        // failed
        if (span == null) {
            return;
        }
        span.status(Span.Status.ERROR);

        if (throwable == null) {
            span.addEvent("error", Map.of("message", description,
                                          "error.kind", "SecurityException"));
            span.end();
        } else {
            span.end(throwable);
        }
    }
}
