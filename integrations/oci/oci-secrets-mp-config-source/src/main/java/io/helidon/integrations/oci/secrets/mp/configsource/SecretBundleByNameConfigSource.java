/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.oracle.bmc.secrets.Secrets;

import static io.helidon.integrations.oci.secrets.mp.configsource.Guards.guardWithAcceptPattern;
import static io.helidon.integrations.oci.secrets.mp.configsource.SecretBundleContentDetailsFunctions.secretBundleContentDetailsByName;

/**
 * An {@link AbstractSecretBundleConfigSource} that retrieves values for named secrets housed in an <a
 * href="https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Tasks/managingsecrets.htm">Oracle Cloud Infrastructure
 * (OCI) Vault</a> using the {@linkplain Secrets OCI Secrets Retrieval API}.
 */
final class SecretBundleByNameConfigSource extends AbstractSecretBundleConfigSource {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link SecretBundleByNameConfigSource}.
     *
     * @param ordinal a value to be returned by the {@link #getOrdinal()} method
     *
     * @param propertyNamesSupplier a {@link Supplier} of a {@link Set} of {@link String}s where the supplied {@link
     * Set} adheres to the requirements of the return value of the {@link #getPropertyNames()} method; must not be
     * {@code null}. Note that among other things this means a {@link Supplier} of an empty {@link Set} is permissible.
     *
     * @param acceptPattern a {@linkplain Pattern regular expression} against which prospective property names will be
     * {@linkplain java.util.regex.Matcher#matches() matched}; {@code null} will be returned from the {@link
     * #getValue(String)} method for any property names that do not match; must not be {@code null}
     *
     * @param vaultOcid a valid <a
     * href="https://docs.oracle.com/en-us/iaas/Content/General/Concepts/identifiers.htm">OCID</a> identifying an OCI
     * Vault; must not be {@code null}
     *
     * @param secretsSupplier a (normally {@linkplain Suppliers#memoizedSupplier(Supplier) normally memoized}) {@link
     * Supplier} of {@link Secrets} instances; must not be {@code null}
     *
     * @exception NullPointerException if any reference argument is {@code null}
     */
    SecretBundleByNameConfigSource(int ordinal,
                                   Supplier<? extends Set<String>> propertyNamesSupplier,
                                   Pattern acceptPattern,
                                   String vaultOcid,
                                   Supplier<? extends Secrets> secretsSupplier) {
        super(ordinal,
              propertyNamesSupplier,
              guardWithAcceptPattern(secretBundleContentDetailsByName(vaultOcid, secretsSupplier),
                                     acceptPattern),
              secretsSupplier,
              null);
    }

}
