/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import io.smallrye.openapi.api.OpenApiConfig;

import static io.smallrye.openapi.api.constants.OpenApiConstants.NEVER_SCAN_CLASSES;
import static io.smallrye.openapi.api.constants.OpenApiConstants.NEVER_SCAN_PACKAGES;

/**
 * Helidon-specific implementation of the smallrye OpenApiConfig interface,
 * loadable from a Helidon {@link Config} object as well as individual items
 * settable programmatically.
 */
public class OpenAPIConfigImpl implements OpenApiConfig {

    private final String modelReader;
    private final String filter;
    private final Map<String, Set<String>> operationServers;
    private final Map<String, Set<String>> pathServers;
    private Boolean scanDisable;
    private final Set<String> scanPackages;
    private final Set<String>  scanClasses;
    private final Set<String>  scanExcludePackages;
    private final Set<String>  scanExcludeClasses;
    private final Set<String> servers;
    private final Boolean scanDependenciesDisable = Boolean.TRUE;
    private final Set<String> scanDependenciesJars = Collections.emptySet();
    private final String customSchemaRegistryClass;
    private final Boolean applicationPathDisable;
    private final Map<String, String> schemas;

    // Cannot be final because the interface we implement requires this to be settable after it is built.
    private Optional<Boolean> allowNakedPathParameter = Optional.empty();

    private OpenAPIConfigImpl(Builder builder) {
        modelReader = builder.modelReader;
        filter = builder.filter;
        operationServers = builder.operationServers;
        pathServers = builder.pathServers;
        servers = new HashSet<>(builder.servers);
        scanDisable = builder.scanDisable;
        scanPackages = builder.scanPackages;
        scanClasses = builder.scanClasses;
        scanExcludePackages = builder.scanExcludePackages;
        scanExcludeClasses = builder.scanExcludeClasses;
        customSchemaRegistryClass = builder.customSchemaRegistryClass;
        applicationPathDisable = builder.applicationPathDisable;
        schemas = Collections.unmodifiableMap(builder.schemas);
    }

    /**
     * Creates a new builder for composing an OpenAPI config instance.
     *
     * @return the new {@code Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String modelReader() {
        return modelReader;
    }

    @Override
    public String filter() {
        return filter;
    }

    @Override
    public boolean scanDisable() {
        return scanDisable;
    }

    @Override
    public Set<String> scanPackages() {
        return scanPackages;
    }

    @Override
    public Set<String> scanClasses() {
        return scanClasses;
    }

    @Override
    public Set<String> scanExcludePackages() {
        return scanExcludePackages;
    }

    @Override
    public Set<String> scanExcludeClasses() {
        return scanExcludeClasses;
    }

    @Override
    public Set<String> servers() {
        return servers;
    }

    @Override
    public Set<String> pathServers(String path) {
        return chooseEntry(pathServers, path);
    }

    @Override
    public Set<String> operationServers(String operationID) {
        return chooseEntry(operationServers, operationID);
    }

    @Override
    public boolean scanDependenciesDisable() {
        return scanDependenciesDisable;
    }

    @Override
    public Set<String> scanDependenciesJars() {
        return scanDependenciesJars;
    }

    @Override
    public String customSchemaRegistryClass() {
        return customSchemaRegistryClass;
    }

    @Override
    public boolean applicationPathDisable() {
        return applicationPathDisable;
    }

    @Override
    public Map<String, String> getSchemas() {
        return schemas;
    }

    @Override
    public Optional<Boolean> allowNakedPathParameter() {
        return allowNakedPathParameter;
    }

    @Override
    public boolean scanBeanValidation() {
        return true;
    }

    @Override
    public boolean arrayReferencesEnable() {
        return true;
    }

    @Override
    public boolean privatePropertiesEnable() {
        return true;
    }

    @Override
    public String propertyNamingStrategy() {
        return null;
    }

    @Override
    public boolean sortedPropertiesEnable() {
        return true;
    }

    @Override
    public String getOpenApiVersion() {
        return "3.0.3";
    }

    @Override
    public String getInfoTitle() {
        return null;
    }

    @Override
    public String getInfoVersion() {
        return null;
    }

    @Override
    public String getInfoDescription() {
        return null;
    }

    @Override
    public String getInfoTermsOfService() {
        return null;
    }

    @Override
    public String getInfoContactEmail() {
        return null;
    }

    @Override
    public String getInfoContactName() {
        return null;
    }

    @Override
    public String getInfoContactUrl() {
        return null;
    }

    @Override
    public String getInfoLicenseName() {
        return null;
    }

    @Override
    public String getInfoLicenseUrl() {
        return null;
    }

    @Override
    public OperationIdStrategy getOperationIdStrategy() {
        return null;
    }

    @Override
    public DuplicateOperationIdBehavior getDuplicateOperationIdBehavior() {
        return OpenApiConfig.DUPLICATE_OPERATION_ID_BEHAVIOR_DEFAULT;
    }

    @Override
    public Optional<String[]> getDefaultProduces() {
        return Optional.empty();
    }

    @Override
    public Optional<String[]> getDefaultConsumes() {
        return Optional.empty();
    }

    @Override
    public Set<String> getScanProfiles() {
        return Set.of();
    }

    @Override
    public Set<String> getScanExcludeProfiles() {
        return Set.of();
    }

    @Override
    public boolean removeUnusedSchemas() {
        return false;
    }

    private static <T, U> Set<U> chooseEntry(Map<T, Set<U>> map, T key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return Set.of();
    }

    /**
     * Fluent builder for {@link io.helidon.openapi.internal.OpenAPIConfigImpl}.
     * <p>
     * The caller can set values individually by invoking the method
     * corresponding to each value, or by passing a {@link Config} object with
     * keys as follows:
     * <table class="config">
     * <caption>Configuration for Setting OpenAPIConfig</caption>
     * <tr>
     * <th>Key</th>
     * </tr>
     * <tr><td>{@value MODEL_READER}</td></tr>
     * <tr><td>{@value FILTER}</td></tr>
     * <tr><td>{@value SERVERS}</td></tr>
     * <tr><td>{@value SERVERS_PATH}</td></tr>
     * <tr><td>{@value SERVERS_OPERATION}</td></tr>
     * </table>
     */
    @Configured()
    public static final class Builder implements io.helidon.common.Builder<Builder, OpenApiConfig> {

        /**
         * Config key prefix for schema overrides for specified classes.
         */
        public static final String SCHEMA = "schema";

        // Key names are inspired by the MP OpenAPI config key names
        static final String MODEL_READER = "model.reader";
        static final String FILTER = "filter";
        static final String SERVERS = "servers";
        static final String SERVERS_PATH = "servers.path";
        static final String SERVERS_OPERATION = "servers.operation";

        private static final Set<String> NEVER_SCAN_PACKAGES = Set.of("java.lang");
        private static final Set<String> NEVER_SCAN_CLASSES = Set.of();

        private String modelReader;
        private String filter;
        private final Map<String, Set<String>> operationServers = new HashMap<>();
        private final Map<String, Set<String>> pathServers = new HashMap<>();
        private final Set<String> servers = new HashSet<>();
        private boolean scanDisable = true;
        private Set<String> scanPackages = Set.of();
        private Set<String> scanClasses = Set.of();
        private Set<String> scanExcludePackages = NEVER_SCAN_PACKAGES;
        private Set<String> scanExcludeClasses = NEVER_SCAN_CLASSES;

        private String customSchemaRegistryClass;
        private boolean applicationPathDisable = false;
        private Map<String, String> schemas = new HashMap<>();
        private Builder() {
        }

        @Override
        public OpenApiConfig build() {
            return new OpenAPIConfigImpl(this);
        }

        /**
         * Sets the builder's attributes according to the corresponding entries
         * (if present) in the specified openapi {@link Config} object.
         *
         * @param config {@code} openapi Config object to process
         * @return updated builder
         */
        public Builder config(Config config) {
            stringFromConfig(config, MODEL_READER, this::modelReader);
            stringFromConfig(config, FILTER, this::filter);
            stringFromConfig(config, SERVERS, this::servers);
            listFromConfig(config, SERVERS_PATH, this::pathServers);
            listFromConfig(config, SERVERS_OPERATION, this::operationServers);
            mapFromConfig(config, SCHEMA, this::schemas);
            return this;
        }

        /**
         * Sets the developer-provided OpenAPI model reader class name.
         *
         * @param modelReader model reader class name
         * @return updated builder
         */
        @ConfiguredOption(key = MODEL_READER)
        public Builder modelReader(String modelReader) {
            this.modelReader = modelReader;
            return this;
        }

        /**
         * Sets the developer-provided OpenAPI filter class name.
         *
         * @param filter filter class name
         * @return updated builder
         */
        @ConfiguredOption
        public Builder filter(String filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets alternative servers to service the specified operation. Repeat for multiple operations.
         *
         * @param operationID operation ID
         * @param operationServers comma-separated list of servers for the given
         * operation
         * @return updated builder
         */
        @ConfiguredOption(key = SERVERS_OPERATION + ".*",
                          kind = ConfiguredOption.Kind.LIST,
                          description = """
                                  Sets alternative servers to service the indicated operation \
                                  (represented here by '*'). \
                                  Repeat for multiple operations.""",
                          type = String.class)
        public Builder operationServers(String operationID, String operationServers) {
            this.operationServers.clear();
            setEntry(this.operationServers, operationID, operationServers);
            return this;
        }

        /**
         * Adds an alternative server to service the specified operation.
         *
         * @param operationID operation ID for the server being added
         * @param operationServer the server being added
         * @return updated builder
         */
        public Builder addOperationServer(String operationID, String operationServer) {
            addToEntry(operationServers, operationID, operationServer);
            return this;
        }

        /**
         * Sets alternative servers to service all operations in the specified path. Repeat for multiple paths.
         *
         * @param path path for the servers being set
         * @param pathServers comma-list of servers for the given path
         * @return updated builder
         */
        @ConfiguredOption(key = SERVERS_PATH + ".*",
                          kind = ConfiguredOption.Kind.LIST,
                          description = """
                                  Sets alternative servers to service all operations at the indicated path \
                                  (represented here by '*'). \
                                  Repeat for multiple paths.""",
                          type = String.class)
        public Builder pathServers(String path, String pathServers) {
            setEntry(this.pathServers, path, pathServers);
            return this;
        }

        /**
         * Adds an alternative server for all operations in the specified path.
         *
         * @param path path for the server being added
         * @param pathServer the server being added
         * @return updated builder
         */
        public Builder addPathServer(String path, String pathServer) {
            addToEntry(pathServers, path, pathServer);
            return this;
        }

        /**
         * Sets servers.
         *
         * @param servers comma-list of servers
         * @return updated builder
         */
        @ConfiguredOption(kind = ConfiguredOption.Kind.LIST)
        public Builder servers(String servers) {
            this.servers.clear();
            this.servers.addAll(commaListToSet(servers));
            return this;
        }

        /**
         * Adds server.
         *
         * @param server server to be added
         * @return updated builder
         */
        public Builder addServer(String server) {
            servers.add(server);
            return this;
        }

        /**
         * Sets schemas for one or more classes referenced in the OpenAPI model.
         *
         * @param schemas map of FQ class name to JSON string depicting the schema
         * @return updated builder
         */
        @ConfiguredOption(key = SCHEMA + ".*",
                          description = """
                                   Sets the schema for the indicated fully-qualified class name (represented here by '*'); \
                                   value is the schema in JSON format. \
                                   Repeat for multiple classes. \
                                   """,
                          type = String.class)
        public Builder schemas(Map<String, String> schemas) {
            this.schemas = new HashMap<>(schemas);
            return this;
        }

        /**
         * Adds a schema for a class.
         *
         * @param fullyQualifiedClassName name of the class the schema describes
         * @param schema JSON text definition of the schema
         * @return updated builder
         */
        public Builder addSchema(String fullyQualifiedClassName, String schema) {
            schemas.put(fullyQualifiedClassName, schema);
            return this;
        }

        /**
         * Sets whether annotation scanning should be disabled.
         *
         * @param value new setting for annotation scanning disabled flag
         * @return updated builder
         */
        public Builder scanDisable(boolean value) {
            scanDisable = value;
            return this;
        }

        /**
         * Sets the custom schema registry class.
         *
         * @param className class to be assigned
         * @return updated builder
         */
        @ConfiguredOption
        public Builder customSchemaRegistryClass(String className) {
            customSchemaRegistryClass = className;
            return this;
        }

        /**
         * Sets whether the app path search should be disabled.
         *
         * @param value true/false
         * @return updated builder
         */
        @ConfiguredOption("false")
        public Builder applicationPathDisable(Boolean value) {
            applicationPathDisable = value;
            return this;
        }



        private static void stringFromConfig(Config config,
                                             String key,
                                             Function<String, Builder> assignment) {
            config.get(key).asString().ifPresent(assignment::apply);
        }

        private static void listFromConfig(Config config,
                String keyPrefix,
                BiFunction<String, String, Builder> assignment) {
            config.get(keyPrefix).asNodeList().ifPresent(cf -> cf.forEach(c -> {
                String key = c.key().name();
                String value = c.asString().get();
                assignment.apply(key, value);
            }));
        }

        private static void mapFromConfig(Config config,
                                          String keyPrefix,
                                          Function<Map<String, String>, Builder> assignment) {
            config.get(keyPrefix)
                    .detach()
                    .asMap()
                    .ifPresent(assignment::apply);
        }

        private static void booleanFromConfig(Config config,
                                              String key,
                                              Function<Boolean, Builder> assignment) {
            config.get(key).asBoolean().ifPresent(assignment::apply);
        }

        /**
         * Given a Map<T,Set<U>>, adds an entry to the set for a given key
         * value, creating the entry in the map if none already exists.
         *
         * @param <T> key type of the Map
         * @param <U> value type of the Map
         * @param map Map<T,U>
         * @param key key for which a value is to be added
         * @param value value to add to the Map entry for the given key
         */
        private static <T, U> void addToEntry(Map<T, Set<U>> map, T key, U value) {
            Set<U> result;
            if (map.containsKey(key)) {
                result = map.get(key);
            } else {
                result = new HashSet<>();
                map.put(key, result);
            }
            result.add(value);
        }

        /**
         * Sets the entry for a key in Map<T,String> by parsing a
         * comma-separated list of values.
         *
         * @param <T> type of the map's key
         * @param map Map<T,String>
         * @param key key value for which to assign its associated values
         * @param values comma-separated list of String values to convert to a
         * list
         */
        private static <T> void setEntry(
                Map<T, Set<String>> map,
                T key,
                String values) {
            Set<String> set = commaListToSet(values);
            map.put(key, set);
        }

        private static Set<String> commaListToSet(String items) {
            /*
             * Do not special-case an empty comma-list to an empty set because a
             * set created here might be added to later.
             */
            var result = new HashSet<String>();
            if (items != null) {
                for (String item : items.split(",")) {
                    result.add(item.trim());
                }
            }
            return result;
        }
    }
}
