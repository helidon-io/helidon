/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.BUILDER_DESCRIPTION;
import static io.helidon.builder.codegen.Types.DEPRECATED;
import static io.helidon.builder.codegen.Types.OPTION_ACCESS;
import static io.helidon.builder.codegen.Types.OPTION_ALLOWED_VALUES;
import static io.helidon.builder.codegen.Types.OPTION_CONFIDENTIAL;
import static io.helidon.builder.codegen.Types.OPTION_CONFIGURED;
import static io.helidon.builder.codegen.Types.OPTION_DECORATOR;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_BOOLEAN;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_CODE;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_DOUBLE;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_INT;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_LONG;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_METHOD;
import static io.helidon.builder.codegen.Types.OPTION_PROVIDER;
import static io.helidon.builder.codegen.Types.OPTION_REDUNDANT;
import static io.helidon.builder.codegen.Types.OPTION_REQUIRED;
import static io.helidon.builder.codegen.Types.OPTION_SAME_GENERIC;
import static io.helidon.builder.codegen.Types.OPTION_SINGULAR;
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
                            Consumer<ContentBuilder<?>> defaultValue,
                            DeprecationData deprecationData,
                            List<Annotation> annotations,
                            TypeName decorator // support for decorating an option when setting it
) {

    static AnnotationDataOption create(TypeHandler handler, TypedElementInfo element) {
        Javadoc javadoc = javadoc(element);
        boolean configured = false;
        String configKey = null;
        boolean configMerge = false;
        AccessModifier accessModifier;
        boolean providerBased = false;
        TypeName providerType = null;
        boolean discoverServices = false;
        List<AllowedValue> allowedValues = null;
        boolean singular;
        String singularName;
        boolean equalityRedundant;
        boolean toStringRedundant;

        if (element.hasAnnotation(OPTION_CONFIGURED)) {
            Annotation annotation = element.annotation(OPTION_CONFIGURED);
            configured = true;
            configKey = annotation.stringValue()
                    .filter(Predicate.not(String::isBlank))
                    .orElseGet(() -> toConfigKey(handler.name()));
            configMerge = annotation.booleanValue("merge")
                    .orElse(false);
        }
        accessModifier = accessModifier(element);
        if (element.hasAnnotation(OPTION_PROVIDER)) {
            Annotation annotation = element.annotation(OPTION_PROVIDER);
            providerBased = true;
            providerType = annotation.stringValue().map(TypeName::create).orElseThrow();
            discoverServices = annotation.booleanValue("discoverServices").orElse(true);
        }
        if (element.hasAnnotation(OPTION_ALLOWED_VALUES)) {
            Annotation annotation = element.annotation(OPTION_ALLOWED_VALUES);
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
        if (element.hasAnnotation(OPTION_SINGULAR)) {
            singular = true;
            singularName = element.annotation(OPTION_SINGULAR)
                    .value()
                    .filter(Predicate.not(String::isBlank))
                    .orElseGet(() -> singularName(handler.name()));
        } else {
            singular = false;
            singularName = null;
        }
        if (element.hasAnnotation(OPTION_REDUNDANT)) {
            Annotation annotation = element.annotation(OPTION_REDUNDANT);
            equalityRedundant = annotation.booleanValue("equality").orElse(true);
            toStringRedundant = annotation.booleanValue("stringValue").orElse(true);
        } else {
            equalityRedundant = false;
            toStringRedundant = false;
        }

        var defaultValue = defaultValue(element,
                                        handler);

        TypeName genericType = handler.declaredType().genericTypeName();
        boolean validateNotNull = shouldValidateNotNull(defaultValue == null, genericType);

        boolean required = element.hasAnnotation(OPTION_REQUIRED);
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

        TypeName decorator = optionDecorator(element);

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
                                        element.hasAnnotation(OPTION_SAME_GENERIC),
                                        equalityRedundant,
                                        toStringRedundant,
                                        element.hasAnnotation(OPTION_CONFIDENTIAL),
                                        allowedValues,
                                        defaultValue,
                                        deprecationData,
                                        annotations,
                                        decorator);
    }

    private static TypeName optionDecorator(TypedElementInfo element) {
        if (element.hasAnnotation(OPTION_DECORATOR)) {
            return element.annotation(OPTION_DECORATOR).typeValue()
                    .orElseThrow(() -> new IllegalStateException("There is no value defined on "
                                                                         + OPTION_DECORATOR.fqName()
                                                                         + " on element "
                                                                         + element
                                                                         + ", even though it is a required property."));
        }
        return null;
    }

    boolean hasDefault() {
        return defaultValue != null;
    }

    boolean hasAllowedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    private static AccessModifier accessModifier(TypedElementInfo element) {
        return element.findAnnotation(OPTION_ACCESS)
                .flatMap(Annotation::stringValue)
                .map(it -> it.isBlank() ? AccessModifier.PACKAGE_PRIVATE : AccessModifier.valueOf(it))
                .orElse(AccessModifier.PUBLIC);
    }

    private static Javadoc javadoc(TypedElementInfo element) {
        if (element.hasAnnotation(BUILDER_DESCRIPTION)) {
            return Javadoc.parse(element.annotation(BUILDER_DESCRIPTION).stringValue().orElseThrow());
        }
        return null;
    }

    private static Consumer<ContentBuilder<?>> defaultValue(TypedElementInfo element,
                                                            TypeHandler handler) {

        List<String> defaultValues = null;
        List<Integer> defaultInts = null;
        List<Long> defaultLongs = null;
        List<Double> defaultDoubles = null;
        List<Boolean> defaultBooleans = null;
        DefaultMethod defaultMethod = null;
        String defaultCode = null;
         /*
        Now all the defaults
         */
        if (element.hasAnnotation(OPTION_DEFAULT)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT);
            defaultValues = annotation.stringValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_INT)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_INT);
            defaultInts = annotation.intValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_LONG)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_LONG);
            defaultLongs = annotation.longValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_DOUBLE)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_DOUBLE);
            defaultDoubles = annotation.doubleValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_BOOLEAN)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_BOOLEAN);
            defaultBooleans = annotation.booleanValues().orElseGet(List::of);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_METHOD)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_METHOD);
            TypeName type = annotation.typeValue("type").orElse(OPTION_DEFAULT_METHOD);
            if (OPTION_DEFAULT_METHOD.equals(type)) {
                type = handler.declaredType();
            }
            String name = annotation.stringValue().orElseThrow();
            defaultMethod = new DefaultMethod(type, name);
        }
        if (element.hasAnnotation(OPTION_DEFAULT_CODE)) {
            defaultCode = element.annotation(OPTION_DEFAULT_CODE).stringValue().orElseThrow();
        }

        boolean noDefault = defaultValues == null
                && defaultInts == null
                && defaultLongs == null
                && defaultDoubles == null
                && defaultBooleans == null
                && defaultCode == null
                && defaultMethod == null;

        if (noDefault) {
            return null;
        } else {
            return handler.toDefaultValue(defaultValues,
                                          defaultInts,
                                          defaultLongs,
                                          defaultDoubles,
                                          defaultBooleans,
                                          defaultCode,
                                          defaultMethod);
        }

    }

    private static boolean shouldValidateNotNull(boolean noDefault, TypeName genericType) {
        return noDefault
                && !(
                genericType.equals(OPTIONAL)
                        || (genericType.primitive() && !genericType.array())
                        || genericType.equals(MAP)
                        || genericType.equals(SET)
                        || genericType.equals(LIST));
    }

    private static Javadoc processDeprecation(DeprecationData deprecationData, List<Annotation> annotations, Javadoc javadoc) {
        if (javadoc == null) {
            return null;
        }

        if (!deprecationData.deprecated()) {
            return javadoc;
        }

        io.helidon.common.types.Annotation.Builder deprecated = io.helidon.common.types.Annotation.builder()
                .typeName(DEPRECATED);
        if (deprecationData.since() != null) {
            deprecated.putValue("since", deprecationData.since());
        }
        if (deprecationData.forRemoval()) {
            deprecated.putValue("forRemoval", true);
        }

        if (Annotations.findFirst(DEPRECATED, annotations).isEmpty()) {
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
    result is kebab-case (such as max-initial-line-length).
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
