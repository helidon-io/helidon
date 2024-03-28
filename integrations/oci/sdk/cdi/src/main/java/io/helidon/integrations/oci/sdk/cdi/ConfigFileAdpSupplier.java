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

import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.environmentVariables;
import static io.helidon.integrations.oci.sdk.cdi.ConfigAccessor.systemProperties;
import static io.helidon.integrations.oci.sdk.cdi.ConfigFiles.configFileSupplier;

/**
 * An {@link AdpSupplier} of {@link ConfigFileAuthenticationDetailsProvider} instances.
 *
 * @see #get()
 *
 * @see #ConfigFileAdpSupplier(Supplier, Function)
 *
 * @see ConfigFileAuthenticationDetailsProvider
 *
 * @see ConfigFiles
 */
class ConfigFileAdpSupplier implements AdpSupplier<ConfigFileAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(ConfigFileAdpSupplier.class.getName());


    /*
     * Instance fields.
     */


    private final Supplier<? extends ConfigFile> cfs;

    private final Function<? super ConfigFile, ? extends ConfigFileAuthenticationDetailsProvider> f;

    private final Predicate<? super RuntimeException> p;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ConfigFileAdpSupplier}.
     *
     * @see #ConfigFileAdpSupplier(Supplier)
     *
     * @see ConfigFiles#configFileSupplier(ConfigAccessor)
     */
    ConfigFileAdpSupplier() {
        this(configFileSupplier(systemProperties().thenTry(environmentVariables())));
    }

    /**
     * Creates a new {@link ConfigFileAdpSupplier}.
     *
     * @param configurationFilePath the first argument to be supplied to the {@link ConfigFiles#parse(String, String)}
     * method; may be {@code null} in which case the {@link ConfigFiles#parseDefault()} or {@link
     * ConfigFiles#parseDefault(String)} method will be used instead, based on the value of the supplied {@code profile}
     * argument
     *
     * @param profile the second argument to be supplied to the {@link ConfigFiles#parse(String, String)} method; may be
     * {@code null} in which case the {@link ConfigFiles#parseDefault(String)} or {@link ConfigFiles#parseDefault()}
     * method will be used instead, based on the value of the supplied {@code configurationFilePath}
     *
     * @see #ConfigFileAdpSupplier(Supplier)
     *
     * @see ConfigFiles#configFileSupplier(String, String)
     */
    ConfigFileAdpSupplier(String configurationFilePath, String profile) {
        this(configFileSupplier(configurationFilePath, profile));
    }

    /**
     * Creates a new {@link ConfigFileAdpSupplier}.
     *
     * @param configFileSupplier a {@link Supplier} of {@link ConfigFile} instances; must not be {@code null}; see
     * {@link ConfigFiles#configFileSupplier()} et al. for one way to supply an argument for this parameter
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #ConfigFileAdpSupplier(Supplier, Function)
     */
    ConfigFileAdpSupplier(Supplier<? extends ConfigFile> configFileSupplier) {
        this(configFileSupplier, ConfigFileAuthenticationDetailsProvider::new);
    }

    /**
     * Creates a new {@link ConfigFileAdpSupplier}.
     *
     * @param f a {@link Function} that accepts a {@link ConfigFile} and returns a {@link
     * ConfigFileAuthenticationDetailsProvider}; {@link
     * ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(ConfigFile)
     * ConfigFileAuthenticationDetailsProvider::new} is often supplied as an argument
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #ConfigFileAdpSupplier(Supplier, Function)
     */
    ConfigFileAdpSupplier(Function<? super ConfigFile, ? extends ConfigFileAuthenticationDetailsProvider> f) {
        this(configFileSupplier(systemProperties().thenTry(environmentVariables())), f);
    }

    /**
     * Creates a new {@link ConfigFileAdpSupplier}.
     *
     * @param configFileSupplier a {@link Supplier} of {@link ConfigFile} instances; must not be {@code null}; see
     * {@link ConfigFiles#configFileSupplier()} et al. for one way to supply an argument for this parameter
     *
     * @param f a {@link Function} that accepts a {@link ConfigFile} and returns a {@link
     * ConfigFileAuthenticationDetailsProvider}; {@link
     * ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(ConfigFile)
     * ConfigFileAuthenticationDetailsProvider::new} is often supplied as an argument
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    ConfigFileAdpSupplier(Supplier<? extends ConfigFile> configFileSupplier,
                          Function<? super ConfigFile, ? extends ConfigFileAuthenticationDetailsProvider> f) {
        this(configFileSupplier, f, ConfigFiles::indicatesConfigFileAbsence);
    }

    /**
     * Creates a new {@link ConfigFileAdpSupplier} (advanced constructor).
     *
     * @param configFileSupplier a {@link Supplier} of {@link ConfigFile} instances; must not be {@code null}; see
     * {@link ConfigFiles#configFileSupplier()} et al. for one way to supply an argument for this parameter
     *
     * @param f a {@link Function} that accepts a {@link ConfigFile} and returns a {@link
     * ConfigFileAuthenticationDetailsProvider}; {@link
     * ConfigFileAuthenticationDetailsProvider#ConfigFileAuthenticationDetailsProvider(ConfigFile)
     * ConfigFileAuthenticationDetailsProvider::new} is often supplied as an argument
     *
     * @param indicatesConfigFileAbsence (for advanced use cases) a {@link Predicate} for determining whether a given
     * {@link Runtime} exception actually merely indicates the absence of a configuration file; {@link
     * ConfigFiles#indicatesConfigFileAbsence(RuntimeException) ConfigFiles::indicatesConfigFileAbsence} should almost
     * always be supplied as an argument
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #ConfigFileAdpSupplier(Supplier, Function)
     */
    ConfigFileAdpSupplier(Supplier<? extends ConfigFile> configFileSupplier,
                          Function<? super ConfigFile, ? extends ConfigFileAuthenticationDetailsProvider> f,
                          Predicate<? super RuntimeException> indicatesConfigFileAbsence) {
        super();
        this.cfs = configFileSupplier == null ? ConfigFiles::parseDefault : configFileSupplier;
        this.f = f == null ? ConfigFileAuthenticationDetailsProvider::new : f;
        this.p = indicatesConfigFileAbsence == null ? ConfigFiles::indicatesConfigFileAbsence : indicatesConfigFileAbsence;
    }


    /*
     * Instance methods.
     */


    /**
     * Builds and returns an {@link Optional} containing a {@link ConfigFileAuthenticationDetailsProvider}, or an
     * {@linkplain Optional#isEmpty() empty} {@link Optional} if the preconditions for building a {@link
     * ConfigFileAuthenticationDetailsProvider} cannot be met.
     *
     * @return a (possibly {@linkplain Optional#isEmpty() empty}) {@link Optional} containing a {@link
     * ConfigFileAuthenticationDetailsProvider}; never {@code null}
     *
     * @exception UncheckedIOException if the {@linkplain #ConfigFileAdpSupplier(Supplier)
     * <code>configFileSupplier</code> supplied at construction time} throws an {@link UncheckedIOException}; its
     * {@linkplain Throwable#getCause() cause} will reflect the nature of the underlying problem
     *
     * @see #validConfigFile()
     *
     * @see ConfigFileAuthenticationDetailsProvider
     */
    @Override
    public Optional<ConfigFileAuthenticationDetailsProvider> get() {
        return this.validConfigFile()
            .map(this.f);
    }

    /**
     * Returns a (possibly {@linkplain Optional#empty() empty}) {@link Optional} containing a {@link ConfigFile}.
     *
     * @return a (possibly {@linkplain Optional#empty() empty}) {@link Optional} containing a {@link ConfigFile}; never
     * {@code null}
     *
     * @exception UncheckedIOException if the {@linkplain #ConfigFileAdpSupplier(Supplier)
     * <code>configFileSupplier</code> supplied at construction time} throws an {@link UncheckedIOException}; its
     * {@linkplain Throwable#getCause() cause} will reflect the nature of the underlying problem
     *
     * @see ConfigFiles#configFile(Supplier)
     */
    public Optional<ConfigFile> validConfigFile() {
        return ConfigFiles.configFile(this.cfs, this.p)
            .filter(ConfigFileAdpSupplier::containsRequiredValues);
    }

    /**
     * Returns {@code true} if and only if the supplied {@link ConfigFile} has the minimal set of required information
     * present for all possible OCI-supported {@link ConfigFileAuthenticationDetailsProvider}-related use cases.
     *
     * <p>No additional validation of any kind is performed by this method.</p>
     *
     * @param cf a {@link ConfigFile ConfigFile}; must not be {@code null}
     *
     * @return {@code true} if and only if the supplied {@link ConfigFile} has the minimal set of required information
     * present for all possible OCI-supported authentication-related use cases
     *
     * @exception NullPointerException if {@code cf} is {@code null}
     *
     * @see ConfigFiles#containsRequiredValues(ConfigFile)
     */
    // (available())
    public static boolean containsRequiredValues(ConfigFile cf) {
        // Rule out SessionTokenAuthenticationDetailsProvider usage up front.
        return cf.get("security_token_file") == null && ConfigFiles.containsRequiredValues(cf);
    }

}
