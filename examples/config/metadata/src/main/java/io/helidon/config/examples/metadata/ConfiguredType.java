/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.examples.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.config.metadata.ConfiguredOption;

final class ConfiguredType {
    private final Set<ConfiguredProperty> allProperties = new HashSet<>();
    private final List<ProducerMethod> producerMethods = new LinkedList<>();
    /**
     * The type that is built by a builder, or created using create method.
     */
    private final String targetClass;
    private final boolean standalone;
    private final String prefix;
    private final String description;
    private final List<String> provides;
    private final List<String> inherited = new LinkedList<>();

    ConfiguredType(String targetClass, boolean standalone, String prefix, String description, List<String> provides) {
        this.targetClass = targetClass;
        this.standalone = standalone;
        this.prefix = prefix;
        this.description = description;
        this.provides = provides;
    }

    private static String paramsToString(String[] params) {
        String result = Arrays.toString(params);
        if (result.startsWith("[") && result.endsWith("]")) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    String description() {
        return description;
    }

    static ConfiguredType create(JsonObject type) {
        ConfiguredType ct = new ConfiguredType(
                type.getString("type"),
                type.getBoolean("standalone", false),
                type.getString("prefix", null),
                type.getString("description", null),
                toList(type.getJsonArray("provides"))
        );

        List<String> producers = toList(type.getJsonArray("producers"));
        for (String producer : producers) {
            ct.addProducer(ProducerMethod.parse(producer));
        }
        List<String> inherits = toList(type.getJsonArray("inherits"));
        for (String inherit : inherits) {
            ct.addInherited(inherit);
        }

        JsonArray options = type.getJsonArray("options");
        for (JsonValue option : options) {
            ct.addProperty(ConfiguredProperty.create(option.asJsonObject()));
        }

        return ct;
    }

    private static List<String> toList(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());

        for (JsonValue jsonValue : array) {
            result.add(((JsonString) jsonValue).getString());
        }

        return result;
    }

    ConfiguredType addProducer(ProducerMethod producer) {
        producerMethods.add(producer);
        return this;
    }

    ConfiguredType addProperty(ConfiguredProperty property) {
        allProperties.add(property);
        return this;
    }

    List<ProducerMethod> producers() {
        return producerMethods;
    }

    Set<ConfiguredProperty> properties() {
        return allProperties;
    }

    String targetClass() {
        return targetClass;
    }

    boolean standalone() {
        return standalone;
    }

    String prefix() {
        return prefix;
    }

    List<String> provides() {
        return provides;
    }

    @Override
    public String toString() {
        return targetClass;
    }

    void addInherited(String classOrIface) {
        inherited.add(classOrIface);
    }

    List<String> inherited() {
        return inherited;
    }

    static final class ProducerMethod {
        private final boolean isStatic;
        private final String owningClass;
        private final String methodName;
        private final String[] methodParams;

        ProducerMethod(boolean isStatic, String owningClass, String methodName, String[] methodParams) {
            this.isStatic = isStatic;
            this.owningClass = owningClass;
            this.methodName = methodName;
            this.methodParams = methodParams;
        }

        public static ProducerMethod parse(String producer) {
            int methodSeparator = producer.indexOf('#');
            String owningClass = producer.substring(0, methodSeparator);
            int paramBraceStart = producer.indexOf('(', methodSeparator);
            String methodName = producer.substring(methodSeparator + 1, paramBraceStart);
            int paramBraceEnd = producer.indexOf(')', paramBraceStart);
            String parameters = producer.substring(paramBraceStart + 1, paramBraceEnd);
            String[] methodParams = parameters.split(",");

            return new ProducerMethod(false,
                                      owningClass,
                                      methodName,
                                      methodParams);
        }

        @Override
        public String toString() {
            return owningClass
                    + "#"
                    + methodName + "("
                    + paramsToString(methodParams) + ")";
        }
    }

    static final class ConfiguredProperty {
        private final String builderMethod;
        private final String key;
        private final String description;
        private final String defaultValue;
        private final String type;
        private final boolean experimental;
        private final boolean optional;
        private final ConfiguredOption.Kind kind;
        private final List<AllowedValue> allowedValues;
        private final boolean provider;

        // if this is a nested type
        private ConfiguredType configuredType;
        private String outputKey;

        ConfiguredProperty(String builderMethod,
                           String key,
                           String description,
                           String defaultValue,
                           String type,
                           boolean experimental,
                           boolean optional,
                           ConfiguredOption.Kind kind,
                           boolean provider,
                           List<AllowedValue> allowedValues) {
            this.builderMethod = builderMethod;
            this.key = key;
            this.description = description;
            this.defaultValue = defaultValue;
            this.type = type;
            this.experimental = experimental;
            this.optional = optional;
            this.kind = kind;
            this.allowedValues = allowedValues;
            this.outputKey = key;
            this.provider = provider;
        }

        public static ConfiguredProperty create(JsonObject json) {
            return new ConfiguredProperty(
                    json.getString("method", null),
                    json.getString("key"),
                    json.getString("description"),
                    json.getString("defaultValue", null),
                    json.getString("type", "java.lang.String"),
                    json.getBoolean("experimental", false),
                    json.getBoolean("optional", true),
                    toKind(json.getString("kind", null)),
                    json.getBoolean("provider", false),
                    toAllowedValues(json.getJsonArray("allowedValues"))
            );
        }

        private static ConfiguredOption.Kind toKind(String kind) {
            if (kind == null) {
                return ConfiguredOption.Kind.VALUE;
            }
            return ConfiguredOption.Kind.valueOf(kind);
        }

        List<AllowedValue> allowedValues() {
            return allowedValues;
        }

        private static List<AllowedValue> toAllowedValues(JsonArray allowedValues) {
            if (allowedValues == null) {
                return List.of();
            }
            List<AllowedValue> result = new ArrayList<>(allowedValues.size());

            for (JsonValue allowedValue : allowedValues) {
                JsonObject json = allowedValue.asJsonObject();
                result.add(new AllowedValue(json.getString("value"), json.getString("description", null)));
            }

            return result;
        }

        String builderMethod() {
            return builderMethod;
        }

        String outputKey() {
            return outputKey;
        }

        String key() {
            return key;
        }

        void key(String key) {
            this.outputKey = key;
        }

        String description() {
            return description;
        }

        String defaultValue() {
            return defaultValue;
        }

        String type() {
            return type;
        }

        boolean experimental() {
            return experimental;
        }

        boolean optional() {
            return optional;
        }

        ConfiguredOption.Kind kind() {
            return kind;
        }

        void nestedType(ConfiguredType nested) {
            this.configuredType = nested;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfiguredProperty that = (ConfiguredProperty) o;
            return key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }

        @Override
        public String toString() {
            return key;
        }

        boolean provider() {
            return provider;
        }
    }

    static final class AllowedValue {
        private final String value;
        private final String description;

        private AllowedValue(String value, String description) {
            this.value = value;
            this.description = description;
        }

        String value() {
            return value;
        }

        String description() {
            return description;
        }
    }
}
