/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

final class ConfiguredType {
    private final Set<ConfiguredProperty> allProperties = new HashSet<>();
    private final List<ProducerMethod> producerMethods = new LinkedList<>();
    /*
     * The type that is built by a builder, or created using create method.
     */
    private final TypeName targetClass;
    /*
    The type we are processing that has @Configured annotation
     */
    private final TypeName annotatedClass;
    private final List<TypeName> inherited = new LinkedList<>();
    private final ConfiguredAnnotation configured;

    ConfiguredType(ConfiguredAnnotation configured, TypeName annotatedClass, TypeName targetClass, boolean typeDefinition) {
        this.annotatedClass = annotatedClass;
        this.targetClass = targetClass;
        this.configured = configured;
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
        return targetClass.fqName();
    }

    String annotatedClass() {
        return annotatedClass.fqName();
    }

    boolean standalone() {
        return configured.root();
    }

    String prefix() {
        return configured.prefix().orElse(null);
    }

    void write(List<Hson.Object> typeArray) {
        var typeObject = Hson.Object.builder();

        typeObject.set("type", targetClass());
        typeObject.set("annotatedType", annotatedClass());
        if (standalone()) {
            typeObject.set("standalone", true);
        }
        configured.prefix().ifPresent(it -> typeObject.set("prefix", it));
        configured.description().ifPresent(it -> typeObject.set("description", it));

        if (!inherited.isEmpty()) {
            typeObject.setStrings("inherits", inherited.stream()
                    .map(TypeName::fqName)
                    .toList());
        }

        if (!configured.provides().isEmpty()) {
            typeObject.setStrings("provides", configured.provides());
        }

        if (!producerMethods.isEmpty()) {
            typeObject.setStrings("producers", producerMethods.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        }

        List<Hson.Object> options = new ArrayList<>();
        for (ConfiguredProperty property : allProperties) {
            writeProperty(options, "", property);
        }
        typeObject.setObjects("options", options);

        typeArray.add(typeObject.build());
    }

    @Override
    public String toString() {
        return targetClass.fqName();
    }

    void addInherited(TypeName classOrIface) {
        inherited.add(classOrIface);
    }

    private static String paramsToString(List<TypeName> params) {
        return params.stream()
                .map(TypeName::resolvedName)
                .collect(Collectors.joining(", "));
    }

    private void writeProperty(List<Hson.Object> optionsBuilder,
                               String prefix,
                               ConfiguredProperty property) {

        var optionBuilder = Hson.Object.builder();
        if (property.key() != null && !property.key.isBlank()) {
            optionBuilder.set("key", prefix(prefix, property.key()));
        }
        if (!"java.lang.String".equals(property.type)) {
            optionBuilder.set("type", property.type());
        }
        optionBuilder.set("description", property.description());
        if (property.defaultValue() != null) {
            optionBuilder.set("defaultValue", property.defaultValue());
        }
        if (property.experimental) {
            optionBuilder.set("experimental", true);
        }
        if (!property.optional) {
            optionBuilder.set("required", true);
        }
        if (!property.kind().equals("VALUE")) {
            optionBuilder.set("kind", property.kind());
        }
        if (property.provider) {
            optionBuilder.set("provider", true);
            optionBuilder.set("providerType", property.providerType.fqName());
        }
        if (property.deprecated()) {
            optionBuilder.set("deprecated", true);
        }
        if (property.merge()) {
            optionBuilder.set("merge", true);
        }
        String method = property.builderMethod();
        if (method != null) {
            optionBuilder.set("method", method);
        }
        if (property.configuredType != null) {
            String finalPrefix;
            if (property.kind().equals("LIST")) {
                finalPrefix = prefix(prefix(prefix, property.key()), "*");
            } else {
                finalPrefix = prefix(prefix, property.key());
            }
            property.configuredType.properties()
                    .forEach(it -> writeProperty(optionsBuilder, finalPrefix, it));
        }
        if (!property.allowedValues.isEmpty()) {
            List<Hson.Object> allowedValues = new ArrayList<>();

            for (ConfiguredOptionData.AllowedValue allowedValue : property.allowedValues) {
                allowedValues.add(Hson.Object.builder()
                                          .set("value", allowedValue.value())
                                          .set("description", allowedValue.description())
                                          .build());
            }

            optionBuilder.setObjects("allowedValues", allowedValues);
        }

        optionsBuilder.add(optionBuilder.build());
    }

    private String prefix(String currentPrefix, String newSuffix) {
        if (currentPrefix.isEmpty()) {
            return newSuffix;
        }
        return currentPrefix + "." + newSuffix;
    }

    static final class ProducerMethod {
        private final boolean isStatic;
        private final TypeName owningClass;
        private final String methodName;
        private final List<TypeName> methodParams;

        ProducerMethod(boolean isStatic, TypeName owningClass, String methodName, List<TypeName> methodParams) {
            this.isStatic = isStatic;
            this.owningClass = owningClass;
            this.methodName = methodName;
            this.methodParams = methodParams;
        }

        @Override
        public String toString() {
            return owningClass.fqName()
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
        private final String kind;
        private final boolean provider;
        private final TypeName providerType;
        private final boolean deprecated;
        private final boolean merge;
        private final List<ConfiguredOptionData.AllowedValue> allowedValues;
        // if this is a nested type
        private ConfiguredType configuredType;

        ConfiguredProperty(String builderMethod,
                           String key,
                           String description,
                           String defaultValue,
                           TypeName type,
                           boolean experimental,
                           boolean optional,
                           String kind,
                           boolean provider,
                           TypeName providerType,
                           boolean deprecated,
                           boolean merge,
                           List<ConfiguredOptionData.AllowedValue> allowedValues) {
            this.builderMethod = builderMethod;
            this.key = key;
            this.description = description;
            this.defaultValue = defaultValue;
            this.type = type.fqName();
            this.experimental = experimental;
            this.optional = optional;
            this.kind = kind;
            this.provider = provider;
            this.providerType = providerType == null ? type : providerType;
            this.deprecated = deprecated;
            this.merge = merge;
            this.allowedValues = allowedValues;
        }

        String builderMethod() {
            return builderMethod;
        }

        String key() {
            return key;
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

        String kind() {
            return kind;
        }

        boolean deprecated() {
            return deprecated;
        }

        boolean merge() {
            return merge;
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
    }
}
