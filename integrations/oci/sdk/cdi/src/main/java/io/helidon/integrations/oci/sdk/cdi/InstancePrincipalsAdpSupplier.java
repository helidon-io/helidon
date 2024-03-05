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
package io.helidon.integrations.oci.sdk.cdi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

import static com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL;
import static java.lang.System.Logger.Level.DEBUG;

class InstancePrincipalsAdpSupplier implements AdpSupplier<InstancePrincipalsAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final String DEFAULT_IMDS_HOSTNAME = URI.create(METADATA_SERVICE_BASE_URL).getHost();

    private static final Logger LOGGER = System.getLogger(ConfigFileAdpSupplier.class.getName());


    /*
     * Instance fields.
     */


    private final Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> builderSupplier;

    private final ConfigAccessor ca;


    /*
     * Constructors.
     */


    InstancePrincipalsAdpSupplier(ConfigAccessor ca) {
        this(InstancePrincipalsAuthenticationDetailsProvider::builder, ca);
    }

    InstancePrincipalsAdpSupplier(Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> builderSupplier,
                                  ConfigAccessor ca) {
        super();
        this.builderSupplier =
            builderSupplier == null ? InstancePrincipalsAuthenticationDetailsProvider::builder : builderSupplier;
        this.ca = Objects.requireNonNull(ca, "ca");
    }


    /*
     * Instance methods.
     */


    @Override
    public Optional<InstancePrincipalsAuthenticationDetailsProvider> get() {
        InetAddress imds;
        try {
            imds = InetAddress.getByName(this.ca.get("oci.imds.hostname").orElse(DEFAULT_IMDS_HOSTNAME));
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        int ociImdsTimeoutMillis;
        try {
            ociImdsTimeoutMillis =
                Math.max(0, Integer.parseInt(this.ca.get("oci.imds.timeout.milliseconds").orElse("100")));
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, e.getMessage(), e);
            }
            ociImdsTimeoutMillis = 100;
        }
        try {
            if (!imds.isReachable(ociImdsTimeoutMillis)) {
                return Optional.empty();
            }
        } catch (ConnectException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, e.getMessage(), e);
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        return Optional.of(this.builderSupplier.get().build());
    }

}
