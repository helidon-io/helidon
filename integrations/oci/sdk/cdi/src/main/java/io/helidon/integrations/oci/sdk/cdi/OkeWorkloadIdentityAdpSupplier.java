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
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider.OkeWorkloadIdentityAuthenticationDetailsProviderBuilder;

import static java.nio.file.Files.readAttributes;

class OkeWorkloadIdentityAdpSupplier implements AdpSupplier<OkeWorkloadIdentityAuthenticationDetailsProvider> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> builderSupplier;


    /*
     * Constructors.
     */


    OkeWorkloadIdentityAdpSupplier() {
        this(OkeWorkloadIdentityAuthenticationDetailsProvider::builder);
    }

    OkeWorkloadIdentityAdpSupplier(Supplier<? extends OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> builderSupplier) {
        super();
        this.builderSupplier =
            builderSupplier == null ? OkeWorkloadIdentityAuthenticationDetailsProvider::builder : builderSupplier;
    }


    /*
     * Instance methods.
     */


    @Override
    public Optional<OkeWorkloadIdentityAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(available() ? this.builderSupplier.get().build() : null);
    }


    /*
     * Static methods.
     */


    private static boolean available() {
        try {
            return
                readAttributes(Path.of(Optional.ofNullable(System.getenv("OCI_KUBERNETES_SERVICE_ACCOUNT_CERT_PATH"))
                                       .orElse("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")),
                               BasicFileAttributes.class)
                .isRegularFile(); // (symbolic links are followed by default)
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

}
