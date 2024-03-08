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
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.environmentVariables;
import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.systemProperties;

/**
 * An {@link AdpSupplier} of {@link SimpleAuthenticationDetailsProvider} instances configured via a {@link
 * ConfigAccessor}.
 *
 * @see #get()
 *
 * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
 *
 * @see SimpleAuthenticationDetailsProvider
 *
 * @see SimpleAuthenticationDetailsProviderBuilder
 */
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


    private final Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs;

    private final Function<? super SimpleAuthenticationDetailsProviderBuilder,
                           ? extends SimpleAuthenticationDetailsProvider> f;

    private final ConfigAccessor ca;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link SimpleAdpSupplier}.
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    SimpleAdpSupplier() {
        this(SimpleAuthenticationDetailsProvider::builder);
    }

    /**
     * Creates a new {@link SimpleAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    SimpleAdpSupplier(ConfigAccessor ca) {
        this(ca, SimpleAuthenticationDetailsProvider::builder);
    }

    /**
     * Creates a new {@link SimpleAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * SimpleAuthenticationDetailsProvider#builder()
     * SimpleAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    SimpleAdpSupplier(Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs) {
        this(systemProperties().thenTry(environmentVariables()), bs);
    }

    /**
     * Creates a new {@link SimpleAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * SimpleAuthenticationDetailsProvider#builder()
     * SimpleAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    SimpleAdpSupplier(ConfigAccessor ca,
                      Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs) {
        this(ca, bs, SimpleAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link SimpleAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * SimpleAuthenticationDetailsProvider#builder()
     * SimpleAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @param f a {@link Function} that accepts an {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder} and returns an {@link
     * SimpleAuthenticationDetailsProvider} sourced ultimately from its {@link
     * SimpleAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * SimpleAuthenticationDetailsProviderBuilder#build()
     * SimpleAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    SimpleAdpSupplier(ConfigAccessor ca,
                      Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs,
                      Function<? super SimpleAuthenticationDetailsProviderBuilder,
                               ? extends SimpleAuthenticationDetailsProvider> f) {
        super();
        this.ca = Objects.requireNonNull(ca, "ca");
        this.bs =
            bs == null
            ? () -> configure(ca, SimpleAuthenticationDetailsProvider.builder())
            : () -> configure(ca, bs.get());
        this.f = f == null ? SimpleAuthenticationDetailsProviderBuilder::build : f;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} a {@link SimpleAuthenticationDetailsProvider}
     * instance.
     *
     * <p>An {@linkplain Optional#isEmpty() empty <code>Optional</code>} return value indicates only that at the moment
     * of invocation minimal requirements were not met. It implies no further semantics of any kind.</p>
     *
     * <p>This method will return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} if at the moment of
     * invocation an invocation of the {@link #available()} method returns {@code false}.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link SimpleAuthenticationDetailsProvider}
     * instance; never {@code null}
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     *
     * @see #available()
     *
     * @see #configure(ConfigAccessor, SimpleAuthenticationDetailsProviderBuilder)
     *
     * @see SimpleAuthenticationDetailsProvider
     */
    @Override
    public Optional<SimpleAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(this.available() ? this.f.apply(this.bs.get()) : null);
    }

    /**
     * Invokes the {@link #available(ConfigAccessor)} method with the {@link ConfigAccessor} {@linkplain
     * #SimpleAdpSupplier(ConfigAccessor, Supplier, Function) supplied at construction time} and returns its result.
     *
     * <p>This method is called by the {@link #get()} method's implementation.</p>
     *
     * @return the result of invoking the {@link #available(ConfigAccessor)} method with the {@link ConfigAccessor}
     * {@linkplain #SimpleAdpSupplier(ConfigAccessor, Supplier, Function) supplied at construction time}
     *
     * @see #available(ConfigAccessor)
     *
     * @see #SimpleAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    // Throws IllegalArgumentException if the region code or ID is bad
    public final boolean available() {
        return available(this.ca);
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if the supplied {@link ConfigAccessor} returns {@linkplain Optional#isPresent() present
     * <code>Optional</code>s} for certain configuration names, and {@code false} if it does not.
     *
     * <p>The supplied {@link ConfigAccessor} will be {@linkplain ConfigAccessor#get(String) queried for} values
     * corresponding to the configuration names:</p>
     *
     * <ul>
     *
     * <li>{@value OCI_AUTH_FINGERPRINT}</li>
     *
     * <li>{@value OCI_AUTH_REGION}</li>
     *
     * <li>{@value OCI_AUTH_TENANT_ID}</li>
     *
     * <li>{@value OCI_AUTH_USER_ID}</li>
     *
     * </ul>
     *
     * <p>If the {@link ConfigAccessor#get(String)} returns an {@linkplain Optional#isEmpty() empty
     * <code>Optional</code>} for any of these configuration names, {@code false} is returned.</p>
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @return {@code true} if the supplied {@link ConfigAccessor} returns {@linkplain Optional#isPresent() present
     * <code>Optional</code>s} for certain configuration names, {@code false} otherwise
     *
     * @exception NullPointerException if the supplied {@link ConfigAccessor} is {@code null}
     *
     * @see ConfigAccessor#get(String)
     */
    public static final boolean available(ConfigAccessor ca) {
        return ca.get(OCI_AUTH_FINGERPRINT).isPresent()
            && ca.get(OCI_AUTH_REGION).isPresent() // NOTE: it may be formatted badly, but that's for checking later
            && ca.get(OCI_AUTH_TENANT_ID).isPresent()
            && ca.get(OCI_AUTH_USER_ID).isPresent(); // NOTE: not clear that this is truly required?
    }

    /**
     * Calls certain mutator methods on the supplied {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder} if and only if certain {@linkplain ConfigAccessor#get(String)
     * configuration values are present} and returns the mutated {@link SimpleAuthenticationDetailsProviderBuilder
     * SimpleAuthenticationDetailsProviderBuilder}.
     *
     * <p>This method may be called by the {@link #get()} method's implementation.</p>
     *
     * <p>The supplied {@link ConfigAccessor} will be {@linkplain ConfigAccessor#get(String) queried for} values
     * corresponding to the configuration names:</p>
     *
     * <ul>
     *
     * <li>{@value OCI_AUTH_FINGERPRINT} ({@link SimpleAuthenticationDetailsProviderBuilder#fingerprint(String)} will be
     * called)</li>
     *
     * <li>{@value OCI_AUTH_PASSPHRASE} ({@link SimpleAuthenticationDetailsProviderBuilder#passPhrase(String)} will be
     * called)</li>
     *
     * <li>{@value OCI_AUTH_PRIVATE_KEY} ({@link
     * SimpleAuthenticationDetailsProviderBuilder#privateKeySupplier(Supplier)} will be called)</li>
     *
     * <li>{@value OCI_AUTH_REGION} ({@link SimpleAuthenticationDetailsProviderBuilder#region(Region)} will be
     * called)</li>
     *
     * <li>{@value OCI_AUTH_TENANT_ID} ({@link SimpleAuthenticationDetailsProviderBuilder#tenantId(String)} will be
     * called)</li>
     *
     * <li>{@value OCI_AUTH_USER_ID} ({@link SimpleAuthenticationDetailsProviderBuilder#userId(String)} will be
     * called)</li>
     *
     * </ul>
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param b a {@link SimpleAuthenticationDetailsProviderBuilder} to be (possibly) configured; must not be {@code
     * null}
     *
     * @return the supplied {@link SimpleAuthenticationDetailsProviderBuilder}
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#fingerprint(String)
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#passPhrase(String)
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#privateKeySupplier(Supplier)
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#region(Region)
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#tenantId(String)
     *
     * @see SimpleAuthenticationDetailsProviderBuilder#userId(String)
     */
    // Throws IllegalArgumentException if the region code or ID is bad
    public static final SimpleAuthenticationDetailsProviderBuilder configure(ConfigAccessor ca,
                                                                             SimpleAuthenticationDetailsProviderBuilder b) {
        ca.get(OCI_AUTH_FINGERPRINT).ifPresent(b::fingerprint);
        ca.get(OCI_AUTH_PASSPHRASE).or(() -> ca.get(OCI_AUTH_PASSPHRASE + "Characters")).ifPresent(b::passPhrase);
        privateKeySupplier(ca).ifPresent(b::privateKeySupplier);
        ca.get(OCI_AUTH_REGION).map(Region::valueOf).ifPresent(b::region);
        ca.get(OCI_AUTH_TENANT_ID).ifPresent(b::tenantId);
        ca.get(OCI_AUTH_USER_ID).ifPresent(b::userId);
        return b;
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
