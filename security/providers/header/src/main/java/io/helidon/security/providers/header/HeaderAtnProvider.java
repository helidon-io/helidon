/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.providers.header;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

/**
 * Security provider that extracts a username (or service name) from a header.
 * This provider also supports propagation of identity through a header.
 */
public class HeaderAtnProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private final boolean optional;
    private final boolean authenticate;
    private final boolean propagate;
    private final SubjectType subjectType;
    private final TokenHandler atnTokenHandler;
    private final TokenHandler outboundTokenHandler;
    private final OutboundConfig outboundConfig;
    private final TokenHandler defaultOutboundTokenHandler;

    private HeaderAtnProvider(Builder builder) {
        this.optional = builder.optional;
        this.authenticate = builder.authenticate;
        this.propagate = builder.propagate;
        this.subjectType = builder.subjectType;
        this.atnTokenHandler = builder.atnTokenHandler;
        this.outboundTokenHandler = builder.outboundTokenHandler;
        this.outboundConfig = builder.outboundConfig;

        this.defaultOutboundTokenHandler = (outboundTokenHandler == null) ? atnTokenHandler : outboundTokenHandler;
    }

    /**
     * Create provider instance from configuration.
     *
     * @param config configuration of this provider
     * @return provider instance
     */
    public static HeaderAtnProvider create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A builder for this provider.
     *
     * @return builder to create a new instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        if (!authenticate) {
            return AuthenticationResponse.abstain();
        }

        Optional<String> username;
        try {
            username = atnTokenHandler.extractToken(providerRequest.env().headers());
        } catch (Exception e) {
            if (optional) {
                return AuthenticationResponse.abstain();
            } else {
                return AuthenticationResponse.failed("Header not available or in a wrong format", e);
            }
        }

        return username
                .map(Principal::create)
                .map(principal -> {
                    if (subjectType == SubjectType.USER) {
                        return AuthenticationResponse.success(principal);
                    } else {
                        return AuthenticationResponse.successService(principal);
                    }
                })
                .orElseGet(() -> {
                               if (optional) {
                                   return AuthenticationResponse.abstain();
                               } else {
                                   return AuthenticationResponse.failed("Header not available or in a wrong format");
                               }
                           }
                );
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        return propagate;
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {

        Optional<Subject> toPropagate;
        if (subjectType == SubjectType.USER) {
            toPropagate = providerRequest.securityContext().user();
        } else {
            toPropagate = providerRequest.securityContext().service();
        }

        // find the target
        var target = outboundConfig.findTargetCustomObject(outboundEnv,
                                                           HeaderAtnOutboundConfig.class,
                                                           HeaderAtnOutboundConfig::create,
                                                           HeaderAtnOutboundConfig::create);

        // we have no target, let's fall back to original behavior
        if (target.isEmpty()) {
            if (outboundTokenHandler != null) {
                return toPropagate.map(Subject::principal)
                        .map(Principal::id)
                        .map(id -> respond(outboundEnv, outboundTokenHandler, id))
                        .orElseGet(OutboundSecurityResponse::abstain);
            }
            return OutboundSecurityResponse.abstain();
        }
        // we found a target
        HeaderAtnOutboundConfig outboundConfig = target.get();

        TokenHandler tokenHandler = outboundConfig.tokenHandler()
                .orElse(defaultOutboundTokenHandler);

        return outboundConfig.explicitUser()
                .or(() -> toPropagate.map(Subject::principal)
                        .map(Principal::id))
                .map(id -> respond(outboundEnv, tokenHandler, id))
                .orElseGet(OutboundSecurityResponse::abstain);
    }

    private OutboundSecurityResponse respond(SecurityEnvironment outboundEnv,
                                             TokenHandler handler,
                                             String username) {
        // This is for backward compatibility
        Map<String, List<String>> headers = new HashMap<>(outboundEnv.headers());
        handler.header(headers, username);
        return OutboundSecurityResponse.withHeaders(headers);
    }

    /**
     * A fluent api Builder for {@link HeaderAtnProvider}.
     */
    public static final class Builder implements io.helidon.common.Builder<HeaderAtnProvider> {
        private final OutboundConfig.Builder outboundBuilder = OutboundConfig.builder();

        private boolean optional = false;
        private boolean authenticate = true;
        private Boolean propagate;
        private SubjectType subjectType = SubjectType.USER;
        private TokenHandler atnTokenHandler;
        private TokenHandler outboundTokenHandler;
        private OutboundConfig outboundConfig;

        private Builder() {
        }

        @Override
        public HeaderAtnProvider build() {
            outboundConfig = outboundBuilder.build();

            if (propagate == null || propagate) {
                this.propagate = (outboundTokenHandler != null) || (outboundConfig.targets().size() > 0);
            }

            if (outboundConfig.targets().size() > 0 && outboundTokenHandler == null) {
                outboundTokenHandler = atnTokenHandler;
            }
            return new HeaderAtnProvider(this);
        }

        /**
         * Load this builder from a configuration.
         *
         * @param config configuration to load from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("authenticate").asBoolean().ifPresent(this::authenticate);
            config.get("propagate").asBoolean().ifPresent(this::propagate);
            config.get("principal-type").asString().map(SubjectType::valueOf).ifPresent(this::subjectType);
            config.get("atn-token").as(TokenHandler::create).ifPresent(this::atnTokenHandler);
            config.get("outbound-token").as(TokenHandler::create).ifPresent(this::outboundTokenHandler);

            config.get("outbound").asList(OutboundTarget::create)
                    .ifPresent(it -> it.forEach(outboundBuilder::addTarget));

            return this;
        }

        /**
         * Principal type this provider extracts (and also propagates).
         *
         * @param subjectType type of principal
         * @return updated builder instance
         */
        public Builder subjectType(SubjectType subjectType) {
            this.subjectType = subjectType;

            switch (subjectType) {
            case USER:
            case SERVICE:
                break;
            default:
                throw new SecurityException("Invalid configuration. Principal type not supported: " + subjectType);
            }

            return this;
        }

        /**
         * Whether to propagate identity.
         *
         * @param propagate whether to propagate identity (true) or not (false)
         * @return updated builder instance
         */
        public Builder propagate(boolean propagate) {
            this.propagate = propagate;
            return this;
        }

        /**
         * Whether to authenticate requests.
         *
         * @param authenticate whether to authenticate (true) or not (false)
         * @return updated builder instance
         */
        public Builder authenticate(boolean authenticate) {
            this.authenticate = authenticate;
            return this;
        }

        /**
         * Token handler to extract username from request.
         *
         * @param tokenHandler token handler instance
         * @return updated builder instance
         */
        public Builder atnTokenHandler(TokenHandler tokenHandler) {
            this.atnTokenHandler = tokenHandler;

            return this;
        }

        /**
         * Token handler to create outbound headers to propagate identity.
         * If not defined, {@link #atnTokenHandler} will be used.
         *
         * @param tokenHandler token handler instance
         * @return updated builder instance
         */
        public Builder outboundTokenHandler(TokenHandler tokenHandler) {
            this.outboundTokenHandler = tokenHandler;

            return this;
        }

        /**
         * Whether authentication is required.
         * By default, request will fail if the username cannot be extracted.
         * If set to false, request will process and this provider will abstain.
         *
         * @param optional whether authentication is optional (true) or required (false)
         * @return updated builder instance
         */
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Configure outbound target for identity propagation.
         *
         * @param target outbound target
         * @return updated builder instance
         */
        public Builder addOutboundTarget(OutboundTarget target) {
            this.outboundBuilder.addTarget(target);
            return this;
        }
    }
}
