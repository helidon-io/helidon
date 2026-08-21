/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlDriver;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

/**
 * Creates qualified JDBC clients from configured persistence units.
 */
@Service.Singleton
final class JdbcPersistenceUnitFactory implements Service.ServicesFactory<JdbcClient> {

    // All JDBC persistence units are read from this configuration branch.
    static final String CONFIG_KEY = "data.persistence-units.jdbc";

    // Names identify persistence units in Service Registry qualifiers and application-visible diagnostics.
    private static final String NAME_CONFIG_KEY = "name";

    // A data-source value selects an existing named datasource and is mutually exclusive with direct connection
    // settings.
    private static final String DATA_SOURCE_CONFIG_KEY = "data-source";

    // A connection value selects provider-created direct JDBC access and is mutually exclusive with a named
    // datasource.
    private static final String CONNECTION_CONFIG_KEY = "connection";

    // Initialization scripts run after any configured drop script and before the JDBC client is published.
    private static final String INIT_SCRIPT_CONFIG_KEY = "init-script";

    // Drop scripts run before initialization so bootstrap ordering is deterministic for applications.
    private static final String DROP_SCRIPT_CONFIG_KEY = "drop-script";

    // Provider properties remain under their own typed persistence unit subtree.
    private static final String PROPERTIES_CONFIG_KEY = "properties";

    // Plain inline content is measured as the UTF-8 bytes that the common resource builder would create.
    private static final String CONTENT_PLAIN_CONFIG_KEY = "content-plain";

    // Binary inline content uses the basic Base64 alphabet understood by the common resource builder.
    private static final String CONTENT_CONFIG_KEY = "content";

    // Resource configuration keys accepted for init-script and drop-script.
    private static final String SUPPORTED_SCRIPT_RESOURCE_KEYS =
            "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.";

    private static final List<String> SCRIPT_RESOURCE_KEYS = List.of("path",
                                                                     "resource-path",
                                                                     "uri",
                                                                     "content-plain",
                                                                     "content");

    // JDBC drivers conventionally receive direct-connection usernames through the standard user property.
    private static final String USER_PROPERTY = "user";

    // JDBC drivers conventionally receive direct-connection credentials through the standard password property.
    private static final String PASSWORD_PROPERTY = "password";

    // Publish every client with the JDBC provider qualifier so injection can distinguish it from other Data providers.
    private static final Qualifier PROVIDER_QUALIFIER = Qualifier.builder()
            .typeName(Data.ProviderType.TYPE)
            .value(Jdbc.PROVIDER)
            .build();

    private final Supplier<List<ServiceInstance<DataSource>>> dataSources;
    private final Supplier<Config> config;
    private final JdbcTransactionConnectionManager connectionManager;

    /**
     * Creates the persistence-unit service factory.
     *
     * @param dataSources available datasource services
     * @param config application configuration
     * @param connectionManager transaction connection manager
     */
    @Service.Inject
    JdbcPersistenceUnitFactory(Supplier<List<ServiceInstance<DataSource>>> dataSources,
                               Supplier<Config> config,
                               JdbcTransactionConnectionManager connectionManager) {
        this.dataSources = dataSources;
        this.config = config;
        this.connectionManager = connectionManager;
    }

    @Override
    public List<Service.QualifiedInstance<JdbcClient>> services() {
        List<Config> units = config.get().get(CONFIG_KEY).asNodeList().orElse(List.of());
        Set<String> names = new HashSet<>();
        List<ValidatedUnit> validatedUnits = new ArrayList<>(units.size());
        // The first phase validates every provider policy before resource creation or JDBC activation can cause
        // side effects.
        for (Config unitConfig : units) {
            String unitName = unitConfig.get(NAME_CONFIG_KEY).asString().orElse(Service.Named.DEFAULT_NAME);
            Config dataSource = unitConfig.get(DATA_SOURCE_CONFIG_KEY);
            boolean hasDataSource = dataSource.exists();
            boolean hasConnection = unitConfig.get(CONNECTION_CONFIG_KEY).exists();
            if (hasDataSource == hasConnection) {
                throw new DataException(persistenceUnitDescription(unitName)
                                                + " must specify exactly one connection source. Configure either "
                                                + "'data-source' or 'connection'.");
            }
            if (hasDataSource && dataSource.asString().orElse("").isBlank()) {
                throw new DataException(persistenceUnitDescription(unitName)
                                                + " has a blank 'data-source' name.");
            }
            validateBootstrapResourceSourceKeys(unitName, unitConfig);
            List<JdbcBootstrapResource.Descriptor> descriptors = bootstrapDescriptors(unitConfig);
            validateBootstrapResources(unitName, descriptors);
            if (unitName.isBlank()) {
                throw new DataException("A JDBC persistence unit name must not be blank.");
            }
            if (!names.add(unitName)) {
                throw new DataException(duplicatePersistenceUnitNameMessage(unitName));
            }
            JdbcProviderPropertiesSupport.Policy policy;
            try {
                JdbcPropertiesConfig properties = JdbcPropertiesConfig.create(unitConfig.get(PROPERTIES_CONFIG_KEY));
                policy = JdbcProviderPropertiesSupport.create(properties);
            } catch (DataException failure) {
                throw new DataException(persistenceUnitDescription(unitName)
                                                + " has invalid JDBC provider properties. " + failure.getMessage());
            } catch (RuntimeException failure) {
                throw new DataException(persistenceUnitDescription(unitName) + " has invalid JDBC provider properties.",
                                        JdbcExceptionTranslator.sanitize("reading JDBC provider properties", failure));
            }
            validatedUnits.add(new ValidatedUnit(unitConfig,
                                                 unitName,
                                                 descriptors,
                                                 policy));
        }

        // The second phase preflights inline allocation and detaches scripts while JDBC services remain untouched.
        List<ConfiguredUnit> configuredUnits = new ArrayList<>(validatedUnits.size());
        for (ValidatedUnit validatedUnit : validatedUnits) {
            String unitName = validatedUnit.name();
            Config unitConfig = validatedUnit.config();
            List<JdbcBootstrapResource.Descriptor> descriptors = validatedUnit.descriptors();
            // One budget applies the per resource limit and the remaining total limit to all inline scripts in plan
            // order.
            JdbcScriptRunner.BootstrapBudget budget = validatedUnit.policy().bootstrap().newBudget();
            for (JdbcBootstrapResource.Descriptor descriptor : descriptors) {
                JdbcBootstrapResource.SourceType sourceType = descriptor.sourceType();
                if (sourceType != JdbcBootstrapResource.SourceType.CONFIGURED_TEXT
                        && sourceType != JdbcBootstrapResource.SourceType.CONFIGURED_BINARY) {
                    // Other resource forms are bounded when the script runner reads their streams.
                    continue;
                }
                Config scriptConfig = unitConfig.get(scriptConfigKey(descriptor.role()));
                // The effective limit is the smaller of the per resource limit and the remaining total budget.
                int limit = budget.resourceLimit();
                try {
                    if (sourceType == JdbcBootstrapResource.SourceType.CONFIGURED_TEXT) {
                        String content = scriptConfig.get(CONTENT_PLAIN_CONFIG_KEY).asString().get();
                        int encodedBytes = 0;
                        // Count the bytes that String UTF-8 encoding would create without creating the byte array.
                        for (int index = 0; index < content.length(); index++) {
                            char character = content.charAt(index);
                            int characterBytes;
                            if (character <= 0x7f) {
                                characterBytes = 1;
                            } else if (character <= 0x7ff) {
                                characterBytes = 2;
                            } else if (Character.isHighSurrogate(character)
                                    && index + 1 < content.length()
                                    && Character.isLowSurrogate(content.charAt(index + 1))) {
                                characterBytes = 4;
                                index++;
                            } else if (Character.isSurrogate(character)) {
                                // String UTF-8 encoding replaces an unmatched surrogate with one byte.
                                characterBytes = 1;
                            } else {
                                characterBytes = 3;
                            }
                            // Stop before allocating resource storage once the next character proves the limit is
                            // exceeded.
                            if (encodedBytes > limit - characterBytes) {
                                throw inlineSizeFailure(unitName, descriptor, budget);
                            }
                            encodedBytes += characterBytes;
                        }
                        // Reserve the bytes so the next inline script sees the remaining aggregate budget.
                        budget.addBytes(encodedBytes);
                    } else {
                        String content = scriptConfig.get(CONTENT_CONFIG_KEY).asString().get();
                        int symbols = 0;
                        int padding = 0;
                        boolean paddingStarted = false;
                        int decodedBytes = 0;
                        // Validate the basic Base64 alphabet while counting decoded groups without decoding the
                        // payload.
                        for (int index = 0; index < content.length(); index++) {
                            char character = content.charAt(index);
                            boolean alphabet = (character >= 'A' && character <= 'Z')
                                    || (character >= 'a' && character <= 'z')
                                    || (character >= '0' && character <= '9')
                                    || character == '+'
                                    || character == '/';
                            if (alphabet && !paddingStarted) {
                                symbols++;
                                // Each complete group of four alphabet symbols contributes three decoded bytes.
                                if (symbols % 4 == 0) {
                                    if (decodedBytes > limit - 3) {
                                        throw inlineSizeFailure(unitName, descriptor, budget);
                                    }
                                    decodedBytes += 3;
                                }
                            } else if (character == '=') {
                                // Padding is valid only at the end and contains no more than two symbols.
                                paddingStarted = true;
                                padding++;
                                if (padding > 2) {
                                    throw invalidBase64Failure(unitName, descriptor);
                                }
                            } else {
                                throw invalidBase64Failure(unitName, descriptor);
                            }
                        }
                        // Validate the final partial group and determine its one or two decoded bytes.
                        int remainder = symbols % 4;
                        int finalBytes;
                        if (padding == 0) {
                            if (remainder == 1) {
                                throw invalidBase64Failure(unitName, descriptor);
                            }
                            finalBytes = remainder == 0 ? 0 : remainder - 1;
                        } else if (padding == 1 && remainder == 3 && (symbols + padding) % 4 == 0) {
                            finalBytes = 2;
                        } else if (padding == 2 && remainder == 2 && (symbols + padding) % 4 == 0) {
                            finalBytes = 1;
                        } else {
                            throw invalidBase64Failure(unitName, descriptor);
                        }
                        if (decodedBytes > limit - finalBytes) {
                            throw inlineSizeFailure(unitName, descriptor, budget);
                        }
                        decodedBytes += finalBytes;
                        // Reserve the decoded bytes so the next inline script sees the remaining aggregate budget.
                        budget.addBytes(decodedBytes);
                    }
                } catch (DataException failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new DataException(persistenceUnitDescription(unitName) + " has invalid inline content for '"
                                                    + scriptConfigKey(descriptor.role()) + "'.",
                                            JdbcExceptionTranslator.sanitize("reading inline bootstrap content",
                                                                             failure));
                }
            }
            JdbcPersistenceUnitConfig unit;
            try {
                unit = JdbcPersistenceUnitConfig.create(unitConfig);
            } catch (RuntimeException e) {
                throw new DataException(invalidPersistenceUnitConfigurationMessage(unitName, descriptors),
                                        JdbcExceptionTranslator.sanitize("creating a persistence unit configuration",
                                                                         e));
            }
            List<JdbcBootstrapResource> resources = new ArrayList<>(2);
            // Drop is both loaded and executed before initialization.
            unit.dropScript().ifPresent(resource -> resources.add(
                    JdbcBootstrapResource.create(JdbcBootstrapResource.Role.DROP, resources.size() + 1, resource)));
            unit.initScript().ifPresent(resource -> resources.add(
                    JdbcBootstrapResource.create(JdbcBootstrapResource.Role.INIT, resources.size() + 1, resource)));
            JdbcProviderPropertiesSupport.Policy policy = validatedUnit.policy();
            configuredUnits.add(new ConfiguredUnit(unit,
                                                   JdbcScriptRunner.load(unitName, resources, policy.bootstrap()),
                                                   policy.cache()));
        }

        // The final phase resolves connection sources, runs bootstrap scripts, and publishes fully initialized clients.
        List<Service.QualifiedInstance<JdbcClient>> result = new ArrayList<>(configuredUnits.size());
        for (ConfiguredUnit configuredUnit : configuredUnits) {
            JdbcPersistenceUnitConfig unit = configuredUnit.config();
            JdbcClient client = createClient(configuredUnit);
            Qualifier named = Qualifier.createNamed(unit.name());
            // A single view with both qualifiers avoids duplicate candidates for named lookups.
            result.add(Service.QualifiedInstance.create(client, named, PROVIDER_QUALIFIER));
        }
        return List.copyOf(result);
    }

    /**
     * Rejects URI bootstrap configuration before the common resource factory
     * can open its stream. The script runner repeats this check for resources
     * supplied programmatically.
     *
     * @param unitName persistence-unit name
     * @param descriptors configured bootstrap descriptors
     */
    private static void validateBootstrapResources(String unitName,
                                                   List<JdbcBootstrapResource.Descriptor> descriptors) {
        descriptors.stream()
                .filter(descriptor -> descriptor.sourceType() == JdbcBootstrapResource.SourceType.URI)
                .findFirst()
                .ifPresent(descriptor -> {
                    throw new DataException(persistenceUnitDescription(unitName)
                                                    + " does not support a URI value for '"
                                                    + scriptConfigKey(descriptor.role()) + "'.");
                });

    }

    /**
     * Validates the resource source selection for both bootstrap roles before
     * either resource can be constructed.
     *
     * @param unitName persistence unit name
     * @param unitConfig raw persistence unit configuration
     */
    private static void validateBootstrapResourceSourceKeys(String unitName, Config unitConfig) {
        validateBootstrapResourceSourceKeys(unitName, unitConfig.get(DROP_SCRIPT_CONFIG_KEY), DROP_SCRIPT_CONFIG_KEY);
        validateBootstrapResourceSourceKeys(unitName, unitConfig.get(INIT_SCRIPT_CONFIG_KEY), INIT_SCRIPT_CONFIG_KEY);
    }

    /**
     * Rejects an ambiguous resource definition instead of allowing the common
     * resource builder to select one configured source by precedence.
     *
     * @param unitName persistence unit name
     * @param scriptConfig raw script resource configuration
     * @param scriptKey persistence unit key for the script role
     */
    private static void validateBootstrapResourceSourceKeys(String unitName, Config scriptConfig, String scriptKey) {
        if (!scriptConfig.exists()) {
            return;
        }
        List<String> configuredKeys = SCRIPT_RESOURCE_KEYS.stream()
                .filter(key -> scriptConfig.get(key).exists())
                .map(key -> "'" + key + "'")
                .toList();
        if (configuredKeys.size() > 1) {
            throw new DataException(persistenceUnitDescription(unitName)
                                            + " has invalid value for '" + scriptKey + "'. Configure exactly one "
                                            + "resource source key for '" + scriptKey + "'. Configured keys are "
                                            + String.join(" and ", configuredKeys) + ". "
                                            + SUPPORTED_SCRIPT_RESOURCE_KEYS);
        }
    }

    private static String persistenceUnitDescription(String name) {
        return Service.Named.DEFAULT_NAME.equals(name)
                ? "JDBC persistence unit configuration"
                : "JDBC persistence unit '" + name + "'";
    }

    private static String duplicatePersistenceUnitNameMessage(String name) {
        return Service.Named.DEFAULT_NAME.equals(name)
                ? "Each JDBC persistence unit must have a unique name. "
                        + "More than one configured persistence unit is unnamed."
                : "More than one JDBC persistence unit is named '" + name + "'.";
    }

    private static String invalidPersistenceUnitConfigurationMessage(
            String name,
            List<JdbcBootstrapResource.Descriptor> descriptors) {
        List<String> invalidSettings = descriptors.stream()
                .filter(descriptor -> descriptor.sourceType() == JdbcBootstrapResource.SourceType.UNSPECIFIED)
                .map(descriptor -> "'" + scriptConfigKey(descriptor.role()) + "'")
                .toList();
        if (!invalidSettings.isEmpty()) {
            String subject = invalidSettings.size() == 1 ? "value" : "values";
            String target = String.join(" and ", invalidSettings);
            return persistenceUnitDescription(name) + " has invalid " + subject + " for "
                    + target
                    + ". Configure " + target + " as a resource object. "
                    + SUPPORTED_SCRIPT_RESOURCE_KEYS;
        }
        if (!descriptors.isEmpty()) {
            JdbcBootstrapResource.Descriptor descriptor = descriptors.getFirst();
            return persistenceUnitDescription(name) + " could not load the " + descriptor.sourceType().text()
                    + " " + descriptor.role().text() + " script configured by '"
                    + scriptConfigKey(descriptor.role()) + "." + scriptSourceKey(descriptor.sourceType()) + "'. "
                    + scriptSourceGuidance(descriptor.sourceType());
        }
        return Service.Named.DEFAULT_NAME.equals(name)
                ? "The JDBC persistence unit configuration is invalid."
                : "The configuration for JDBC persistence unit '" + name + "' is invalid.";
    }

    private static String scriptSourceKey(JdbcBootstrapResource.SourceType sourceType) {
        return switch (sourceType) {
        case FILE -> "path";
        case CLASSPATH -> "resource-path";
        case CONFIGURED_TEXT -> "content-plain";
        case CONFIGURED_BINARY -> "content";
        case URI -> "uri";
        case SUPPLIED_STREAM, UNSPECIFIED -> "resource";
        };
    }

    private static String scriptSourceGuidance(JdbcBootstrapResource.SourceType sourceType) {
        return switch (sourceType) {
        case FILE -> "Ensure the filesystem path points to an existing file readable by the application process.";
        case CLASSPATH -> "Ensure the classpath resource exists in the application runtime classpath.";
        case CONFIGURED_BINARY -> "Ensure the configured content is valid Base64.";
        case CONFIGURED_TEXT -> "Ensure the configured text content is valid.";
        case URI -> "URI-backed bootstrap scripts are not supported.";
        case SUPPLIED_STREAM, UNSPECIFIED -> "Use one supported resource source key. " + SUPPORTED_SCRIPT_RESOURCE_KEYS;
        };
    }

    private static String scriptConfigKey(JdbcBootstrapResource.Role role) {
        return switch (role) {
        case INIT -> INIT_SCRIPT_CONFIG_KEY;
        case DROP -> DROP_SCRIPT_CONFIG_KEY;
        };
    }

    /**
     * Creates safe descriptors directly from configuration so failures during
     * generated resource construction cannot disclose a location through the
     * common resource exception.
     *
     * @param unitConfig persistence-unit configuration
     * @return descriptors in bootstrap execution order
     */
    private static List<JdbcBootstrapResource.Descriptor> bootstrapDescriptors(Config unitConfig) {
        List<JdbcBootstrapResource.Descriptor> descriptors = new ArrayList<>(2);
        addBootstrapDescriptor(unitConfig.get(DROP_SCRIPT_CONFIG_KEY),
                               JdbcBootstrapResource.Role.DROP,
                               descriptors);
        addBootstrapDescriptor(unitConfig.get(INIT_SCRIPT_CONFIG_KEY),
                               JdbcBootstrapResource.Role.INIT,
                               descriptors);
        return List.copyOf(descriptors);
    }

    /**
     * Adds one descriptor without reading a configured location or content.
     *
     * @param scriptConfig script configuration node
     * @param role bootstrap role
     * @param descriptors target descriptors
     */
    private static void addBootstrapDescriptor(Config scriptConfig,
                                               JdbcBootstrapResource.Role role,
                                               List<JdbcBootstrapResource.Descriptor> descriptors) {
        if (!scriptConfig.exists()) {
            return;
        }
        descriptors.add(new JdbcBootstrapResource.Descriptor(role,
                                                              configuredSourceType(scriptConfig),
                                                              descriptors.size() + 1));
    }

    /**
     * Resolves the source category using the same precedence as
     * {@link Resource#create(io.helidon.common.configurable.ResourceConfig)}.
     *
     * @param scriptConfig script configuration node
     * @return configured source category
     */
    private static JdbcBootstrapResource.SourceType configuredSourceType(Config scriptConfig) {
        if (scriptConfig.get("path").exists()) {
            return JdbcBootstrapResource.SourceType.FILE;
        }
        if (scriptConfig.get("resource-path").exists()) {
            return JdbcBootstrapResource.SourceType.CLASSPATH;
        }
        if (scriptConfig.get("uri").exists()) {
            return JdbcBootstrapResource.SourceType.URI;
        }
        if (scriptConfig.get(CONTENT_PLAIN_CONFIG_KEY).exists()) {
            return JdbcBootstrapResource.SourceType.CONFIGURED_TEXT;
        }
        if (scriptConfig.get(CONTENT_CONFIG_KEY).exists()) {
            return JdbcBootstrapResource.SourceType.CONFIGURED_BINARY;
        }
        return JdbcBootstrapResource.SourceType.UNSPECIFIED;
    }

    /**
     * Creates a safe diagnostic for inline content that exceeds its effective
     * byte limit.
     *
     * @param unitName persistence unit name
     * @param descriptor safe bootstrap resource descriptor
     * @param budget current bootstrap budget
     * @return inline content size failure
     */
    private static DataException inlineSizeFailure(String unitName,
                                                   JdbcBootstrapResource.Descriptor descriptor,
                                                   JdbcScriptRunner.BootstrapBudget budget) {
        String limitDescription = budget.remainingBytes() < budget.maxResourceBytes()
                ? "aggregate bootstrap byte limit of " + budget.maxTotalBytes()
                : "per resource bootstrap byte limit of " + budget.maxResourceBytes();
        return new DataException(persistenceUnitDescription(unitName) + " cannot load the configured "
                                         + descriptor.role().text() + " script because its inline content exceeds the "
                                         + limitDescription + ".");
    }

    /**
     * Creates a safe diagnostic for invalid inline Base64 content.
     *
     * @param unitName persistence unit name
     * @param descriptor safe bootstrap resource descriptor
     * @return invalid Base64 failure
     */
    private static DataException invalidBase64Failure(String unitName,
                                                      JdbcBootstrapResource.Descriptor descriptor) {
        return new DataException(persistenceUnitDescription(unitName)
                                         + " cannot load the configured "
                                         + descriptor.role().text()
                                         + " script because it contains invalid Base64 content.");
    }

    /**
     * Resolves one unit's datasource and creates its client.
     *
     * @param configuredUnit persistence-unit configuration and detached scripts
     * @return configured client
     */
    private JdbcClient createClient(ConfiguredUnit configuredUnit) {
        JdbcPersistenceUnitConfig unit = configuredUnit.config();
        DataSource dataSource = connectionSource(unit);
        JdbcScriptRunner.execute(unit.name(), dataSource, configuredUnit.scripts());
        return new JdbcClientImpl(dataSource, connectionManager, configuredUnit.cachePolicy());
    }

    /**
     * Validates and resolves exactly one connection source.
     * <p>
     * Cardinality is validated before resolving either source so invalid
     * configuration cannot activate a datasource service or load a JDBC
     * driver as a side effect.
     *
     * @param unit persistence-unit configuration
     * @return configured datasource
     */
    private DataSource connectionSource(JdbcPersistenceUnitConfig unit) {
        boolean hasDataSource = unit.dataSource().isPresent();
        boolean hasConnection = unit.connection().isPresent();
        if (hasDataSource == hasConnection) {
            throw new DataException(persistenceUnitDescription(unit.name())
                                            + " must specify exactly one connection source. Configure either "
                                            + "'data-source' or 'connection'.");
        }
        if (hasDataSource) {
            String name = unit.dataSource().orElseThrow();
            if (name.isBlank()) {
                throw new DataException(persistenceUnitDescription(unit.name())
                                                + " has a blank 'data-source' name.");
            }
            return namedDataSource(unit.name(), name);
        }
        return directDataSource(unit.name(), unit.connection().orElseThrow());
    }

    /**
     * Resolves exactly one registry datasource by name.
     *
     * @param unitName persistence-unit name
     * @param name datasource service name
     * @return matching datasource
     */
    private DataSource namedDataSource(String unitName, String name) {
        Qualifier named = Qualifier.createNamed(name);
        List<ServiceInstance<DataSource>> matches;
        try {
            matches = dataSources.get()
                    .stream()
                    .filter(instance -> instance.qualifiers().contains(named))
                    .toList();
        } catch (RuntimeException failure) {
            throw dataSourceResolutionFailure(unitName, name, failure);
        }
        if (matches.isEmpty()) {
            throw new DataException("No SQL datasource service is named '" + name + "'.");
        }
        if (matches.size() > 1) {
            throw new DataException("More than one SQL datasource service is named '" + name + "'.");
        }
        try {
            return matches.getFirst().get();
        } catch (RuntimeException failure) {
            throw dataSourceResolutionFailure(unitName, name, failure);
        }
    }

    private static DataException dataSourceResolutionFailure(String unitName,
                                                             String dataSourceName,
                                                             RuntimeException cause) {
        return new DataException(persistenceUnitDescription(unitName) + " could not resolve SQL datasource service '"
                                         + dataSourceName + "'.",
                                 JdbcExceptionTranslator.sanitize("resolving a SQL datasource service", cause));
    }

    /**
     * Adapts the existing direct connection configuration to a datasource.
     *
     * @param unitName persistence-unit name
     * @param config direct connection configuration
     * @return datasource adapter
     */
    private static DataSource directDataSource(String unitName, ConnectionConfig config) {
        Driver driver;
        try {
            driver = SqlDriver.create(config).driver();
        } catch (RuntimeException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new DataException(persistenceUnitDescription(unitName)
                                            + " could not resolve a JDBC driver for its direct connection.",
                                    JdbcExceptionTranslator.sanitize("resolving a JDBC driver", cause));
        }
        return new DirectDataSource(Objects.requireNonNull(config, "The connection configuration must not be null."),
                                    Objects.requireNonNull(driver, "The JDBC driver must not be null."));
    }

    /**
     * Raw persistence unit configuration paired with validated provider policy.
     *
     * @param config raw persistence unit configuration
     * @param name persistence unit name
     * @param descriptors safe bootstrap resource descriptors
     * @param policy validated provider policy
     */
    private record ValidatedUnit(Config config,
                                 String name,
                                 List<JdbcBootstrapResource.Descriptor> descriptors,
                                 JdbcProviderPropertiesSupport.Policy policy) {
    }

    /**
     * Persistence unit configuration paired with detached scripts and cache policy.
     *
     * @param config persistence unit configuration
     * @param scripts preloaded scripts
     * @param cachePolicy parameter count cache policy
     */
    private record ConfiguredUnit(JdbcPersistenceUnitConfig config,
                                  JdbcScriptRunner.PreparedScripts scripts,
                                  JdbcClientImpl.CachePolicy cachePolicy) {
    }

    /**
     * Minimal DataSource adaptation for the existing direct SQL connection configuration.
     */
    private static final class DirectDataSource implements DataSource, JdbcTransactionConnectionManager.IdentitySource {

        private final String url;
        private final Driver driver;

        // Copied for each connection so a driver cannot mutate the shared defaults.
        private final Properties defaults;

        // Equivalent direct configurations must share one transaction identity.
        private final DirectIdentity transactionIdentity;

        private volatile PrintWriter logWriter;

        /**
         * Creates a datasource over one driver and connection configuration.
         *
         * @param config connection configuration
         * @param driver selected driver
         */
        private DirectDataSource(ConnectionConfig config, Driver driver) {
            this.url = config.url();
            this.driver = driver;
            this.defaults = new Properties();
            String username = config.username().orElse(null);
            char[] passwordChars = config.password().map(value -> value.clone()).orElse(null);
            // JDBC driver properties require string credentials. Keep the cloned
            // character array for the immutable datasource identity.
            String password = passwordChars == null ? null : new String(passwordChars);
            if (username != null) {
                defaults.setProperty(USER_PROPERTY, username);
            }
            if (password != null) {
                defaults.setProperty(PASSWORD_PROPERTY, password);
            }
            this.transactionIdentity = new DirectIdentity(url,
                                                          driver.getClass().getName(),
                                                          username,
                                                          passwordChars);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connect(copy(defaults));
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties properties = copy(defaults);
            // Per-call credentials replace configured defaults. A null argument
            // removes the corresponding default.
            if (username == null) {
                properties.remove(USER_PROPERTY);
            } else {
                properties.setProperty(USER_PROPERTY, username);
            }
            if (password == null) {
                properties.remove(PASSWORD_PROPERTY);
            } else {
                properties.setProperty(PASSWORD_PROPERTY, password);
            }
            return connect(properties);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            if (seconds < 0) {
                throw new IllegalArgumentException("The login timeout must not be negative.");
            }
            if (seconds != 0) {
                throw new SQLFeatureNotSupportedException(
                        "The direct JDBC datasource does not support login timeouts.");
            }
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            try {
                return driver.getParentLogger();
            } catch (RuntimeException failure) {
                throw (RuntimeException) JdbcExceptionTranslator.sanitize("reading a JDBC driver parent logger",
                                                                           failure);
            }
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            Objects.requireNonNull(iface, "The unwrap type must not be null.");
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            if (iface.isInstance(driver)) {
                return iface.cast(driver);
            }
            throw new SQLException("The direct JDBC datasource cannot be unwrapped as '" + iface.getName() + "'.");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            Objects.requireNonNull(iface, "The wrapper type must not be null.");
            return iface.isInstance(this) || iface.isInstance(driver);
        }

        @Override
        public DirectIdentity transactionIdentity() {
            return transactionIdentity;
        }

        /**
         * Opens one physical connection and rejects a non-accepting driver.
         *
         * @param properties connection properties
         * @return opened connection
         * @throws SQLException when the driver cannot connect
         */
        private Connection connect(Properties properties) throws SQLException {
            Connection connection = JdbcExceptionTranslator.invoke("opening a direct JDBC connection",
                                                                   () -> driver.connect(url, properties));
            if (connection == null) {
                throw new SQLException("The JDBC driver does not accept the configured URL.");
            }
            return connection;
        }

        /**
         * Copies connection properties so a driver cannot mutate or retain the
         * adapter's shared defaults.
         *
         * @param source source properties
         * @return independent properties
         */
        private static Properties copy(Properties source) {
            Properties copy = new Properties();
            copy.putAll(source);
            return copy;
        }
    }

    /**
     * Immutable identity for equivalent direct datasource adapters.
     */
    private static final class DirectIdentity implements JdbcTransactionConnectionManager.StableIdentity {

        private final String url;
        private final String driverClass;
        private final String username;

        // The array is copied at construction so configuration changes cannot alter the identity.
        private final char[] password;

        /**
         * Creates a direct datasource identity.
         *
         * @param url JDBC URL
         * @param driverClass driver implementation name
         * @param username configured username
         * @param password configured password
         */
        private DirectIdentity(String url, String driverClass, String username, char[] password) {
            this.url = url;
            this.driverClass = driverClass;
            this.username = username;
            this.password = password == null ? null : password.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DirectIdentity that
                    && url.equals(that.url)
                    && driverClass.equals(that.driverClass)
                    && Objects.equals(username, that.username)
                    && Arrays.equals(password, that.password);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(url, driverClass, username);
            return 31 * result + Arrays.hashCode(password);
        }
    }
}
