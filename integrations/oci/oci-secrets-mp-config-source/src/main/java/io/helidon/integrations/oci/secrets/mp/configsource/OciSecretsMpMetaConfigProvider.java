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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.common.Prioritized;
import io.helidon.config.Config;
import io.helidon.config.mp.spi.MpMetaConfigProvider;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.Secrets;
import org.eclipse.microprofile.config.spi.ConfigSource;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.integrations.oci.secrets.mp.configsource.SecretsSuppliers.secrets;
import static io.helidon.integrations.oci.secrets.mp.configsource.Suppliers.memoizedSupplier;
import static java.lang.System.Logger;
import static java.lang.System.Logger.Level.DEBUG;
import static org.eclipse.microprofile.config.spi.ConfigSource.CONFIG_ORDINAL;
import static org.eclipse.microprofile.config.spi.ConfigSource.DEFAULT_ORDINAL;

/**
 * An {@link MpMetaConfigProvider} implementation that {@linkplain #create(String, Config, String) creates} {@link
 * ConfigSource} implementations backed by the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Oracle Cloud
 * Infrastructure (OCI) Secrets Retrieval API</a>.
 *
 * <p>Instances of this class are designed to be created by a {@link java.util.ServiceLoader} or the equivalent.</p>
 *
 * <p>Instances of this class are participants in Helidon's <a
 * href="https://helidon.io/docs/v3/#/mp/config/advanced-configuration">meta-configuration facility for MicroProfile
 * Config</a>. Following this facility's rules, here is one way to use the features of this class:</p>
 *
 * <ol>
 *
 * <li>Ensure you have at least one valid <a
 * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">mechanism set up properly for
 * authenticating with OCI</a>.</li>
 *
 * <li>Create a (or locate an existing) classpath resource named {@code mp-meta-config.yaml}.</li>
 *
 * <li>Ensure it has YAML analogous to the following:<blockquote><pre>sources:
 *  # (other sources may be present here)
 *  - type: {@value #SUPPORTED_TYPE} # must be exactly {@value #SUPPORTED_TYPE} (double- or single-quoted)
 *    accept-pattern: '^.+\.password$' # a {@linkplain java.util.regex.Matcher#matches() fully-matching} {@linkplain Pattern regular expression}
 *    vault-ocid: 'ocid...' # a valid OCI Vault <a href="https://docs.oracle.com/en-us/iaas/Content/General/Concepts/identifiers.htm">OCID</a>
 *  # (other sources may be present here)
 *</pre></blockquote></li>
 *
 * <li>Ensure the packaging artifact containing this class is present on the classpath or module path as
 * appropriate.</li>
 *
 * </ol>
 *
 * <p>After registering this class for use in this way, a {@link ConfigSource} will be {@linkplain #create(String,
 * Config, String) made available for use} by the standard MicroProfile Config {@link
 * org.eclipse.microprofile.config.Config Config} object. This {@link ConfigSource} will {@linkplain
 * ConfigSource#getValue(String) source its property values} from the OCI Vault identified by the relevant Vault OCID
 * you supplied in your {@code mp-meta-config.yaml} classpath resource, provided that the name of any property in
 * question is {@linkplain java.util.regex.Matcher#matches() entirely matched by} the {@code accept-pattern} regular
 * expression in your {@code mp-meta-config.yaml} resource (this is to prevent needless communication with a Vault for
 * property values that will never exist there).</p>
 *
 * @see MpMetaConfigProvider
 *
 * @see ConfigSource
 */
public final class OciSecretsMpMetaConfigProvider implements MpMetaConfigProvider, Prioritized {


    /*
     * Static fields.
     */


    /**
     * A {@link Logger} for the {@link OciSecretsMpMetaConfigProvider} class.
     */
    private static final Logger LOGGER = System.getLogger(OciSecretsMpMetaConfigProvider.class.getName());

    /**
     * An {@code int}, {@value #PRIORITY}, returned by the {@link #priority()} method.
     */
    public static final int PRIORITY = 300;

    /**
     * The sole {@linkplain #supportedTypes() supported type} of this {@link OciSecretsMpMetaConfigProvider} ({@value
     * #OCI_SECRETS}).
     *
     * @see #supportedTypes()
     *
     * @see #SUPPORTED_TYPES
     */
    private static final String SUPPORTED_TYPE = "oci-secrets";

    /**
     * An unmodifiable, unchanging {@link Set} of types returned by the {@link #supportedTypes()} method.
     *
     * <p>The {@link Set} consists of a single {@link String} whose value is {@value SUPPORTED_TYPE}.</p>
     *
     * @see #supportedTypes()
     */
    public static final Set<String> SUPPORTED_TYPES = Set.of(SUPPORTED_TYPE);

    /**
     * The name of a configuration property ({@value #VAULT_OCID_PROPERTY_NAME}) whose value should be a valid OCI Vault
     * <a href="https://docs.oracle.com/en-us/iaas/Content/General/Concepts/identifiers.htm">OCID</a>.
     */
    private static final String VAULT_OCID_PROPERTY_NAME = "vault-ocid";


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OciSecretsMpMetaConfigProvider}.
     *
     * @deprecated For {@link java.util.ServiceLoader} use only.
     */
    @Deprecated // For ServiceLoader use only.
    public OciSecretsMpMetaConfigProvider() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a non-{@code null}, unmodifiable, unchanging, determinate {@link Set} of identifiers under which this
     * {@link OciSecretsMpMetaConfigProvider} will be registered while a set of {@link ConfigSource}s is being
     * assembled.
     *
     * @return a non-{@code null}, unmodifiable, unchanging, determinate {@link Set} of identifiers
     *
     * @see #SUPPORTED_TYPES
     */
    @Override
    public Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    /**
     * Creates and returns a non-{@code null}, unmodifiable, unchanging {@link List} of {@link ConfigSource}
     * implementations suitable for the supplied ({@linkplain #supportedTypes() supported}) {@code type}, and as
     * initially set up using the supplied {@link Config} and (possibly {@code null}) {@code profile}.
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

        // The OCID of an OCI Vault within which named secrets may be found; necessary to talk to the OCI Secrets
        // Retrieval API.
        String vaultOcid = metaConfig.get(VAULT_OCID_PROPERTY_NAME)
            .asString()
            .orElse(System.getProperty(VAULT_OCID_PROPERTY_NAME)); // useful for testing
        if (vaultOcid == null) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                           "No meta-configuration value supplied for "
                           + metaConfig.key().toString() + "." + VAULT_OCID_PROPERTY_NAME
                           + " (and no fallback System property value supplied for "
                           + VAULT_OCID_PROPERTY_NAME
                           + "); skipping ConfigSource creation");
            }
            // (There's no point in trying to create a ConfigSource that cannot talk to the OCI Secrets Retrieval API.)
            return List.of();
        }

        // A Supplier of BasicAuthenticationDetailsProviders; necessary to talk to the OCI Secrets Retrieval API.
        Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier;
        try {
            adpSupplier = adpSupplier();
        } catch (NoSuchElementException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                           "No BasicAuthenticationDetailsProvider implementation available; skipping ConfigSource creation");
            }
            // (There's no point in trying to create a ConfigSource that cannot talk to the OCI Secrets Retrieval API.)
            return List.of();
        }

        // A Pattern to control which property values are sought from a Vault. Recall that MicroProfile Config
        // ConfigSource implementations can conceivably be called directly by an end user for any reason, and, if
        // ordinals are not set up properly, accidentally in the wrong sequence in a configuration setup. This could
        // result, for example, in a Vault-backed ConfigSource such as this one being asked to retrieve values for such
        // configuration property names as user.dir, java.home, and so on. The accept pattern helps to restrict what
        // communication the ConfigSource has with a Vault. If it is not specified, then ^.*$ will be used instead, and
        // therefore no restrictions on property resolution will occur.
        Pattern acceptPattern = Pattern.compile(metaConfig.get("accept-pattern").asString().orElse("^.*$"));

        // The value to be returned by the MicroProfile Config-defined getOrdinal() method. This is actually
        // meta-configuration which is why we supply it here.
        int ordinal = metaConfig.get("ordinal")
            .asInt()
            .or(() -> metaConfig.get(CONFIG_ORDINAL) // MicroProfile Config-defined name; people might cut/paste it
                .asInt().asOptional())
            .orElse(DEFAULT_ORDINAL); // MicroProfile Config-defined default value of 100

        // A Supplier of Secrets Retrieval API clients (which are also AutoCloseable implementations).
        Supplier<? extends Secrets> secretsSupplier = memoizedSupplier(secrets(adpSupplier));

        return
            List.of(new SecretBundleByNameConfigSource(ordinal,
                                                       Set::of,
                                                       acceptPattern,
                                                       vaultOcid,
                                                       secretsSupplier));
    }

    /**
     * Returns a (determinate) priority {{@value #PRIORITY}) for this {@link OciSecretsMpMetaConfigProvider} when
     * invoked.
     *
     * @return a determinate priority ({@value #PRIORITY}) wen invoked
     *
     * @see #PRIORITY
     */
    @Override
    public int priority() {
        return PRIORITY;
    }


    /*
     * Static methods.
     */


    /**
     * Returns a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances.
     *
     * @return a non-{@code null} {@link Supplier} of {@link BasicAuthenticationDetailsProvider} instances
     */
    private static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier() {
        // Provisional? until the "real" mechanism is approved/decided on/implemented.
        // The approach below is behaviorally identical to that of io.helidon.integrations.oci.cdi.OciExtension.
        Config ociYaml = Config.just(classpath("oci.yaml").optional(), file(Paths.get("oci.yaml")).optional());
        return memoizedSupplier(AdpSuppliers.adpSupplier(s -> ociYaml.get(s).asString().asOptional()));
    }

}
