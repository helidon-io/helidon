/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.jersey;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityClientBuilder;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;

import io.opentracing.Span;

/**
 * JAX-RS client filter propagating current context principal.
 * <p>
 * Only works as part of integration with Security component.
 * This class is public to allow unit testing from providers (without invoking an HTTP request)
 */
@Provider
@ConstrainedTo(RuntimeType.CLIENT)
public class ClientSecurityFilter implements ClientRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(ClientSecurityFilter.class.getName());

    /**
     * Create an instance of this filter (used by Jersey or for unit tests, do not use explicitly in your production code).
     */
    public ClientSecurityFilter() {
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        try {
            doFilter(requestContext);
        } catch (Throwable e) {
            //If I do not log the exception here, it would be silently consumed and a 500 response provided to caller
            LOGGER.log(Level.WARNING, "Failed to process client filter.", e);
            throw e;
        }
    }

    private void doFilter(ClientRequestContext requestContext) throws IOException {
        //Try to have a look for @AuthenticatedClient annotation on client (if constructed as such) and use explicit provider
        // from there

        // first try to find the context on request configuration
        SecurityContext context = (SecurityContext) requestContext.getProperty(ClientSecurityFeature.PROPERTY_CONTEXT);

        if (null == context) {
            throw new SecurityException("SecurityContext must be configured as property \"" + ClientSecurityFeature
                    .PROPERTY_CONTEXT + " on request");
        }

        Span span = context.getTracer()
                .buildSpan("security:outbound")
                .asChildOf(context.getTracingSpan())
                .start();

        String explicitProvider = (String) requestContext.getProperty(ClientSecurityFeature.PROPERTY_PROVIDER);

        try {
            SecurityEnvironment.Builder outboundEnv = context.getEnv().derive();
            outboundEnv.method(requestContext.getMethod())
                    .path(requestContext.getUri().getPath())
                    .targetUri(requestContext.getUri())
                    .headers(HttpUtil.toSimpleMap(requestContext.getStringHeaders()));

            EndpointConfig.Builder outboundEp = context.getEndpointConfig().derive();
            for (String name : requestContext.getPropertyNames()) {
                outboundEp.addAtribute(name, requestContext.getProperty(name));
            }

            OutboundSecurityClientBuilder clientBuilder = context.outboundClientBuilder()
                    .outboundEnvironment(outboundEnv)
                    .outboundEndpointConfig(outboundEp)
                    .explicitProvider(explicitProvider);

            OutboundSecurityResponse providerResponse = clientBuilder.buildAndGet();

            switch (providerResponse.getStatus()) {
            case FAILURE:
            case FAILURE_FINISH:
                HttpUtil.traceError(span,
                                    providerResponse.getThrowable().orElse(null),
                                    providerResponse.getDescription()
                                            .orElse(providerResponse.getStatus().toString()));
                break;
            case ABSTAIN:
            case SUCCESS:
            case SUCCESS_FINISH:
            default:
                break;
            }
            // TODO check response status - maybe entity was updated?
            // see MIC-6785

            Map<String, List<String>> newHeaders = providerResponse.getRequestHeaders();

            LOGGER.finest(() -> "Client filter header(s). SIZE: " + newHeaders.size());

            MultivaluedMap<String, Object> hdrs = requestContext.getHeaders();
            for (Map.Entry<String, List<String>> entry : newHeaders.entrySet()) {
                LOGGER.finest(() -> "    + Header: " + entry.getKey() + ": " + entry.getValue());

                //replace existing
                hdrs.remove(entry.getKey());
                for (String value : entry.getValue()) {
                    hdrs.add(entry.getKey(), value);
                }
            }
            span.finish();
        } catch (SecurityException e) {
            HttpUtil.traceError(span, e, null);

            throw new IOException("Security principal propagation error!", e);
        } catch (Exception e) {
            HttpUtil.traceError(span, e, null);

            throw e;
        }
    }
}
