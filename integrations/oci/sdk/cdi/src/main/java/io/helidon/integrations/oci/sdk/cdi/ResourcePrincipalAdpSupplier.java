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
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;

/**
 * An {@link AdpSupplier} of {@link ResourcePrincipalAuthenticationDetailsProvider} instances.
 *
 * @see #get()
 *
 * @see #ResourcePrincipalAdpSupplier(Supplier, Function)
 *
 * @see ResourcePrincipalAuthenticationDetailsProvider
 *
 * @see ResourcePrincipalAuthenticationDetailsProviderBuilder
 */
class ResourcePrincipalAdpSupplier implements AdpSupplier<ResourcePrincipalAuthenticationDetailsProvider> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs;

    private final Function<? super ResourcePrincipalAuthenticationDetailsProviderBuilder,
                           ? extends ResourcePrincipalAuthenticationDetailsProvider> f;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ResourcePrincipalAdpSupplier}.
     *
     * @see #ResourcePrincipalAdpSupplier(Supplier, Function)
     */
    ResourcePrincipalAdpSupplier() {
        this(ResourcePrincipalAuthenticationDetailsProvider::builder,
             ResourcePrincipalAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link ResourcePrincipalAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link ResourcePrincipalAuthenticationDetailsProviderBuilder
     * ResourcePrincipalAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * ResourcePrincipalAuthenticationDetailsProvider#builder() ResourcePrincipalAuthenticationDetailsProvider::builder}
     * is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #ResourcePrincipalAdpSupplier(Supplier, Function)
     */
    ResourcePrincipalAdpSupplier(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs) {
        this(bs, ResourcePrincipalAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link ResourcePrincipalAdpSupplier}.
     *
     * @param f a {@link Function} that accepts an {@link ResourcePrincipalAuthenticationDetailsProviderBuilder
     * ResourcePrincipalAuthenticationDetailsProviderBuilder} and returns an {@link
     * ResourcePrincipalAuthenticationDetailsProvider} sourced ultimately from its {@link
     * ResourcePrincipalAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * ResourcePrincipalAuthenticationDetailsProviderBuilder#build()
     * ResourcePrincipalAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #ResourcePrincipalAdpSupplier(Supplier, Function)
     */
    ResourcePrincipalAdpSupplier(Function<? super ResourcePrincipalAuthenticationDetailsProviderBuilder,
                                          ? extends ResourcePrincipalAuthenticationDetailsProvider> f) {
        this(ResourcePrincipalAuthenticationDetailsProvider::builder, f);
    }

    /**
     * Creates a new {@link ResourcePrincipalAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link ResourcePrincipalAuthenticationDetailsProviderBuilder
     * ResourcePrincipalAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * ResourcePrincipalAuthenticationDetailsProvider#builder() ResourcePrincipalAuthenticationDetailsProvider::builder}
     * is a commonly-supplied value
     *
     * @param f a {@link Function} that accepts an {@link ResourcePrincipalAuthenticationDetailsProviderBuilder
     * ResourcePrincipalAuthenticationDetailsProviderBuilder} and returns an {@link
     * ResourcePrincipalAuthenticationDetailsProvider} sourced ultimately from its {@link
     * ResourcePrincipalAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * ResourcePrincipalAuthenticationDetailsProviderBuilder#build()
     * ResourcePrincipalAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    // This is The Way.
    ResourcePrincipalAdpSupplier(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs,
                                 Function<? super ResourcePrincipalAuthenticationDetailsProviderBuilder,
                                          ? extends ResourcePrincipalAuthenticationDetailsProvider> f) {
        super();
        this.bs = bs == null ? ResourcePrincipalAuthenticationDetailsProvider::builder : bs;
        this.f = f == null ? ResourcePrincipalAuthenticationDetailsProviderBuilder::build : f;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} a {@link
     * ResourcePrincipalAuthenticationDetailsProvider} instance.
     *
     * <p>An {@linkplain Optional#isEmpty() empty <code>Optional</code>} return value indicates only that at the moment
     * of invocation minimal requirements were not met. It implies no further semantics of any kind.</p>
     *
     * <p>This method will return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} if at the moment of
     * invocation an invocation of the {@link #available()} method returns {@code false}.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link
     * ResourcePrincipalAuthenticationDetailsProvider} instance; never {@code null}
     *
     * @see #ResourcePrincipalAdpSupplier(Supplier, Function)
     *
     * @see #available()
     *
     * @see ResourcePrincipalAuthenticationDetailsProvider
     */
    @Override
    public final Optional<ResourcePrincipalAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(available() ? this.f.apply(this.bs.get()) : null);
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if a non-{@code null} value {@linkplain System#getenv(String) exists for the environment
     * variable} named "{@link ResourcePrincipalAuthenticationDetailsProvider OCI_RESOURCE_PRINCIPAL_VERSION}" and
     * {@code false} if it does not.
     *
     * @return {@code true} if a non-{@code null} value {@linkplain System#getenv(String) exists for the environment
     * variable} named "{@link ResourcePrincipalAuthenticationDetailsProvider OCI_RESOURCE_PRINCIPAL_VERSION}" and
     * {@code false} if it does not
     *
     * @see ResourcePrincipalAuthenticationDetailsProvider
     */
    public static boolean available() {
        return System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
    }

}
