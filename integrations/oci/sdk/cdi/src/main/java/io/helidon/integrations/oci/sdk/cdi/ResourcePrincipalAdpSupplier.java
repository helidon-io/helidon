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

import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;

class ResourcePrincipalAdpSupplier implements AdpSupplier<ResourcePrincipalAuthenticationDetailsProvider> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> builderSupplier;


    /*
     * Constructors.
     */


    ResourcePrincipalAdpSupplier() {
        this(ResourcePrincipalAuthenticationDetailsProvider::builder);
    }

    ResourcePrincipalAdpSupplier(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> builderSupplier) {
        super();
        this.builderSupplier =
            builderSupplier == null ? ResourcePrincipalAuthenticationDetailsProvider::builder : builderSupplier;
    }


    /*
     * Instance methods.
     */


    @Override
    public Optional<ResourcePrincipalAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(available() ? this.builderSupplier.get().build() : null);
    }


    /*
     * Static methods.
     */


    private static boolean available() {
        return System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
    }

}
