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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder;

class SessionTokenAdpSupplier implements AdpSupplier<SessionTokenAuthenticationDetailsProvider> {


    /*
     * Instance fields.
     */


    private final Supplier<? extends Optional<SessionTokenAuthenticationDetailsProvider>> s;


    /*
     * Constructors.
     */


    private SessionTokenAdpSupplier(Supplier<? extends Optional<SessionTokenAuthenticationDetailsProvider>> s) {
        this.s = Objects.requireNonNull(s, "s");
    }


    /*
     * Instance methods.
     */


    @Override
    public final Optional<SessionTokenAuthenticationDetailsProvider> get() {
        return this.s.get();
    }


    /*
     * Static methods.
     */


    /**
     * Returns a {@link SessionTokenAdpSupplier} backed by the supplied {@link SessionTokenAuthenticationDetailsProviderBuilder}.
     *
     * @param b a {@link SessionTokenAuthenticationDetailsProviderBuilder}; must not be {@code null}
     *
     * @return a {@link SessionTokenAdpSupplier}; never {@code null}
     *
     * @exception NullPointerException if {@code b} is {@code null}
     *
     * @see #ofSupplier(Supplier)
     */
    public static SessionTokenAdpSupplier ofBuilder(SessionTokenAuthenticationDetailsProviderBuilder b) {
        Objects.requireNonNull(b, "b");
        return ofSupplier(() -> produce(b));
    }

    /**
     * Returns a {@link SessionTokenAdpSupplier} backed by the supplied {@link Supplier} of {@link
     * SessionTokenAuthenticationDetailsProvider} instances.
     *
     * @param s a {@link Supplier} of {@link SessionTokenAuthenticationDetailsProvider} instances; must not be {@code null}
     *
     * @return a {@link SessionTokenAdpSupplier}; never {@code null}
     *
     * @exception NullPointerException if {@code s} is {@code null}
     */
    public static SessionTokenAdpSupplier ofSupplier(Supplier<? extends SessionTokenAuthenticationDetailsProvider> s) {
        return new SessionTokenAdpSupplier(fromSupplier(s));
    }

    /**
     * Returns a {@link SessionTokenAdpSupplier} backed by the supplied {@link Supplier} of {@link ConfigFile}
     * instances.
     *
     * @param cfs a {@link Supplier} of {@link ConfigFile} instances; must not be {@code null}
     *
     * @return a {@link SessionTokenAdpSupplier}; never {@code null}
     *
     * @exception NullPointerException if {@code cfs} is {@code null}
     */
    public static SessionTokenAdpSupplier ofConfigFileSupplier(Supplier<? extends ConfigFile> cfs) {
        return new SessionTokenAdpSupplier(fromConfigFileSupplier(cfs));
    }

    /**
     * Returns {@code true} if and only if the supplied {@link ConfigFile} contains enough information to {@linkplain
     * SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile) create a
     * <code>SessionTokenAuthenticationDetailsProvider</code>}.
     *
     * @param cf a {@link ConfigFile}; must not be {@code null}
     *
     * @return {@code true} if and only if the supplied {@link ConfigFile} contains enough information to {@linkplain
     * SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile) create a
     * <code>SessionTokenAuthenticationDetailsProvider</code>}
     *
     * @exception NullPointerException if {@code cf} is {@code null}
     *
     * @see ConfigFiles#containsRequiredValues(ConfigFile)
     */
    public static boolean containsRequiredValues(ConfigFile cf) {
        // Rule out ConfigFileAuthenticationDetailsProvider usage up front.
        return cf.get("security_token_file") != null && ConfigFiles.containsRequiredValues(cf);
    }

    private static Supplier<Optional<SessionTokenAuthenticationDetailsProvider>>
        fromConfigFileSupplier(Supplier<? extends ConfigFile> cfs) {
        Objects.requireNonNull(cfs, "cfs");
        return () -> ConfigFiles.configFile(cfs)
            .filter(SessionTokenAdpSupplier::containsRequiredValues)
            .map(SessionTokenAdpSupplier::produce);
    }

    private static Supplier<Optional<SessionTokenAuthenticationDetailsProvider>>
        fromSupplier(Supplier<? extends SessionTokenAuthenticationDetailsProvider> s) {
        Objects.requireNonNull(s, "s");
        return () -> {
            try {
                return Optional.ofNullable(s.get());
            } catch (RuntimeException e) {
                if (indicatesMissingRequirement(e)) {
                    return Optional.empty();
                }
                throw e;
            }
        };
    }

    private static boolean indicatesMissingRequirement(RuntimeException e) {
        // There is no way to check if a SessionTokenAuthenticationDetailsProviderBuilder contains the information
        // required for building before attempting a build. You just have to try it, and then interpret the resulting
        // NullPointerException's message to see which requirement was missing, and repeat as necessary.
        if (e instanceof NullPointerException) {
            String message = e.getMessage();
            if (message != null && message.startsWith("SessionTokenAuthenticationDetailsProvider: ")) {
                // See
                // https://github.com/oracle/oci-java-sdk/blob/v3.34.1/bmc-common/src/main/java/com/oracle/bmc/auth/SessionTokenAuthenticationDetailsProvider.java#L112-L138
                message = message.substring("SessionTokenAuthenticationDetailsProvider: ".length());
                // Requirements were not supplied, but this isn't really an error, per se, just a reflection of the fact
                // that the builder is missing requirements, so this strategy isn't available.
                return message.startsWith("privateKeyFilePath ")
                    || message.startsWith("Set either region ")
                    || message.startsWith("Set either sessionToken ")
                    || message.startsWith("tenantId ");
            }
        }
        return false;
    }

    /**
     * A convenience method that invokes the {@link SessionTokenAuthenticationDetailsProviderBuilder#build()} method on
     * the supplied {@link SessionTokenAuthenticationDetailsProviderBuilder} and returns its results, wrapping any
     * {@link IOException}s encountered in {@link UncheckedIOException}s instead.
     *
     * @param b a {@link SessionTokenAuthenticationDetailsProviderBuilder}; must not be {@code null}
     *
     * @return the result of invoking the {@link SessionTokenAuthenticationDetailsProviderBuilder#build()} method on the
     * supplied {@link SessionTokenAuthenticationDetailsProviderBuilder}
     *
     * @exception NullPointerException if {@code b} was {@code null}
     *
     * @exception UncheckedIOException if an {@link IOException} was thrown by the {@link
     * SessionTokenAuthenticationDetailsProviderBuilder#build()} method; its {@linkplain Throwable#getCause() cause}
     * will be the original {@link IOException}
     *
     * @see SessionTokenAuthenticationDetailsProviderBuilder#build()
     */
    private static SessionTokenAuthenticationDetailsProvider produce(SessionTokenAuthenticationDetailsProviderBuilder b) {
        try {
            return b.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    /**
     * A convenience method that invokes the {@link
     * SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile)} constructor with
     * the supplied {@link ConfigFile} and returns the new {@link SessionTokenAuthenticationDetailsProvider}, wrapping
     * any {@link IOException}s encountered in {@link UncheckedIOException}s instead.
     *
     * @param cf a {@link ConfigFile}; must not be {@code null}
     *
     * @return the result of invoking the {@link
     * SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile)} constructor with
     * the supplied {@link ConfigFile}
     *
     * @exception NullPointerException if {@code cf} was {@code null}
     *
     * @exception UncheckedIOException if an {@link IOException} was thrown by the {@link
     * SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile)} method; its
     * {@linkplain Throwable#getCause() cause} will be the original {@link IOException}
     *
     * @see SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(ConfigFile)
     */
    private static SessionTokenAuthenticationDetailsProvider produce(ConfigFile cf) {
        try {
            return new SessionTokenAuthenticationDetailsProvider(cf);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

}
