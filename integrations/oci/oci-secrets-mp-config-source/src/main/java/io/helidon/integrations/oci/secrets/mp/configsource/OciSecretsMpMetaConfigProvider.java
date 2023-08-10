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

import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.common.Prioritized;
import io.helidon.config.Config;
import io.helidon.config.mp.spi.MpMetaConfigProvider;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.integrations.oci.secrets.mp.configsource.SecretsSuppliers.secrets;
import static io.helidon.integrations.oci.secrets.mp.configsource.Suppliers.memoizedSupplier;

/**
 * An {@link MpMetaConfigProvider} implementation that {@linkplain #create(String, Config, String) creates} {@link
 * ConfigSource} implementations backed by the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">OCI Secrets
 * Retrieval API</a>.
 *
 * @see MpMetaConfigProvider
 *
 * @see ConfigSource
 */
public final class OciSecretsMpMetaConfigProvider implements MpMetaConfigProvider, Prioritized {

    private static final String ACCEPT_PATTERN_KEY = "accept-pattern";

    private static final int PRIORITY = 300;

    /**
     * An unmodifiable, unchanging {@link Set} of types returned by the {@link #supportedTypes()} method.
     */
    public static final Set<String> SUPPORTED_TYPES = Set.of("oci-secrets");

    private static final String VAULT_OCID_KEY = "vault-ocid";

    /**
     * Creates a new {@link OciSecretsMpMetaConfigProvider}.
     *
     * @deprecated For {@link java.util.ServiceLoader} use only.
     */
    @Deprecated // For ServiceLoader use only.
    public OciSecretsMpMetaConfigProvider() {
        super();
    }

    /**
     * Returns a non-{@code null}, unmodifiable, unchanging, determinate {@link Set} of identifiers under which this
     * {@link OciSecretsMpMetaConfigProvider} will be registered while a set of {@link ConfigSource}s is being
     * assembled.
     *
     * @return a non-{@code null}, unmodifiable, unchanging, determinate {@link Set} of identifiers
     */
    @Override
    public Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    /**
     * Creates and returns a non-{@code null}, unmodifiable, unchanging {@link List} of {@link ConfigSource}
     * implementations suitable for the supplied {@code type}, and as initially set up using the supplied {@link Config}
     * and (possibly {@code null}) {@code profile}.
     *
     * @param type a type drawn from the {@link Set} of {@linkplain #supportedTypes() supported types}; must not be
     * {@code null}
     *
     * @param metaConfig a {@link Config} representing meta-configuration that will be used to set up the {@link
     * ConfigSource}s that are returned; must not be {@code null}
     *
     * @param profile a configuration profile, or {@code null} if there is none
     *
     * @return a non-{@code null}, unmodifiable, unchanging {@link List} of {@link ConfigSource} implementations
     *
     * @exception NullPointerException if {@code type} or {@code metaConfig} is {@code null}
     *
     * @exception IllegalArgumentException if {@code type} is not present in the determinate {@link Set} of types
     * returned by an invocation of the {@link #supportedTypes()} method
     *
     * @exception java.util.regex.PatternSyntaxException if a regular expression syntax was not correct
     *
     * @see MpMetaConfigProvider#create(String, Config, String)
     *
     * @see #supportedTypes()
     */
    @Override
    public List<? extends ConfigSource> create(String type, Config metaConfig, String profile) {
        if (!this.supportedTypes().contains(Objects.requireNonNull(type, "type"))) {
            throw new IllegalArgumentException("type: " + type);
        }
        Pattern acceptPattern =
            Pattern.compile(metaConfig.get(ACCEPT_PATTERN_KEY)
                            .asString()
                            .or(() -> Optional.ofNullable(System.getProperty(ACCEPT_PATTERN_KEY))) // useful for testing
                            .orElseThrow());
        String vaultId = metaConfig.get(VAULT_OCID_KEY)
            .asString()
            .orElse(System.getProperty(VAULT_OCID_KEY)); // useful for testing
        return
            List.of(new SecretBundleByNameConfigSource(acceptPattern,
                                                       vaultId,
                                                       memoizedSupplier(secrets(adpSupplier()))));
    }

    private Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier() {
        // Provisional? until the "real" mechanism is approved/decided on.
        // The approach below is behaviorally identical to that of io.helidon.integrations.oci.cdi.OciExtension.
        Config ociYaml = Config.just(classpath("oci.yaml").optional(), file(Paths.get("oci.yaml")).optional());
        return memoizedSupplier(AdpSuppliers.adpSupplier(s -> ociYaml.get(s).asString().asOptional()));
    }

    /**
     * Returns a (determinate) priority for this {@link OciSecretsMpMetaConfigProvider} when invoked.
     *
     * @return a determinate priority
     */
    @Override
    public int priority() {
        return PRIORITY;
    }

}
