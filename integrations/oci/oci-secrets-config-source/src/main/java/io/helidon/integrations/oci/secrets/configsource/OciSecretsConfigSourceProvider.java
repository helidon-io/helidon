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
package io.helidon.integrations.oci.secrets.configsource;

import java.util.Set;

import io.helidon.config.AbstractConfigSource;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;

import jakarta.annotation.Priority;

/**
 * A {@link ConfigSourceProvider} that {@linkplain #create(String, Config) creates} {@link ConfigSource} implementations
 * that interact with the Oracle Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
 *
 * <p>To use, ensure the packaging artifact (e.g. {@code .jar} file or similar) containing this class is present on your
 * class or module path as appropriate, and configure a meta-configuration source with a {@code type} of {@code
 * oci-secrets}, following the usual Helidon meta-configuration rules.</p>
 *
 * <p>More specifically:</p>
 *
 * <ol>
 *
 * <li>Ensure you have an authentication mechanism set up to connect to OCI (e.g. a valid <a
 * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">OCI configuration
 * file</a>). Authentication with OCI is accomplished via the {@link
 * io.helidon.integrations.oci.sdk.runtime.OciExtension} class; please see its documentation for how and when to set up
 * an {@code oci.yaml} classpath resource to further refine the mechanism of authentication.</li>
 *
 * <li>Ensure there is a classpath resource present named {@code meta-config.yaml}.</li>
 *
 * <li>Ensure the {@code meta-config.yaml} classpath resource contains a {@code sources} element with a {@code type} of
 * {@code oci-secrets} that looks similar to the following, substituting values as appropriate:<blockquote><pre>sources:
 *  - type: 'oci-secrets'
 *    properties:
 *      compartment-ocid: 'your vault compartment OCID goes here'
 *      vault-ocid: 'your vault OCID goes here'</pre></blockquote></li>
 *
 * </ol>
 *
 * <p>Refer to Helidon's documentation concerning meta-configuration for more details.</p>
 *
 * @see ConfigSourceProvider
 */
@Priority(300)
public final class OciSecretsConfigSourceProvider implements ConfigSourceProvider {


    /*
     * Static fields.
     */


    private static final Set<String> SUPPORTED_TYPES = Set.of("oci-secrets");


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OciSecretsConfigSourceProvider}.
     *
     * @deprecated For use by {@link java.util.ServiceLoader} only.
     */
    @Deprecated // For use by java.util.ServiceLoader only.
    public OciSecretsConfigSourceProvider() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Creates and returns a non-{@code null} {@link AbstractConfigSource} implementation that sources its values from
     * an Oracle Cloud Infrastructure (OCI) <a
     * href="https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm">Vault</a>.
     *
     * @param type one of the {@linkplain #supported() supported types}; not actually used
     *
     * @param metaConfig a {@link Config} serving as meta-configuration for this provider; must not be {@code null} when
     * {@code type} is {@linkplain #supports(String) supported}
     *
     * @return a non-{@code null} {@link AbstractConfigSource} implementation
     *
     * @exception NullPointerException if {@code type} is {@linkplain #supports(String) supported} and {@code
     * metaConfig} is {@code null}
     *
     * @see #supported()
     *
     * @see AbstractConfigSource
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public AbstractConfigSource create(String type, Config metaConfig) {
        if (metaConfig.get("lazy").asBoolean().orElse(Boolean.FALSE)) {
            return SecretBundleLazyConfigSource.builder().config(metaConfig).build();
        }
        return SecretBundleNodeConfigSource.builder().config(metaConfig).build();
    }

    /**
     * Returns a non-{@code null}, immutable {@link Set} of supported types suitable for the Helidon Config subsystem to
     * pass to the {@link #create(String, Config)} method.
     *
     * <p>This method returns a {@link Set} whose sole element is the string "{@code oci-secrets}".</p>
     *
     * @return a non-{@code null}, immutable {@link Set}
     *
     * @see #create(String, Config)
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public Set<String> supported() {
        return SUPPORTED_TYPES;
    }

    /**
     * Returns {@code true} if and only if the {@link Set} returned by an invocation of the {@link #supported()} method
     * {@linkplain Set#contains(Object) contains} it.
     *
     * @param type the type to test
     *
     * @return {@code true} if and only if the {@link Set} returned by an invocation of the {@link #supported()} method
     * {@linkplain Set#contains(Object) contains} it
     *
     * @see #supported()
     *
     * @see #create(String, Config)
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public boolean supports(String type) {
        return this.supported().contains(type);
    }

}
