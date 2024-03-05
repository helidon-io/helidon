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

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

class SimpleAdpSupplier implements AdpSupplier<SimpleAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final String OCI_AUTH_FINGERPRINT = "oci.auth.fingerprint";

    private static final String OCI_AUTH_PASSPHRASE = "oci.auth.passphrase";

    private static final String OCI_AUTH_PRIVATE_KEY = "oci.auth.private-key";

    private static final String OCI_AUTH_REGION = "oci.auth.region";

    private static final String OCI_AUTH_TENANT_ID = "oci.auth.tenant-id";

    private static final String OCI_AUTH_USER_ID = "oci.auth.user-id";


    /*
     * Instance fields.
     */


    private final Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> builderSupplier;

    private final ConfigAccessor ca;


    /*
     * Constructors.
     */


    SimpleAdpSupplier(ConfigAccessor ca) {
        this(SimpleAuthenticationDetailsProvider::builder, ca);
    }

    SimpleAdpSupplier(Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> builderSupplier,
                      ConfigAccessor ca) {
        super();
        this.builderSupplier = builderSupplier == null ? SimpleAuthenticationDetailsProvider::builder : builderSupplier;
        this.ca = Objects.requireNonNull(ca, "ca");
    }


    /*
     * Instance methods.
     */


    // Throws IllegalArgumentException if the region code or ID is bad
    @Override
    public Optional<SimpleAuthenticationDetailsProvider> get() {
        // Here we check for the presence of required configuration values, but do not apply them.(that's the job of the
        // builder supplier).
        return this.available() ? Optional.of(this.builderSupplier.get().build()) : Optional.empty();
    }

    // Throws IllegalArgumentException if the region code or ID is bad
    public boolean available() {
        return available(this.ca);
    }


    /*
     * Static methods.
     */


    public static final boolean available(ConfigAccessor ca) {
        return ca.get(OCI_AUTH_FINGERPRINT).isPresent()
            && ca.get(OCI_AUTH_REGION).map(Region::valueOf).isPresent() // NOTE: weirdly, config file lets this be null
            && ca.get(OCI_AUTH_TENANT_ID).isPresent()
            && ca.get(OCI_AUTH_USER_ID).isPresent(); // NOTE: not clear
    }

    // Convenience; not used internally
    // Throws IllegalArgumentException if the region code or ID is bad
    public static final void configure(SimpleAuthenticationDetailsProviderBuilder b, ConfigAccessor ca) {
        ca.get(OCI_AUTH_FINGERPRINT).ifPresent(b::fingerprint);
        ca.get(OCI_AUTH_PASSPHRASE).or(() -> ca.get(OCI_AUTH_PASSPHRASE + "Characters")).ifPresent(b::passPhrase);
        ca.get(OCI_AUTH_TENANT_ID).ifPresent(b::tenantId);
        ca.get(OCI_AUTH_REGION).map(Region::valueOf).ifPresent(b::region);
        ca.get(OCI_AUTH_USER_ID).ifPresent(b::userId);
        privateKeySupplier(ca).ifPresent(b::privateKeySupplier);
    }

    private static Optional<Supplier<InputStream>> privateKeySupplier(ConfigAccessor ca) {
        return ca.get(OCI_AUTH_PRIVATE_KEY)
            .or(() -> ca.get("oci.auth.privateKey"))
            .<Supplier<InputStream>>map(StringPrivateKeySupplier::new)
            .or(() -> ca.get(OCI_AUTH_PRIVATE_KEY + "-path")
                .or(() -> ca.get("oci.auth.keyFile")
                    .or(() -> Optional.of(Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem").toString())))
                .map(SimplePrivateKeySupplier::new));
    }

}
