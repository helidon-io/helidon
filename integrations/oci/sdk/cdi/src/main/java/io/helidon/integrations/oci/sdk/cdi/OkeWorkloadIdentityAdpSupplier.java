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
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider.OkeWorkloadIdentityAuthenticationDetailsProviderBuilder;

import static java.nio.file.Files.readAttributes;

/**
 * An {@link AdpSupplier} of {@link OkeWorkloadIdentityAuthenticationDetailsProvider} instances.
 *
 * @see #get()
 *
 * @see #OkeWorkloadIdentityAdpSupplier(Supplier, Function)
 *
 * @see OkeWorkloadIdentityAuthenticationDetailsProvider
 */
class OkeWorkloadIdentityAdpSupplier implements AdpSupplier<OkeWorkloadIdentityAuthenticationDetailsProvider> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> bs;

    private final Function<? super OkeWorkloadIdentityAuthenticationDetailsProviderBuilder,
                           ? extends OkeWorkloadIdentityAuthenticationDetailsProvider> f;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OkeWorkloadIdentityAdpSupplier}.
     *
     * @see #OkeWorkloadIdentityAdpSupplier(Supplier, Function)
     */
    OkeWorkloadIdentityAdpSupplier() {
        this(OkeWorkloadIdentityAuthenticationDetailsProvider::builder,
             OkeWorkloadIdentityAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link OkeWorkloadIdentityAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link OkeWorkloadIdentityAuthenticationDetailsProviderBuilder
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider#builder()
     * OkeWorkloadIdentityAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #OkeWorkloadIdentityAdpSupplier(Supplier, Function)
     */
    OkeWorkloadIdentityAdpSupplier(Supplier<? extends OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> bs) {
        this(bs, OkeWorkloadIdentityAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link OkeWorkloadIdentityAdpSupplier}.
     *
     * @param f a {@link Function} that accepts an {@link OkeWorkloadIdentityAuthenticationDetailsProviderBuilder
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder} and returns an {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider} sourced ultimately from its {@link
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder#build()
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #OkeWorkloadIdentityAdpSupplier(Supplier, Function)
     */
    OkeWorkloadIdentityAdpSupplier(Function<? super OkeWorkloadIdentityAuthenticationDetailsProviderBuilder,
                                            ? extends OkeWorkloadIdentityAuthenticationDetailsProvider> f) {
        this(OkeWorkloadIdentityAuthenticationDetailsProvider::builder, f);
    }

    /**
     * Creates a new {@link OkeWorkloadIdentityAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link OkeWorkloadIdentityAuthenticationDetailsProviderBuilder
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider#builder()
     * OkeWorkloadIdentityAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @param f a {@link Function} that accepts an {@link OkeWorkloadIdentityAuthenticationDetailsProviderBuilder
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder} and returns an {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider} sourced ultimately from its {@link
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder#build()
     * OkeWorkloadIdentityAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    OkeWorkloadIdentityAdpSupplier(Supplier<? extends OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> bs,
                                   Function<? super OkeWorkloadIdentityAuthenticationDetailsProviderBuilder,
                                            ? extends OkeWorkloadIdentityAuthenticationDetailsProvider> f) {
        super();
        this.bs = bs == null ? OkeWorkloadIdentityAuthenticationDetailsProvider::builder : bs;
        this.f = f == null ? OkeWorkloadIdentityAuthenticationDetailsProviderBuilder::build : f;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} an {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider} instance.
     *
     * <p>An {@linkplain Optional#isEmpty() empty <code>Optional</code>} return value indicates only that at the moment
     * of invocation minimal requirements were not met. It implies no further semantics of any kind.</p>
     *
     * <p>This method will return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} if at the moment of
     * invocation an invocation of the {@link #available()} method returns {@code false}.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} an {@link
     * OkeWorkloadIdentityAuthenticationDetailsProvider} instance; never {@code null}
     *
     * @see #OkeWorkloadIdentityAdpSupplier(Supplier, Function)
     *
     * @see #available()
     *
     * @see OkeWorkloadIdentityAuthenticationDetailsProvider
     */
    @Override
    public final Optional<OkeWorkloadIdentityAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(available() ? this.f.apply(this.bs.get()) : null);
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if and only if the file identified by the value of the {@code
     * OCI_KUBERNETES_SERVICE_ACCOUNT_CERT_PATH} environment variable, or the default value "{@code
     * /var/run/secrets/kubernetes.io/serviceaccount/ca.crt}", {@linkplain BasicFileAttributes#isRegularFile() is a
     * regular file}.
     *
     * <p>This method is called by the {@link #get()} method.</p>
     *
     * @return {@code true} if and only if the file identified by the value of the {@code
     * OCI_KUBERNETES_SERVICE_ACCOUNT_CERT_PATH} environment variable, or the default value "{@code
     * /var/run/secrets/kubernetes.io/serviceaccount/ca.crt}", {@linkplain BasicFileAttributes#isRegularFile() is a
     * regular file}; {@code false} in all other cases
     *
     * @exception UncheckedIOException if there was an error checking attributes of the (extant) file
     */
    public static boolean available() {
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
