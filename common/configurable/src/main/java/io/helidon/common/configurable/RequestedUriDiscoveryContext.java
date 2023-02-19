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
package io.helidon.common.configurable;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Requested URI discovery settings for a socket.
 */
public interface RequestedUriDiscoveryContext {

    /**
     * Creates a new builder for a {@code RequestedUriDiscoveryContext}.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new builder for a {@code RequestedUriDiscoveryContext} using the provide discovery context config node.
     *
     * @param config discovery context config node
     * @return new builder
     */
    static Builder builder(Config config) {
        return builder().config(config);
    }

    /**
     * Creates a new {@code RequestedUriDiscoveryContext} from the provided discovery context config node.
     *
     * @param config node for the discovery context
     * @return new discovery context instance
     */
    static RequestedUriDiscoveryContext create(Config config) {
        return builder().config(config).build();
    }
    /**
     * Indicates if requested URI discovery is enabled.
     *
     * @return whether discovery is enabled
     */
    boolean enabled();

    /**
     * Returns the requested URI discovery discoveryTypes set up.
     *
     * @return the {@link RequestedUriDiscoveryContext.RequestedUriDiscoveryType}s set up
     */
    List<RequestedUriDiscoveryType> discoveryTypes();

    /**
     * Returns the intermediaries deemed to be trustworthy.
     *
     * @return the {@link io.helidon.common.configurable.AllowList} reflecting those proxies to be trusted
     */
    AllowList trustedProxies();


    /**
     * Builder for {@link RequestedUriDiscoveryContext}.
     */
    @Configured
    final class Builder implements io.helidon.common.Builder<RequestedUriDiscoveryContext.Builder, RequestedUriDiscoveryContext> {

        /**
         * Config key prefix for requested URI discovery settings.
         */
        static final String REQUESTED_URI_DISCOVERY_CONFIG_KEY = "requested-uri-discovery";

        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());
        private Boolean enabled;
        private final List<RequestedUriDiscoveryType> discoveryTypes = new ArrayList<>();
        private AllowList trustedProxies;
        private String socketId;

        private Builder() {
        }

        @Override
        public RequestedUriDiscoveryContext build() {
            prepareAndCheckRequestedUriSettings();
            return new RequestedUriDiscoveryContextImpl(this);
        }

        /**
         * Update the settings from the {@value REQUESTED_URI_DISCOVERY_CONFIG_KEY}
         * {@link io.helidon.common.config.Config} node within the socket configuration.
         *
         * @param requestedUriDiscoveryConfig requested URI discovery configuration node
         * @return updated builder instance
         */
        public Builder config(Config requestedUriDiscoveryConfig) {
            requestedUriDiscoveryConfig.get("enabled")
                    .as(Boolean.class)
                    .ifPresent(this::enabled);
            requestedUriDiscoveryConfig.get("discoveryTypes")
                    .asList(RequestedUriDiscoveryType.class)
                    .ifPresent(this::discoveryTypes);
            requestedUriDiscoveryConfig.get("trusted-proxies")
                    .map(AllowList::create)
                    .ifPresent(this::trustedProxies);
            return this;
        }

        /**
         * Sets whether requested URI discovery is enabled for requestes arriving on the socket.
         *
         * @param value new enabled state
         * @return updated builder
         */
        @ConfiguredOption(value = "true if 'discoveryTypes' or 'trusted-proxies' is set; false otherwise")
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * Sets the trusted proxies for requested URI discovery for requests arriving on the socket.
         *
         * @param trustedProxies the {@link io.helidon.common.configurable.AllowList} represented trusted proxies
         * @return updated builder
         */
        @ConfiguredOption
        public Builder trustedProxies(AllowList trustedProxies) {
            this.trustedProxies = trustedProxies;
            return this;
        }

        /**
         * Sets the discovery types for requested URI discovery for requests arriving on the socket.
         *
         * @param discoveryTypes discovery types to use
         * @return updated builder
         */
        @ConfiguredOption
        public Builder discoveryTypes(List<RequestedUriDiscoveryType> discoveryTypes) {
            this.discoveryTypes.clear();
            this.discoveryTypes.addAll(discoveryTypes);
            return this;
        }

        /**
         * Adds a discovery type for requested URI discovery for requests arriving on the socket.
         *
         * @param discoveryType the {@link RequestedUriDiscoveryContext.RequestedUriDiscoveryType} to add
         * @return updated builder
         */
        public Builder addDiscoveryType(RequestedUriDiscoveryType discoveryType) {
            discoveryTypes.add(discoveryType);
            return this;
        }

        /**
         * Sets the socket identifier to which the discovery context applies.
         *
         * @param socketId socket identifier (used in logging)
         * @return updated builder
         */
        public Builder socketId(String socketId) {
            this.socketId = socketId;
            return this;
        }

        /**
         * Checks validity of requested URI settings and supplies defaults for omitted settings.
         * <p>The behavior of `requested-uri-discovery` config or builder settings can be summarized as follows:
         *     <ul>
         *     <li>The `requested-uri-discovery` settings are optional.</li>
         *     <li>If `requested-uri-discovery` is absent or is present with `enabled` explicitly set to `false`, Helidon
         *     ignores any {@code Forwarded} or {@code X-Forwarded-*} headers and adopts the
         *     {@code HOST} discovery type. That is, Helidon uses the {@code Host} header for the host
         *     and the request's scheme and port.</li>
         *     <li>If `requested-uri-discovery` is present and enabled, either because {@code enabled} is set to {@code true}
         *     or {@code discoveryTypes} or {@code trusted-proxies} has been set, then Helidon performs a simple validity
         *     check before adopting the selected discovery behavior: If {@code discoveryTypes} is specified and includes
         *     either {@code FORWARDED} or {@code X_FORWARDED} then {@code trusted-proxies} must also be set to at least
         *     one value. Put another way, if requested URI discovery is enabled then {@code trusted-proxies} can be unspecified
         *     only if {@code discoveryTypes} contains only {@code HOST}.</li>
         *     </ul>
         * </p>
         */
        private void prepareAndCheckRequestedUriSettings() {
            if (socketId == null) {
                throw new IllegalArgumentException("Required socket ID not specified");
            }
            boolean isDiscoveryEnabledDefaulted = (enabled == null);
            if (enabled == null) {
                enabled = !discoveryTypes.isEmpty() || trustedProxies != null;
            }

            boolean areDiscoveryTypesDefaulted = false;

            if (enabled) {
                // Configure a default type if discovery is enabled and no explicit discoveryTypes are configured.
                if (this.discoveryTypes.isEmpty()) {
                    areDiscoveryTypesDefaulted = true;
                    this.discoveryTypes.add(RequestedUriDiscoveryType.FORWARDED);
                }

                // Require _some_ settings for trusted proxies (except for HOST discovery) so the socket does not start unsafely
                // by accident. The user _can_ set allow.all to run the socket unsafely but at least that way it was
                // an explicit choice.
                if (trustedProxies == null && !isDiscoveryTypesOnlyHost()) {
                    throw new UnsafeRequestedUriSettingsException(this, areDiscoveryTypesDefaulted);
                }
            } else {
                // Discovery is disabled so ignore any explicit settings of discovery type and use HOST discovery.
                if (!discoveryTypes.isEmpty()) {
                    LOGGER.log(System.Logger.Level.INFO, """
                            Ignoring explicit settings of requested-uri-discovery types and trusted-proxies because
                            requested-uri-discovery.enabled {0} to false
                            """, isDiscoveryEnabledDefaulted ? " defaulted" : "was set");
                }
                discoveryTypes.clear();
                discoveryTypes.add(RequestedUriDiscoveryType.HOST);
            }
            if (trustedProxies == null) {
                trustedProxies = AllowList.builder()
                        .addDenied(s -> true)
                        .build();
            }
        }

        private boolean isDiscoveryTypesOnlyHost() {
            return discoveryTypes.size() == 1
                    && discoveryTypes.contains(RequestedUriDiscoveryType.HOST);
        }

        private static class RequestedUriDiscoveryContextImpl implements RequestedUriDiscoveryContext {

            private final boolean enabled;
            private final List<RequestedUriDiscoveryType> discoveryTypes;
            private final AllowList trustedProxies;

            private RequestedUriDiscoveryContextImpl(RequestedUriDiscoveryContext.Builder builder) {
                this.enabled = builder.enabled;
                this.discoveryTypes = builder.discoveryTypes;
                this.trustedProxies = builder.trustedProxies;
            }

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public List<RequestedUriDiscoveryType> discoveryTypes() {
                return discoveryTypes;
            }

            @Override
            public AllowList trustedProxies() {
                return trustedProxies;
            }
        }
    }

    /**
     * Types of discovery of frontend URI. Defaults to {@link #HOST} when frontend URI discovery is disabled (uses only Host
     * header and information about current request to determine scheme, host, port, and path).
     * Defaults to {@link #FORWARDED} when discovery is enabled. Can be explicitly configured on socket configuration builder.
     */
    enum RequestedUriDiscoveryType {
        /**
         * The {@code io.helidon.common.http.Http.Header#FORWARDED} header is used to discover the original requested URI.
         */
        FORWARDED,
        /**
         * The
         * {@code io.helidon.common.http.Http.Header#X_FORWARDED_PROTO},
         * {@code io.helidon.common.http.Http.Header#X_FORWARDED_HOST},
         * {@code io.helidon.common.http.Http.Header#X_FORWARDED_PORT},
         * {@code io.helidon.common.http.Http.Header#X_FORWARDED_PREFIX}
         * headers are used to discover the original requested URI.
         */
        X_FORWARDED,
        /**
         * This is the default, only the {@code io.helidon.common.http.Http.Header#HOST} header is used to discover
         * requested URI.
         */
        HOST
    }

    /**
     * Indicates unsafe settings for a socket's request URI discovery.
     * <p>
     *     This exception typically results when the user has enabled requested URI discovery or selected a discovery type
     *     but has not assigned the trusted proxy {@link AllowList}.
     * </p>
     */
    class UnsafeRequestedUriSettingsException extends RuntimeException {

        /**
         * Creates a new exception instance.
         *
         * @param requestedUriDiscoveryContextBuilder builder for the socket config
         * @param areDiscoveryTypesDefaulted whether discovery discoveryTypes were defaulted (as opposed to set explicitly)
         */
        UnsafeRequestedUriSettingsException(RequestedUriDiscoveryContext.Builder requestedUriDiscoveryContextBuilder,
                                            boolean areDiscoveryTypesDefaulted) {

            super(String.format("""
                Settings which control requested URI discovery for socket %s are unsafe:
                discovery is enabled with types %s to %s but no trusted proxies were set to protect against forgery of headers.
                Server start-up will not continue.
                Please prepare the trusted-proxies allow-list for this socket using 'allow' and/or 'deny' settings.
                If you choose to start unsafely (not recommended), set trusted-proxies.allow.all to 'true'.
                """,
                                requestedUriDiscoveryContextBuilder.socketId,
                                areDiscoveryTypesDefaulted ? "defaulted" : "set",
                                requestedUriDiscoveryContextBuilder.discoveryTypes));
        }
    }
}
