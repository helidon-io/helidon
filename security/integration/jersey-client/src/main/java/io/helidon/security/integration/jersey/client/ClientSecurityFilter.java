/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityClientBuilder;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.OutboundTracing;
import io.helidon.security.integration.common.SecurityTracing;

/**
 * JAX-RS client filter propagating current context principal.
 * <p>
 * Only works as part of integration with Security component.
 * This class is public to allow unit testing from providers (without invoking an HTTP request)
 */
@ConstrainedTo(RuntimeType.CLIENT)
@Priority(Priorities.AUTHENTICATION)
public class ClientSecurityFilter implements ClientRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(ClientSecurityFilter.class.getName());
    private static final AtomicLong CONTEXT_COUNTER = new AtomicLong();

    /**
     * Create an instance of this filter (used by Jersey or for unit tests, do not use explicitly in your production code).
     */
    public ClientSecurityFilter() {
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        try {
            doFilter(requestContext);
        } catch (Throwable e) {
            //If I do not log the exception here, it would be silently consumed and a 500 response provided to caller
            LOGGER.log(Level.WARNING, "Failed to process client filter.", e);
            throw e;
        }
    }

    private void doFilter(ClientRequestContext requestContext) {
        // find the context - if not cannot propagate
        Optional<SecurityContext> securityContext = findContext(requestContext);

        if (securityContext.isPresent()) {
            outboundSecurity(requestContext, securityContext.get());
        } else {
            LOGGER.finest("Security context not available, using empty one. You can define it using "
                                  + "property \""
                                  + ClientSecurity.PROPERTY_CONTEXT + "\" on request");

            // use current context, or create a new one if we run outside of Helidon context
            Context context = Contexts.context()
                    .orElseGet(() -> Context.builder()
                            .id("security-" + CONTEXT_COUNTER.incrementAndGet())
                            .build());

            // create a new security context for current request (not authenticated)
            Optional<SecurityContext> newSecurityContext = context.get(Security.class)
                .map(it -> it.createContext(context.id()));

            if (newSecurityContext.isPresent()) {
                // run in the context we obtained above with the new security context
                // we may still propagate security information (such as when we explicitly configure outbound
                // security in outbound target of a provider
                Contexts.runInContext(context, () -> outboundSecurity(requestContext, newSecurityContext.get()));
            } else {
                // we cannot do anything - security is not available in global or current context, cannot propagate
                LOGGER.finest("Security is not available in global or current context, cannot propagate identity.");
            }
        }
    }

    private void outboundSecurity(ClientRequestContext requestContext, SecurityContext securityContext) {
        OutboundTracing tracing = SecurityTracing.get().outboundTracing();

        Optional<String> explicityProvider = property(requestContext, String.class, ClientSecurity.PROPERTY_PROVIDER);

        try {
            SecurityEnvironment.Builder outboundEnv = securityContext.env()
                    .derive()
                    .clearHeaders();

            outboundEnv.method(requestContext.getMethod())
                    .path(requestContext.getUri().getPath())
                    .targetUri(requestContext.getUri())
                    .headers(requestContext.getStringHeaders());

            EndpointConfig.Builder outboundEp = securityContext.endpointConfig().derive();

            for (String name : requestContext.getConfiguration().getPropertyNames()) {
                outboundEp.addAtribute(name, requestContext.getConfiguration().getProperty(name));
            }

            for (String name : requestContext.getPropertyNames()) {
                outboundEp.addAtribute(name, requestContext.getProperty(name));
            }

            OutboundSecurityClientBuilder clientBuilder = securityContext.outboundClientBuilder()
                    .outboundEnvironment(outboundEnv)
                    .tracingSpan(tracing.findParent().orElse(null))
                    .outboundEndpointConfig(outboundEp);

            explicityProvider.ifPresent(clientBuilder::explicitProvider);

            OutboundSecurityResponse providerResponse = clientBuilder.buildAndGet();
            SecurityResponse.SecurityStatus status = providerResponse.status();
            tracing.logStatus(status);
            switch (status) {
            case FAILURE:
            case FAILURE_FINISH:
                providerResponse.throwable()
                        .ifPresentOrElse(tracing::error,
                                         () -> tracing.error(providerResponse.description().orElse("Failed")));

                break;
            case ABSTAIN:
            case SUCCESS:
            case SUCCESS_FINISH:
            default:
                break;
            }

            Map<String, List<String>> newHeaders = providerResponse.requestHeaders();

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
            tracing.finish();
        } catch (Exception e) {
            tracing.error(e);
            throw e;
        }
    }

    private Optional<SecurityContext> findContext(ClientRequestContext requestContext) {
        return  // client property
                property(requestContext, SecurityContext.class, ClientSecurity.PROPERTY_CONTEXT)
                        // then look for security context in context
                        .or(() -> Contexts.context().flatMap(ctx -> ctx.get(SecurityContext.class)));
    }

    private static <T> Optional<T> property(ClientRequestContext requestContext, Class<T> clazz, String propertyName) {
        return Optional.ofNullable(requestContext.getProperty(propertyName))
                .filter(clazz::isInstance)
                .or(() -> Optional.ofNullable(requestContext.getConfiguration().getProperty(propertyName))
                        .filter(clazz::isInstance))
                .map(clazz::cast);
    }
}
