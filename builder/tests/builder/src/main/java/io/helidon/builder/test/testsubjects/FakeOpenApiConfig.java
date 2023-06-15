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

package io.helidon.builder.test.testsubjects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.LITERAL;
import static java.util.regex.Pattern.compile;

public interface FakeOpenApiConfig {

    default String modelReader() {
        return null;
    }

    default String filter() {
        return null;
    }

    default boolean scanDisable() {
        return false;
    }

    default Pattern scanPackages() {
        return null;
    }

    default Pattern scanClasses() {
        return null;
    }

    default Pattern scanExcludePackages() {
        return null;
    }

    default Pattern scanExcludeClasses() {
        return null;
    }

    default Set<String> servers() {
        return new HashSet<>();
    }

    default Set<String> pathServers(String path) {
        return new HashSet<>();
    }

    default Set<String> operationServers(String operationId) {
        return new HashSet<>();
    }

    default boolean scanDependenciesDisable() {
        return false;
    }

    default Set<String> scanDependenciesJars() {
        return new HashSet<>();
    }

    default boolean arrayReferencesEnable() {
        return true;
    }

    default String customSchemaRegistryClass() {
        return null;
    }

    default boolean applicationPathDisable() {
        return false;
    }

    default boolean privatePropertiesEnable() {
        return true;
    }

    default String propertyNamingStrategy() {
        return "id";
    }

    default boolean sortedPropertiesEnable() {
        return false;
    }

    default Map<String, String> getSchemas() {
        return new HashMap<>();
    }

    // Here we extend this in SmallRye with some more configure options (mp.openapi.extensions)
    default String getOpenApiVersion() {
        return null;
    }

    default String getInfoTitle() {
        return null;
    }

    default String getInfoVersion() {
        return null;
    }

    default String getInfoDescription() {
        return null;
    }

    default String getInfoTermsOfService() {
        return null;
    }

    default String getInfoContactEmail() {
        return null;
    }

    default String getInfoContactName() {
        return null;
    }

    default String getInfoContactUrl() {
        return null;
    }

    default String getInfoLicenseName() {
        return null;
    }

    default String getInfoLicenseUrl() {
        return null;
    }

    default OperationIdStrategy getOperationIdStrategy() {
        return null;
    }

    default Optional<String[]> getDefaultProduces() {
        return Optional.empty();
    }

    default Optional<String[]> getDefaultConsumes() {
        return Optional.empty();
    }

    default Optional<Boolean> allowNakedPathParameter() {
        return Optional.empty();
    }

    default Set<String> getScanProfiles() {
        return new HashSet<>();
    }

    default Set<String> getScanExcludeProfiles() {
        return new HashSet<>();
    }

    default void doAllowNakedPathParameter() {
    }

    default Pattern patternOf(String configValue) {
        return patternOf(configValue, null);
    }

    default Pattern patternOf(String configValue, Set<String> buildIn) {
        Pattern pattern = null;

        if (configValue != null && (configValue.startsWith("^") || configValue.endsWith("$"))) {
            pattern = compile(configValue);
        } else {
            Set<String> literals = asCsvSet(configValue);
            if (buildIn != null && !buildIn.isEmpty()) {
                literals.addAll(buildIn);
            }
            if (literals.isEmpty()) {
                return compile("", LITERAL);
            } else {
                pattern = compile("(" + literals.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")");
            }
        }

        return pattern;
    }

    default Set<String> asCsvSet(String items) {
        Set<String> rval = new HashSet<>();
        if (items != null) {
            String[] split = items.split(",");
            for (String item : split) {
                rval.add(item.trim());
            }
        }
        return rval;
    }

    enum OperationIdStrategy {
        METHOD,
        CLASS_METHOD,
        PACKAGE_CLASS_METHOD
    }
}
