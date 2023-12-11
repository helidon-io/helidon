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

package io.helidon.builder.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.processor.Types.CONFIGURED_OPTION_TYPE;
import static io.helidon.builder.processor.Types.DEPRECATED_TYPE;
import static io.helidon.builder.processor.Types.DESCRIPTION_TYPE;
import static io.helidon.builder.processor.Types.OPTION_ACCESS_TYPE;
import static io.helidon.builder.processor.Types.OPTION_ALLOWED_VALUES_TYPE;
import static io.helidon.builder.processor.Types.OPTION_CONFIDENTIAL_TYPE;
import static io.helidon.builder.processor.Types.OPTION_CONFIGURED_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_BOOLEAN_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_CODE_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_DOUBLE_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_INT_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_LONG_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_METHOD_TYPE;
import static io.helidon.builder.processor.Types.OPTION_DEFAULT_TYPE;
import static io.helidon.builder.processor.Types.OPTION_PROVIDER_TYPE;
import static io.helidon.builder.processor.Types.OPTION_REDUNDANT_TYPE;
import static io.helidon.builder.processor.Types.OPTION_REQUIRED_TYPE;
import static io.helidon.builder.processor.Types.OPTION_SAME_GENERIC_TYPE;
import static io.helidon.builder.processor.Types.OPTION_SINGULAR_TYPE;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.SET;

record AnnotationDataOption(Javadoc javadoc,
                            boolean configured,
                            String configKey,
                            Boolean configMerge,
                            AccessModifier accessModifier,
                            boolean required,
                            boolean validateNotNull,
                            boolean provider,
                            TypeName providerType,
                            boolean providerDiscoverServices,
                            boolean singular,
                            String singularName,
                            boolean sameGeneric,
                            boolean equalityRedundant,
                            boolean toStringRedundant,
                            boolean confidential,
                            List<AllowedValue> allowedValues,
                            String defaultValue,
                            DeprecationData deprecationData,
                            List<Annotation> annotations) {
    private static final String UNCONFIGURED = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    // this is temporary, when we stop supporting config metadata, we can refactor this method
    @SuppressWarnings("checkstyle:MethodLength")
    static AnnotationDataOption create(TypeHandler handler, TypedElementInfo element) {
        // now we need to support both config metadata and new approach, starting with new, the old can be later removed
        Javadoc javadoc = null;
        Boolean configured = null;
        String configKey = null;
        Boolean configMerge = null;
        AccessModifier accessModifier;
        Boolean required = null;
        Boolean providerBased = null;
        TypeName providerType = null;
        Boolean discoverServices = null;
        List<AllowedValue> allowedValues = null;
        boolean singular;
        String singularName;
        boolean sameGeneric;
        boolean equalityRedundant;
        boolean toStringRedundant;
        boolean confidential;
        List<String> defaultValues = null;
        List<Integer> defaultInts = null;
        List<Long> defaultLongs = null;
        List<Double> defaultDoubles = null;
        List<Boolean> defaultBooleans = null;
        DefaultMethod defaultMethod = null;
        String defaultCode = null;

        if (element.hasAnnotation(DESCRIPTION_TYPE)) {
            javadoc = Javadoc.parse(element.annotation(DESCRIPTION_TYPE).stringValue().orElseThrow());
        }
        if (element.hasAnnotation(OPTION_CONFIGURED_TYPE)) {
            Annotation annotation = element.annotation(OPTION_CONFIGURED_TYPE);
            configured = true;
            configKey = annotation.stringValue()
                    .filter(Predicate.not(String::isBlank))
                    .orElseGet(() -> toConfigKey(handler.name()));
            configMerge = annotation.booleanValue("merge")
                    .orElse(false);
        }
        accessModifier = element.findAnnotation(OPTION_ACCESS_TYPE)
                .flatMap(Annotation::stringValue)
                .map(it -> it.isBlank() ? AccessModifier.PACKAGE_PRIVATE : AccessModifier.valueOf(it))
                .orElse(AccessModifier.PUBLIC);
        if (element.hasAnnotation(OPTION_REQUIRED_TYPE)) {
            required = true;
        }
        if (element.hasAnnotation(OPTION_PROVIDER_TYPE)) {
            Annotation annotation = element.annotation(OPTION_PROVIDER_TYPE);
            providerBased = true;
            providerType = annotation.stringValue().map(TypeName::create).orElseThrow();
            discoverServices = annotation.booleanValue("discoverServices").orElse(true);
        }
        if (element.hasAnnotation(OPTION_ALLOWED_VALUES_TYPE)) {
            Annotation annotation = element.annotation(OPTION_ALLOWED_VALUES_TYPE);
            allowedValues = annotation.annotationValues()
                    .orElseGet(List::of)
                    .stream()
                    .map(it -> {
                        String value = it.stringValue().orElseThrow();
                        String description = it.stringValue("description").orElseThrow();
                        return new AllowedValue(value, description);
                    })
                    .toList();
        }
        if (element.hasAnnotation(OPTION_SINGULAR_TYPE)) {
            singular = true;
            singularName = element.annotation(OPTION_SINGULAR_TYPE)
                    .value()
                    .filter(Predicate.not(String::isBlank))
                    .orElseGet(() -> singularName(handler.name()));
        } else {
            singular = false;
            singularName = null;
        }
        sameGeneric = element.hasAnnotation(OPTION_SAME_GENERIC_TYPE);
        if (element.hasAnnotation(OPTION_REDUNDANT_TYPE)) {
            Annotation annotation = element.annotation(OPTION_REDUNDANT_TYPE);
            equalityRedundant = annotation.booleanValue("equality").orElse(true);
            toStringRedundant = annotation.booleanValue("stringValue").orElse(true);
        } else {
            equalityRedundant = false;
            toStringRedundant = false;
        }
        confidential = element.hasAnnotation(OPTION_CONFIDENTIAL_TYPE);
        /*
        Now all the defaults
         */
        if (element.hasAnnotation(OPTION_DEFAULT_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_TYPE);
            defaultValues = annotation.stringValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_INT_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_INT_TYPE);
            defaultInts = annotation.intValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_LONG_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_LONG_TYPE);
            defaultLongs = annotation.longValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_DOUBLE_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_DOUBLE_TYPE);
            defaultDoubles = annotation.doubleValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_BOOLEAN_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_BOOLEAN_TYPE);
            defaultBooleans = annotation.booleanValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_METHOD_TYPE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_METHOD_TYPE);
            TypeName type = annotation.typeValue("type").orElse(OPTION_DEFAULT_METHOD_TYPE);
            if (OPTION_DEFAULT_METHOD_TYPE.equals(type)) {
                type = handler.declaredType();
            }
            String name = annotation.stringValue().orElseThrow();
            defaultMethod = new DefaultMethod(type, name);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_CODE_TYPE)) {
            defaultCode = element.annotation(OPTION_DEFAULT_CODE_TYPE).stringValue().orElseThrow();
        }

        /*
        Section that supports io.helidon.config.metadata.ConfiguredOption etc.
         */
        if (element.hasAnnotation(CONFIGURED_OPTION_TYPE)) {
            // we do have a metadata annotation, let's use it (unless configured by Option annotation already)
            Annotation annotation = element.annotation(CONFIGURED_OPTION_TYPE);

            if (defaultValues == null && defaultBooleans == null && defaultInts == null
                    && defaultCode == null && defaultMethod == null && defaultLongs == null && defaultDoubles == null) {

                String defaultValue = annotation.stringValue()
                        .filter(Predicate.not(UNCONFIGURED::equals))
                        .orElse(null);
                if (defaultValue != null) {
                    if (handler.declaredType().isSet() || handler.declaredType().isList() || handler.declaredType().isMap()) {
                        defaultValues = List.of(defaultValue.split(","));
                    } else {
                        defaultValues = List.of(defaultValue);
                    }
                }
            }
            if (configured == null) {
                configured = !annotation.getValue("notConfigured").map(Boolean::parseBoolean).orElse(false);
                configKey = annotation.stringValue("key")
                        .filter(Predicate.not(String::isBlank))
                        .orElseGet(() -> toConfigKey(handler.name()));
                configMerge = annotation.booleanValue("mergeWithParent")
                        .orElse(false);
            }
            if (required == null) {
                required = annotation.getValue("required").map(Boolean::parseBoolean).orElse(false);
            }
            if (providerBased == null) {
                providerBased = annotation.booleanValue("provider").orElse(false);
                if (providerBased) {
                    providerType = annotation.typeValue("providerType")
                            .filter(Predicate.not(CONFIGURED_OPTION_TYPE::equals))
                            .orElseThrow(() -> new IllegalArgumentException("When a @ConfiguredOption has provided=true, "
                                                                                    + "the providerType must be specified."));
                }
                discoverServices = annotation.booleanValue("providerDiscoverServices")
                        .orElse(true);
            }
            if (allowedValues == null) {
                allowedValues = annotation.annotationValues("allowedValues")
                        .stream()
                        .flatMap(List::stream)
                        .map(it -> new AllowedValue(it.stringValue().orElseThrow(),
                                                    it.stringValue("description").orElseThrow()))
                        .toList();
            }
            if (javadoc == null) {
                javadoc = annotation.stringValue("description")
                        .filter(Predicate.not(String::isBlank))
                        .or(element::description)
                        .map(Javadoc::parse)
                        .orElseGet(() -> Javadoc.builder()
                                .addLine("Option " + handler.name())
                                .returnDescription(handler.name())
                                .build());
            }
        }
        /*
        End of section that supports io.helidon.config.metadata.ConfiguredOption etc.
         */

        String defaultValue = handler.toDefaultValue(defaultValues,
                                                     defaultInts,
                                                     defaultLongs,
                                                     defaultDoubles,
                                                     defaultBooleans,
                                                     defaultCode,
                                                     defaultMethod);

        TypeName genericType = handler.declaredType().genericTypeName();
        boolean validateNotNull = (defaultValue == null)
                && !(
                genericType.equals(OPTIONAL)
                        || (genericType.primitive() && !genericType.array())
                        || genericType.equals(MAP)
                        || genericType.equals(SET)
                        || genericType.equals(LIST));

        configured = configured != null && configured;
        required = required != null && required;
        providerBased = providerBased != null && providerBased;
        discoverServices = discoverServices == null || discoverServices;
        validateNotNull = validateNotNull || required;

        if (javadoc == null) {
            javadoc = element.description()
                    .map(Javadoc::parse)
                    .orElseGet(() -> Javadoc.builder()
                            .addLine("Option " + handler.name())
                            .returnDescription(handler.name())
                            .build());
        }

        DeprecationData deprecationData = DeprecationData.create(element, javadoc);

        List<Annotation> annotations = new ArrayList<>();
        javadoc = processDeprecation(deprecationData, annotations, javadoc);

        // default/is required only based on annotations
        return new AnnotationDataOption(javadoc,
                                        configured,
                                        configKey,
                                        configMerge,
                                        accessModifier,
                                        required,
                                        validateNotNull,
                                        providerBased,
                                        providerType,
                                        discoverServices,
                                        singular,
                                        singularName,
                                        sameGeneric,
                                        equalityRedundant,
                                        toStringRedundant,
                                        confidential,
                                        allowedValues,
                                        defaultValue,
                                        deprecationData,
                                        annotations);
    }

    boolean hasDefault() {
        return defaultValue != null;
    }

    boolean hasAllowedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    private static Javadoc processDeprecation(DeprecationData deprecationData, List<Annotation> annotations, Javadoc javadoc) {
        if (javadoc == null) {
            return null;
        }

        if (!deprecationData.deprecated()) {
            return javadoc;
        }

        io.helidon.common.types.Annotation.Builder deprecated = io.helidon.common.types.Annotation.builder()
                .typeName(DEPRECATED_TYPE);
        if (deprecationData.since() != null) {
            deprecated.putValue("since", deprecationData.since());
        }
        if (deprecationData.forRemoval()) {
            deprecated.putValue("forRemoval", true);
        }

        if (Annotations.findFirst(DEPRECATED_TYPE, annotations).isEmpty()) {
            annotations.add(deprecated.build());
        }

        if (deprecationData.alternativeOption() != null || deprecationData.description() != null) {
            Javadoc.Builder javadocBuilder = Javadoc.builder(javadoc);
            if (deprecationData.alternativeOption() == null) {
                javadocBuilder.deprecation(deprecationData.description());
            } else {
                javadocBuilder.deprecation("use {@link #" + deprecationData.alternativeOption() + "()} instead");
            }
            javadoc = javadocBuilder.build();
        }
        return javadoc;
    }

    private static String singularName(String optionName) {
        if (optionName.endsWith("s")) {
            return optionName.substring(0, optionName.length() - 1);
        }
        return optionName;
    }

    /*
    Method name is camel case (such as maxInitialLineLength)
    result is dash separated and lower cased (such as max-initial-line-length).
    Note that this same method was created in ConfigUtils in common-config, but since this
    module should not have any dependencies in it a copy was left here as well.
    */
    private static String toConfigKey(String propertyName) {
        StringBuilder result = new StringBuilder();

        char[] chars = propertyName.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.isEmpty()) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }

    record AllowedValue(String value, String description) {
    }

    record DefaultMethod(TypeName type, String method) {
    }

}
