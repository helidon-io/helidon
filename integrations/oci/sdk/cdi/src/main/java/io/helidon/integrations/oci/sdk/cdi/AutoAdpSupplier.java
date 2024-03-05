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

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;

import static java.util.function.Predicate.not;

class AutoAdpSupplier extends CascadingAdpSupplier<BasicAuthenticationDetailsProvider> {


    /*
     * Static fields.
     */


    private static final List<String> DEFAULT_NAMES =
        List.of("config", "config-file", "session-token-config-file", "instance-principals", "resource-principal");

    private static final Pattern WHITESPACE_COMMA_WHITESPACE_PATTERN = Pattern.compile("\\s*,\\s*");


    /*
     * Constructors.
     */


    AutoAdpSupplier(ConfigAccessor ca,
                    Function<? super String, ? extends AdpSupplier<? extends BasicAuthenticationDetailsProvider>> f) {
        super(names(ca), f);
    }

    AutoAdpSupplier(ConfigAccessor ca,
                    Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> sadpbs,
                    Supplier<? extends ConfigFile> cfs,
                    Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> ipadps,
                    Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> rpadps) {
        super(DEFAULT_NAMES,
              n -> switch (n) {
              case                    "config" -> new SimpleAdpSupplier(sadpbs, ca);
              case               "config-file" -> new ConfigFileAdpSupplier(cfs);
              case       "instance-principals" -> new InstancePrincipalsAdpSupplier(ipadps, ca);
              case        "resource-principal" -> new ResourcePrincipalAdpSupplier(rpadps);
              case "session-token-config-file" -> SessionTokenAdpSupplier.ofConfigFileSupplier(cfs);
              default                          -> throw new IllegalArgumentException("n: " + n);
              });
    }


    /*
     * Static methods.
     */


    static List<String> names(ConfigAccessor ca) {
        return ca.get("oci.auth-strategies")
            .or(() -> ca.get("oci.auth-strategy"))
            .map(String::strip)
            .filter(not(String::isEmpty))
            .map(WHITESPACE_COMMA_WHITESPACE_PATTERN::split)
            .map(List::of)
            .orElse(DEFAULT_NAMES);
    }

}
