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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

import static io.helidon.integrations.oci.sdk.cdi.ConfigFiles.configFileSupplier;

class ConfigFileAdpSupplier implements AdpSupplier<ConfigFileAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(ConfigFileAdpSupplier.class.getName());


    /*
     * Instance fields.
     */


    private final Supplier<? extends ConfigFile> cfs;


    /*
     * Constructors.
     */


    ConfigFileAdpSupplier(ConfigAccessor ca) {
        this(configFileSupplier(ca));
    }

    ConfigFileAdpSupplier(Supplier<? extends ConfigFile> configFileSupplier) {
        super();
        this.cfs = Objects.requireNonNull(configFileSupplier, "configFileSupplier");
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
     * @see #configFile()
     *
     * @see ConfigFileAuthenticationDetailsProvider
     */
    @Override
    public Optional<ConfigFileAuthenticationDetailsProvider> get() {
        Optional<? extends ConfigFile> o = this.configFile();
        return Optional.ofNullable(o.isPresent() ? new ConfigFileAuthenticationDetailsProvider(o.orElseThrow()) : null);
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
    public Optional<ConfigFile> configFile() {
        return
            ConfigFiles.configFile(this.cfs)
            .filter(ConfigFileAdpSupplier::containsRequiredValues);
    }

    public static boolean containsRequiredValues(ConfigFile cf) {
        return cf.get("fingerprint") != null
            && cf.get("user") != null
            && ConfigFiles.containsRequiredValues(cf);
    }

}
