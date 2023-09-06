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

import java.util.List;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.mp.spi.MpMetaConfigProvider;
import io.helidon.integrations.oci.secrets.configsource.OciSecretsConfigSourceProvider;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An {@link MpMetaConfigProvider} that uses the Oracle Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs
 * to provide a {@link ConfigSource} implementation.
 *
 * <p>This class adapts the Helidon {@link io.helidon.config.spi.ConfigSource} created by the {@link
 * OciSecretsConfigSourceProvider} to the MicroProfile Config contracts.
 *
 * @see #create(String, Config, String)
 *
 * @see OciSecretsConfigSourceProvider
 */
public final class OciSecretsMpMetaConfigProvider implements MpMetaConfigProvider {

    private final OciSecretsConfigSourceProvider p;

    /**
     * Creates a new {@link OciSecretsMpMetaConfigProvider}.
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For java.util.ServiceLoader use only.
    @SuppressWarnings("deprecation")
    public OciSecretsMpMetaConfigProvider() {
        super();
        this.p = new OciSecretsConfigSourceProvider();
    }

    /**
     * Returns an immutable {@link List} whose sole element is a {@link ConfigSource} implementation backed by the Oracle Cloud Infrastructure (OCI) <a
     * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
     * Retrieval</a> and <a
     * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
     *
     * @param type ignored
     *
     * @param metaConfig a Helidon {@link Config} representing meta-configuration
     *
     * @param profile a configuration profile; not currently used
     *
     * @return a single-element immutable {@link List}
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<? extends ConfigSource> create(String type, Config metaConfig, String profile) {
        return List.of(MpConfigSources.create(Config.builder()
                                              .disableEnvironmentVariablesSource()
                                              .disableFilterServices()
                                              .disableMapperServices()
                                              .disableParserServices()
                                              .disableSystemPropertiesSource()
                                              .addSource(this.p.create(type, metaConfig))
                                              .build()));
    }

    /**
     * Returns the return value of an invocation of {@link OciSecretsConfigSourceProvider#supported()}.
     *
     * @return an immutable {@link Set}
     */
    @Override
    @SuppressWarnings("deprecation")
    public Set<String> supportedTypes() {
        return this.p.supported();
    }

}
