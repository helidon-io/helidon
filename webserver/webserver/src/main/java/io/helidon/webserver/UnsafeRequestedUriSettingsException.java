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
package io.helidon.webserver;

/**
 * Indicates unsafe settings for a socket's request URI discovery.
 * <p>
 *     This exception typically results when the user has enabled requested URI discovery or selected a discovery type
 *     but has not assigned the trusted proxy {@link io.helidon.common.configurable.AllowList}.
 * </p>
 */
public class UnsafeRequestedUriSettingsException extends RuntimeException {

    /**
     * Creates a new exception instance.
     *
     * @param socketConfigurationBuilder builder for the socket config
     * @param areDiscoveryTypesDefaulted whether discovery types were defaulted (as opposed to set explicitly)
     */
    public UnsafeRequestedUriSettingsException(SocketConfiguration.Builder socketConfigurationBuilder,
                                               boolean areDiscoveryTypesDefaulted) {

        super(String.format("""
            Settings which control requested URI discovery for socket %s are unsafe:
            discovery is enabled with types %s to %s but no trusted proxies were set to protect against forgery of headers.
            Server start-up will not continue.
            Please prepare the trusted-proxies allow-list for this socket using 'allow' and/or 'deny' settings.
            If you choose to start unsafely (not recommended), set trusted-proxies.allow.all to 'true'.
            """,
                            socketConfigurationBuilder.name(),
                            areDiscoveryTypesDefaulted ? "defaulted" : "set",
                            socketConfigurationBuilder.requestedUriDiscoveryTypes()));
    }
}
