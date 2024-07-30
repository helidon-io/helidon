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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

import static com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL;
import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.environmentVariables;
import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.systemProperties;
import static java.lang.System.Logger.Level.DEBUG;

/**
 * An {@link AdpSupplier} of {@link InstancePrincipalsAuthenticationDetailsProvider} instances.
 *
 * @see #get()
 *
 * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
 *
 * @see InstancePrincipalsAuthenticationDetailsProvider
 *
 * @see InstancePrincipalsAuthenticationDetailsProviderBuilder
 */
class InstancePrincipalsAdpSupplier implements AdpSupplier<InstancePrincipalsAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final String DEFAULT_IMDS_HOSTNAME = URI.create(METADATA_SERVICE_BASE_URL).getHost();

    private static final Logger LOGGER = System.getLogger(ConfigFileAdpSupplier.class.getName());


    /*
     * Instance fields.
     */


    private final Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs;

    private final Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder,
                           ? extends InstancePrincipalsAuthenticationDetailsProvider> f;

    private final ConfigAccessor ca;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier() {
        this(systemProperties().thenTry(environmentVariables()));
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(ConfigAccessor ca) {
        this(ca, InstancePrincipalsAuthenticationDetailsProvider::builder);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProvider#builder()
     * InstancePrincipalsAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @exception NullPointerException if {@code bs} is {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        this(systemProperties().thenTry(environmentVariables()),
             bs,
             InstancePrincipalsAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} and returns an {@link
     * InstancePrincipalsAuthenticationDetailsProvider} sourced ultimately from its {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build()
     * InstancePrincipalsAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if {@code f} is {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder,
                                           ? extends InstancePrincipalsAuthenticationDetailsProvider> f) {
        this(systemProperties().thenTry(environmentVariables()),
             InstancePrincipalsAuthenticationDetailsProvider::builder,
             f);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProvider#builder()
     * InstancePrincipalsAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} and returns an {@link
     * InstancePrincipalsAuthenticationDetailsProvider} sourced ultimately from its {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build()
     * InstancePrincipalsAuthenticationDetailsProvider::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                  Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder,
                                           ? extends InstancePrincipalsAuthenticationDetailsProvider> f) {
        this(systemProperties().thenTry(environmentVariables()), bs, f);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProvider#builder()
     * InstancePrincipalsAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(ConfigAccessor ca,
                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        this(ca, bs, InstancePrincipalsAuthenticationDetailsProviderBuilder::build);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} and returns an {@link
     * InstancePrincipalsAuthenticationDetailsProvider} sourced ultimately from its {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build()
     * InstancePrincipalsAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    InstancePrincipalsAdpSupplier(ConfigAccessor ca,
                                  Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder,
                                           ? extends InstancePrincipalsAuthenticationDetailsProvider> f) {
        this(ca, InstancePrincipalsAuthenticationDetailsProvider::builder, f);
    }

    /**
     * Creates a new {@link InstancePrincipalsAdpSupplier}.
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @param bs a {@link Supplier} of {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} instances; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProvider#builder()
     * InstancePrincipalsAuthenticationDetailsProvider::builder} is a commonly-supplied value
     *
     * @param f a {@link Function} that accepts an {@link InstancePrincipalsAuthenticationDetailsProviderBuilder
     * InstancePrincipalsAuthenticationDetailsProviderBuilder} and returns an {@link
     * InstancePrincipalsAuthenticationDetailsProvider} sourced ultimately from its {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build() build()} method; must not be {@code null}; {@link
     * InstancePrincipalsAuthenticationDetailsProviderBuilder#build()
     * InstancePrincipalsAuthenticationDetailsProviderBuilder::build} is a commonly-supplied value
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    InstancePrincipalsAdpSupplier(ConfigAccessor ca,
                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                  Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder,
                                           ? extends InstancePrincipalsAuthenticationDetailsProvider> f) {
        super();
        this.ca = ca == null ? systemProperties().thenTry(environmentVariables()) : ca;
        this.bs = bs == null ? InstancePrincipalsAuthenticationDetailsProvider::builder : bs;
        this.f = f == null ? InstancePrincipalsAuthenticationDetailsProviderBuilder::build : f;
    }


    /*
     * Instance methods.
     */


    /**
     * Returns an {@link Optional} {@linkplain Optional#get() housing} a {@link
     * InstancePrincipalsAuthenticationDetailsProvider} instance.
     *
     * <p>An {@linkplain Optional#isEmpty() empty <code>Optional</code>} return value indicates only that at the moment
     * of invocation minimal requirements were not met. It implies no further semantics of any kind.</p>
     *
     * <p>This method will return an {@linkplain Optional#isEmpty() empty <code>Optional</code>} if at the moment of
     * invocation an invocation of the {@link #available()} method returns {@code false}.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link
     * InstancePrincipalsAuthenticationDetailsProvider} instance; never {@code null}
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     *
     * @see #available()
     *
     * @see InstancePrincipalsAuthenticationDetailsProvider
     */
    @Override
    public final Optional<InstancePrincipalsAuthenticationDetailsProvider> get() {
        return Optional.ofNullable(this.available() ? this.f.apply(bs.get()) : null);
    }

    /**
     * Invokes the {@link #available(ConfigAccessor)} method with the {@link ConfigAccessor} {@linkplain
     * #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function) supplied at construction time} and returns its
     * result.
     *
     * <p>This method is called by the {@link #get()} method's implementation.</p>
     *
     * @return the result of invoking the {@link #available(ConfigAccessor)} method with the {@link ConfigAccessor}
     * {@linkplain #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function) supplied at construction time}
     *
     * @see #available(ConfigAccessor)
     *
     * @see #InstancePrincipalsAdpSupplier(ConfigAccessor, Supplier, Function)
     */
    public final boolean available() {
        return available(this.ca);
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS service</a> is reachable
     * and {@code false} if it is not.
     *
     * <p><em>Reachable</em> here means only that a cursory effort has been made to verify that the IMDS host
     * {@linkplain InetAddress#isReachable(int) is reachable} via {@link InetAddress#isReachable(int)} or a similar
     * mechanism.</p>
     *
     * <p>The <a href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS service</a> is
     * normally not reachable from anything other than an <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/instances.htm">OCI compute instance</a>.</p>
     *
     * <p>This mechanism for checking availability exists only to rule out, as quickly as reasonably possible, the very
     * common case where an application is <em>not</em> running on an <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/instances.htm">OCI compute instance</a>. There are
     * many possible situations where this method may return {@code true} but an attempt to acquire an {@link
     * InstancePrincipalsAuthenticationDetailsProvider} will fail anyway.</p>
     *
     * <p>The supplied {@link ConfigAccessor} will be {@linkplain ConfigAccessor#get(String) queried for} a value
     * corresponding to the configuration name "{@code oci.imds.hostname}". If such a value is found, it is treated as a
     * hostname suitable for passing to {@link InetAddress#getByName(String)}. If such a value is not found (very common
     * and preferred), then the default IMDS address ({@code 169.254.169.254}) will be used instead.</p>
     *
     * <p>The supplied {@link ConfigAccessor} will be {@linkplain ConfigAccessor#get(String) queried for} a value
     * corresponding to the configuration name "{@code oci.imds.timeout.milliseconds}". If such a value is found, and
     * can be parsed by {@link Integer#parseInt(String)}, then it will be taken to be the number of milliseconds to
     * supply to a call similar to (or exactly) {@link InetAddress#isReachable(int)}. If such a value is not found (very
     * common and preferred), then the default value, {@code 100}, will be used instead.</p>
     *
     * @param ca a {@link ConfigAccessor}; must not be {@code null}
     *
     * @return {@code true} if the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm">IMDS service</a> is reachable
     * and {@code false} if it is not
     *
     * @exception NullPointerException if {@code ca} is {@code null}
     *
     * @exception UncheckedIOException if a network error occurs that was not caused by a {@link ConnectException}
     *
     * @see ConfigAccessor#get(String)
     */
    public static boolean available(ConfigAccessor ca) {
        InetAddress imds;
        try {
            imds = InetAddress.getByName(ca.get("oci.imds.hostname").orElse(DEFAULT_IMDS_HOSTNAME));
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        int ociImdsTimeoutMillis;
        try {
            ociImdsTimeoutMillis =
                Math.max(0, Integer.parseInt(ca.get("oci.imds.timeout.milliseconds").orElse("100")));
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, e.getMessage(), e);
            }
            ociImdsTimeoutMillis = 100;
        }
        try {
            return imds.isReachable(ociImdsTimeoutMillis);
        } catch (ConnectException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        return false;
    }

}
