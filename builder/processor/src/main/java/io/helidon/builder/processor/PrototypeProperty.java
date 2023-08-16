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

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.common.processor.classmodel.Field;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.processor.Types.CONFIDENTIAL_TYPE;
import static io.helidon.builder.processor.Types.CONFIGURED_OPTION_TYPE;
import static io.helidon.builder.processor.Types.PROTOTYPE_SAME_GENERIC_TYPE;
import static io.helidon.builder.processor.Types.REDUNDANT_TYPE;
import static io.helidon.common.processor.GeneratorTools.capitalize;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.SET;

// builder property
record PrototypeProperty(MethodSignature signature,
                         TypeHandler typeHandler,
                         ConfiguredOption configuredOption,
                         Singular singular,
                         FactoryMethods factoryMethods,
                         boolean equality, // part of equals and hash code
                         boolean toStringValue, // part of toString
                         boolean confidential // if part of toString, do not print the actual value
) {
    // cannot be identifiers - such as field name or method name
    private static final Set<String> RESERVED_WORDS = Set.of(
            "abstract", "assert", "boolean", "break",
            "byte", "case", "catch", "char",
            "class", "const", "continue", "default",
            "do", "double", "else", "enum",
            "extends", "final", "finally", "float",
            "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface",
            "long", "native", "new", "package",
            "private", "protected", "public", "return",
            "short", "static", "super", "switch",
            "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile",
            "while", "true", "false", "null"
    );

    static PrototypeProperty create(ProcessingContext processingContext,
                                    TypeInfo blueprint,
                                    TypedElementInfo element,
                                    boolean beanStyleAccessors) {

        boolean isBoolean = element.typeName().boxed().equals(Types.BOXED_BOOLEAN_TYPE);
        String getterName = element.elementName(); // this is always correct
        String name = propertyName(getterName,
                                   isBoolean,
                                   beanStyleAccessors); // name of the property, such as "withDefault", "optional", "list", "map"
        String setterName = setterName(name, beanStyleAccessors);
        if (RESERVED_WORDS.contains(name)) {
            name = "the" + capitalize(name);
        }

        TypeName returnType = element.typeName(); // real return type (String, Optional<String>, List<String>, Map<String, Type>

        boolean sameGeneric = element.hasAnnotation(PROTOTYPE_SAME_GENERIC_TYPE);
        // to help with defaults, setters, config mapping etc.
        TypeHandler typeHandler = TypeHandler.create(name, getterName, setterName, returnType, sameGeneric);

        // all information from @ConfiguredOption annotation
        ConfiguredOption configuredOption = ConfiguredOption.create(typeHandler, element); // from @ConfiguredOption
        Singular singular = Singular.create(name, element);
        FactoryMethods factoryMethods = FactoryMethods.create(processingContext,
                                                              blueprint,
                                                              typeHandler);

        boolean confidential = element.hasAnnotation(CONFIDENTIAL_TYPE);

        Optional<Annotation> redundantAnnotation = element.findAnnotation(REDUNDANT_TYPE);
        boolean toStringValue = !redundantAnnotation.flatMap(it -> it.getValue("stringValue"))
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean equality = !redundantAnnotation.flatMap(it -> it.getValue("equality"))
                .map(Boolean::parseBoolean)
                .orElse(false);

        return new PrototypeProperty(
                MethodSignature.create(element),
                typeHandler,
                configuredOption,
                singular,
                factoryMethods,
                equality,
                toStringValue,
                confidential
        );
    }

    void setters(InnerClass.Builder classBuilder, TypeName builderType, Javadoc blueprintJavadoc) {
        typeHandler().setters(classBuilder,
                              configuredOption(),
                              singular(),
                              factoryMethods(),
                              builderType,
                              blueprintJavadoc);
    }

    String name() {
        return typeHandler.name();
    }

    String getterName() {
        return typeHandler.getterName();
    }

    String setterName() {
        return typeHandler.setterName();
    }

    TypeName typeName() {
        return typeHandler.declaredType();
    }

    TypeName builderGetterType() {
        return typeHandler.builderGetterType(configuredOption.required(),
                                             configuredOption.hasDefault());
    }
    String builderGetter() {
        return typeHandler.generateBuilderGetter(configuredOption.required(),
                                                 configuredOption.hasDefault());
    }

    boolean builderGetterOptional() {
        return typeHandler.builderGetterOptional(configuredOption.required(),
                                                 configuredOption.hasDefault());
    }

    public Field.Builder fieldDeclaration(boolean isBuilder) {
        return typeHandler.fieldDeclaration(configuredOption(), isBuilder, !isBuilder);
    }

    private static String setterName(String name, boolean beanStyleAccessors) {
        if (beanStyleAccessors || RESERVED_WORDS.contains(name)) {
            return "set" + capitalize(name);
        }

        return name;
    }

    private static String propertyName(String getterName, boolean isBoolean, boolean beanStyleAccessors) {
        if (beanStyleAccessors) {
            if (isBoolean) {
                if (getterName.startsWith("is")) {
                    return deCapitalize(getterName.substring(2));
                }
            }
            if (getterName.startsWith("get")) {
                return deCapitalize(getterName.substring(3));
            }
        }
        return getterName;
    }

    private static String deCapitalize(String string) {
        if (string.isBlank()) {
            return string;
        }
        return Character.toLowerCase(string.charAt(0)) + string.substring(1);
    }

    /*
    Method name is camel case (such as maxInitialLineLength)
    result is dash separated and lower cased (such as max-initial-line-length).
    Note that this same method was created in ConfigUtils in common-config, but since this
    module should not have any dependencies in it a copy was left here as well.
    */
    private static String toKey(String propertyName) {

        StringBuilder result = new StringBuilder(propertyName.length() + 5);

        char[] chars = propertyName.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.length() == 0) {
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

    record ProviderOption(TypeName serviceProviderInterface,
                          boolean defaultDiscoverServices) {
        public static ProviderOption create(Annotation configuredAnnotation) {
            TypeName providerType = configuredAnnotation.getValue("providerType").map(TypeName::create)
                    .filter(Predicate.not(CONFIGURED_OPTION_TYPE::equals))
                    .orElseThrow(() -> new IllegalArgumentException("When a @ConfiguredOption has provided=true, "
                                                                            + "the providerType must be specified."));
            boolean defaultDiscoverServices = configuredAnnotation.getValue("providerDiscoverServices")
                    .map(Boolean::parseBoolean)
                    .orElse(true);
            return new ProviderOption(providerType, defaultDiscoverServices);
        }
    }

    record ConfiguredOption(String configKey,
                            Javadoc description,
                            boolean required,
                            boolean validateNotNull,
                            String defaultValue,
                            boolean builderMethod,
                            boolean notConfigured,
                            boolean provider,
                            ProviderOption providerOption) {

        static ConfiguredOption create(TypeHandler typeHandler, TypedElementInfo element) {
            ConfiguredOption configuredOption = element.findAnnotation(CONFIGURED_OPTION_TYPE)
                    .map(configuredAnnotation -> {
                        return configuredFromAnnotation(typeHandler, element, configuredAnnotation);
                    })
                    .orElseGet(() -> configuredNoAnnotation(typeHandler, element));

            TypeName genericType = typeHandler.declaredType().genericTypeName();

            if (genericType.primitive() && !genericType.array()) {
                // if required is only defined by the configured option annotation
                return configuredOption;
            }

            if (!configuredOption.hasDefault() && !(
                    genericType.equals(OPTIONAL)
                            || genericType.equals(MAP)
                            || genericType.equals(SET)
                            || genericType.equals(LIST))) {
                // no default, not optional, not a collection - MUST be required
                return configuredOption.withValidateNotNull();
            }
            // default/is required only based on annotations
            return configuredOption;
        }

        boolean hasDefault() {
            return defaultValue != null;
        }

        ConfiguredOption withValidateNotNull() {
            return new ConfiguredOption(configKey,
                                        description,
                                        required,
                                        true,
                                        defaultValue,
                                        builderMethod,
                                        notConfigured,
                                        provider,
                                        providerOption);
        }

        private static ConfiguredOption configuredNoAnnotation(TypeHandler typeHandler, TypedElementInfo element) {
            return new ConfiguredOption(null,
                                        element.description()
                                                .map(Javadoc::parse)
                                                .orElseGet(() -> Javadoc.builder()
                                                        .addLine("Option " + typeHandler.name())
                                                        .returnDescription(typeHandler.name())
                                                        .build()),
                                        false,
                                        false,
                                        null,
                                        true,
                                        true,
                                        false,
                                        null);
        }

        private static ConfiguredOption configuredFromAnnotation(TypeHandler typeHandler,
                                                                 TypedElementInfo element,
                                                                 Annotation configuredAnnotation) {
            boolean required = configuredAnnotation.getValue("required").map(Boolean::parseBoolean).orElse(false);
            boolean provider = configuredAnnotation.getValue("provider").map(Boolean::parseBoolean).orElse(false);
            return new ConfiguredOption(
                    toConfigKey(configuredAnnotation, typeHandler.name()),
                    configuredAnnotation.getValue("description")
                            .filter(Predicate.not(String::isBlank))
                            .or(element::description)
                            .map(Javadoc::parse)
                            .orElseGet(() -> Javadoc.builder()
                                    .addLine("Option " + typeHandler.name())
                                    .returnDescription(typeHandler.name())
                                    .build()),
                    required,
                    required,
                    configuredAnnotation.value()
                            .filter(Predicate.not(TypeHandler.UNCONFIGURED::equals))
                            .map(typeHandler::toDefaultValue)
                            .orElse(null),
                    configuredAnnotation.getValue("builderMethod").map(Boolean::parseBoolean).orElse(true),
                    configuredAnnotation.getValue("notConfigured").map(Boolean::parseBoolean).orElse(false),
                    provider,
                    provider ? ProviderOption.create(configuredAnnotation) : null);
        }

        private static String toConfigKey(Annotation configuredOption, String propertyName) {
            String key = configuredOption.getValue("key").orElse(null);
            if (key == null || key.isBlank()) {
                return toKey(propertyName);
            }
            return key;
        }
    }

    record Singular(boolean hasSingular, String singularName) {
        static Singular empty() {
            return new Singular(false, null);
        }

        static Singular create(String name, TypedElementInfo element) {
            return element.findAnnotation(Types.SINGULAR_TYPE)
                    .map(it -> new Singular(
                            true,
                            it.value()
                                    .filter(Predicate.not(String::isBlank))
                                    .orElseGet(() -> name.endsWith("s") ? name.substring(0, name.length() - 1) : name)
                    ))
                    .orElseGet(Singular::empty);
        }
    }
}
