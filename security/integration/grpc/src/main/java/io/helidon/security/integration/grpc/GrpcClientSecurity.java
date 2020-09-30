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

package io.helidon.security.integration.grpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityClientBuilder;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.common.OutboundTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.webserver.ServerRequest;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import static io.helidon.security.integration.grpc.GrpcSecurity.ABAC_ATTRIBUTE_METHOD;

/**
 * A gRPC {@link CallCredentials} implementation.
 * <p>
 * Only works as part of integration with the Helidon Security component.
 */
public final class GrpcClientSecurity
        extends CallCredentials {

    /**
     * Property name for outbound security provider name. Set this with
     * {@link GrpcClientSecurity.Builder#property(String, Object)}.
     */
    public static final String PROPERTY_PROVIDER = "io.helidon.security.integration.grpc.GrpcClientSecurity.explicitProvider";

    private final SecurityContext context;

    private final Map<String, Object> properties;

    private GrpcClientSecurity(SecurityContext context, Map<String, Object> properties) {
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        OutboundTracing tracing = SecurityTracing.get().outboundTracing();

        String explicitProvider = (String) properties.get(PROPERTY_PROVIDER);

        try {
            MethodDescriptor<?, ?> methodDescriptor = requestInfo.getMethodDescriptor();
            String methodName = methodDescriptor.getFullMethodName();
            SecurityEnvironment.Builder outboundEnv = context.env()
                    .derive()
                    .clearHeaders();

            outboundEnv.path(methodName)
                    .method(methodName)
                    .addAttribute(ABAC_ATTRIBUTE_METHOD, methodDescriptor)
                    .transport("grpc")
                    .build();

            EndpointConfig.Builder outboundEp = context.endpointConfig().derive();

            properties.forEach(outboundEp::addAtribute);

            OutboundSecurityClientBuilder clientBuilder = context.outboundClientBuilder()
                    .outboundEnvironment(outboundEnv)
                    .tracingSpan(tracing.findParent().orElse(null))
                    .outboundEndpointConfig(outboundEp)
                    .explicitProvider(explicitProvider);

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

            Metadata metadata = new Metadata();
            for (Map.Entry<String, List<String>> entry : newHeaders.entrySet()) {
                Metadata.Key<String> key = Metadata.Key.of(entry.getKey(), Metadata.ASCII_STRING_MARSHALLER);
                for (String value : entry.getValue()) {
                    metadata.put(key, value);
                }
            }

            applier.apply(metadata);

            tracing.finish();
        } catch (SecurityException e) {
            tracing.error(e);
            applier.fail(Status.UNAUTHENTICATED.withDescription("Security principal propagation error").withCause(e));
        } catch (Exception e) {
            tracing.error(e);
            applier.fail(Status.UNAUTHENTICATED.withDescription("Unknown error").withCause(e));
        }
    }

    @Override
    public void thisUsesUnstableApi() {
    }

    /**
     * Create a {@link GrpcClientSecurity} instance.
     *
     * @param securityContext  the {@link SecurityContext} to use
     *
     * @return a {@link GrpcClientSecurity} builder.
     */
    public static GrpcClientSecurity create(SecurityContext securityContext) {
        return builder(securityContext).build();
    }

    /**
     * Create a {@link GrpcClientSecurity} instance.
     *
     * @param req  the http {@link ServerRequest} to use to obtain the {@link SecurityContext}
     *
     * @return a {@link GrpcClientSecurity} builder.
     */
    public static GrpcClientSecurity create(ServerRequest req) {
        return builder(req).build();
    }

    /**
     * Obtain a {@link GrpcClientSecurity} builder.
     *
     * @param securityContext  the {@link SecurityContext} to use
     *
     * @return a {@link GrpcClientSecurity} builder.
     */
    public static Builder builder(SecurityContext securityContext) {
        return new Builder(securityContext);
    }

    /**
     * Obtain a {@link GrpcClientSecurity} builder.
     *
     * @param req  the http {@link ServerRequest} to use to obtain the {@link SecurityContext}
     *
     * @return a {@link GrpcClientSecurity} builder.
     */
    public static Builder builder(ServerRequest req) {
        return builder(getContext(req));
    }

    private static SecurityContext getContext(ServerRequest req) {
        return req.context().get(SecurityContext.class)
                .orElseThrow(() -> new RuntimeException("Failed to get security context from request, security not configured"));
    }

    /**
     * A builder of {@link GrpcClientSecurity} instances.
     */
    public static final class Builder
            implements io.helidon.common.Builder<GrpcClientSecurity> {

        private final SecurityContext securityContext;

        private final Map<String, Object> properties;

        private Builder(SecurityContext securityContext) {
            this.securityContext = securityContext;
            this.properties = new HashMap<>();
        }

        /**
         * Set a new property that may be used by {@link io.helidon.security.spi.SecurityProvider}s
         * when creating the credentials to apply to the call.
         *
         * @param name  property name.
         * @param value (new) property value. {@code null} value removes the property
         *              with the given name.
         *
         * @return the updated builder.
         */
        public Builder property(String name, Object value) {
            if (value == null) {
                properties.remove(name);
            } else {
                properties.put(name, value);
            }
            return this;
        }

        @Override
        public GrpcClientSecurity build() {
            return new GrpcClientSecurity(securityContext, properties);
        }
    }
}
