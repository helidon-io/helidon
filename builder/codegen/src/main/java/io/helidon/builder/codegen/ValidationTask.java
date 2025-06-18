/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY;
import static io.helidon.builder.codegen.Types.RUNTIME_API;
import static io.helidon.builder.codegen.Types.RUNTIME_PROTOTYPED_BY;

abstract class ValidationTask {
    abstract void validate(Errors.Collector errors);

    private static void validateImplements(Errors.Collector errors,
                                           TypeInfo validatedType,
                                           TypeName implementedInterface,
                                           String message) {

        if (!doesImplement(validatedType, implementedInterface)) {
            errors.fatal(validatedType.typeName(), message);
        }
    }

    private static boolean doesImplement(TypeInfo validatedType, TypeName implementedInterface) {
        if (validatedType.interfaceTypeInfo()
                .stream()
                .anyMatch(it -> it.typeName().equals(implementedInterface))) {
            return true;
        }
        return validatedType.superTypeInfo()
                .map(it -> doesImplement(it, implementedInterface))
                .orElse(false);
    }

    private static void validateFactoryMethod(Errors.Collector errors,
                                              TypeInfo validatedType,
                                              TypeName returnType,
                                              String methodName,
                                              TypeName argument,
                                              String message) {
        if (validatedType.elementInfo().stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(ElementInfoPredicates.elementName(methodName))
                .filter(it -> returnType.equals(it.typeName()))
                .filter(it -> {
                    List<TypedElementInfo> args = it.parameterArguments();

                    if (argument == null) {
                        return args.isEmpty();
                    }
                    if (args.size() != 1) {
                        return false;
                    }
                    TypedElementInfo typedElementInfo = args.getFirst();
                    return argument.equals(typedElementInfo.typeName());
                })
                .findFirst()
                .isEmpty()) {
            errors.fatal(validatedType.typeName(), message);
        }
    }

    /**
     * Validate runtime object that is configured by a prototype.
     * <p>
     * If annotated by {@link io.helidon.builder.codegen.Types#RUNTIME_PROTOTYPE}
     * - RuntimeType must have "static RuntimeType create(ConfigObject)"
     * - RuntimeType must have "static RuntimeType create(Consumer<ConfigObject.Builder>)
     * - must implement {@link io.helidon.builder.codegen.Types#RUNTIME_API}
     */
    static class ValidateConfiguredType extends ValidationTask {
        private final TypeInfo runtimeTypeInfo;
        private final List<ValidationTask> nestedValidators;

        ValidateConfiguredType(TypeInfo runtimeTypeInfo, TypeName configObjectType) {
            this.runtimeTypeInfo = runtimeTypeInfo;

            // the type has to have same type parameters as its config bean
            TypeName configObjectWithTypeParams = TypeName.builder(configObjectType)
                    .typeArguments(runtimeTypeInfo.typeName().typeArguments())
                    .build();

            TypeName configuredTypeInterface = TypeName.builder(RUNTIME_API)
                    .addTypeArgument(configObjectType)
                    .build();

            this.nestedValidators = List.of(
                    new ValidateCreateMethod(configObjectWithTypeParams, runtimeTypeInfo),
                    new ValidateCreateWithConsumerMethod(configObjectWithTypeParams, runtimeTypeInfo),
                    new ValidateImplements(runtimeTypeInfo,
                                           configuredTypeInterface,
                                           "Type annotated with @"
                                                   + RUNTIME_PROTOTYPED_BY.classNameWithEnclosingNames()
                                                   + "(" + configObjectType.className()
                                                   + ".class) must implement "
                                                   + RUNTIME_API.classNameWithEnclosingNames()
                                                   + "<"
                                                   + configObjectWithTypeParams.classNameWithTypes() + ">")
            );
        }

        @Override
        public void validate(Errors.Collector errors) {
            for (ValidationTask nestedValidator : nestedValidators) {
                nestedValidator.validate(errors);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidateConfiguredType that = (ValidateConfiguredType) o;
            return Objects.equals(runtimeTypeInfo, that.runtimeTypeInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runtimeTypeInfo);
        }
    }

    private static class ValidateImplements extends ValidationTask {
        private final TypeInfo typeInfo;
        private final TypeName requiredInterface;
        private final String message;

        ValidateImplements(TypeInfo typeInfo, TypeName requiredInterface, String message) {
            this.typeInfo = typeInfo;
            this.requiredInterface = requiredInterface;
            this.message = message;
        }

        @Override
        void validate(Errors.Collector errors) {
            ValidationTask.validateImplements(errors, typeInfo, requiredInterface, message);
        }
    }

    static class ValidateBlueprint extends ValidationTask {
        private final TypeInfo blueprint;

        ValidateBlueprint(TypeInfo blueprint) {
            this.blueprint = blueprint;
        }

        @Override
        public void validate(Errors.Collector errors) {
            // must be package local
            if (blueprint.accessModifier() == AccessModifier.PUBLIC) {
                errors.fatal(blueprint.typeName(), blueprint.typeName().fqName()
                        + " is defined as public, it must be package local");
            }

            // if configured & provides, must have config key
            if (blueprint.hasAnnotation(Types.PROTOTYPE_CONFIGURED)
                && blueprint.hasAnnotation(Types.PROTOTYPE_PROVIDES)) {
                Annotation configured = blueprint.annotation(Types.PROTOTYPE_CONFIGURED);
                String value = configured.stringValue().orElse("");
                if (value.isEmpty()) {
                    // we have a @Configured and @Provides - this should have a configuration key!
                    errors.fatal(blueprint.typeName(), blueprint.typeName().fqName()
                            + " is marked as @Configured and @Provides, yet it does not"
                                         + " define a configuration key");
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidateBlueprint that = (ValidateBlueprint) o;
            return Objects.equals(blueprint.typeName(), that.blueprint.typeName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(blueprint.typeName());
        }
    }

    /**
     * Validation for blueprints that extend a factory.
     * <p>
     * If "PrototypeBlueprint" implements a Factory<RuntimeType>
     * - RuntimeType must implement RuntimeType.Api<ConfigObjectType>
     * - RuntimeType must have "static RuntimeType create(ConfigObject)"
     * - RuntimeType must have "static RuntimeType create(Consumer<ConfigObject.Builder>)
     * - RuntimeType must have "static ConfigObject.Builder builder()
     */
    static class ValidateBlueprintExtendsFactory extends ValidationTask {
        private final List<ValidationTask> nestedValidators;
        private final TypeName configObjectType;
        private final TypeInfo blueprintInfo;
        private final TypeInfo runtimeTypeInfo;

        ValidateBlueprintExtendsFactory(TypeName configObjectType, TypeInfo blueprintInfo, TypeInfo runtimeTypeInfo) {
            this.configObjectType = configObjectType;
            this.blueprintInfo = blueprintInfo;
            this.runtimeTypeInfo = runtimeTypeInfo;

            TypeName configObjectBuilder = TypeName.builder()
                    .packageName(configObjectType.packageName())
                    .enclosingNames(List.of(configObjectType.className()))
                    .className("Builder")
                    .build();

            nestedValidators = List.of(
                    new ValidateBuilderMethod(configObjectType, runtimeTypeInfo, configObjectBuilder),
                    new ValidateAnnotatedWith(runtimeTypeInfo,
                                              RUNTIME_PROTOTYPED_BY,
                                              configObjectType.genericTypeName().fqName())
            );
        }

        @Override
        public void validate(Errors.Collector errors) {
            validateImplements(errors,
                               runtimeTypeInfo,
                               TypeName.builder(RUNTIME_API)
                                       .addTypeArgument(configObjectType.boxed())
                                       .build(),
                               "As " + blueprintInfo.typeName().fqName() + " implements "
                                       + PROTOTYPE_FACTORY.classNameWithEnclosingNames()
                                       + "<"
                                       + runtimeTypeInfo.typeName().fqName() + ">, the runtime type must implement(or extend) "
                                       + "interface " + RUNTIME_API.fqName() + "<" + configObjectType.className() + ">"
            );
            for (ValidationTask nestedValidator : nestedValidators) {
                nestedValidator.validate(errors);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidateBlueprintExtendsFactory that = (ValidateBlueprintExtendsFactory) o;
            return Objects.equals(blueprintInfo, that.blueprintInfo) && Objects.equals(runtimeTypeInfo,
                                                                                       that.runtimeTypeInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blueprintInfo, runtimeTypeInfo);
        }
    }

    private static class ValidateAnnotatedWith extends ValidationTask {

        private final TypeInfo typeInfo;
        private final TypeName annotation;
        private final String expectedValue;

        ValidateAnnotatedWith(TypeInfo typeInfo, TypeName annotation, String expectedValue) {
            this.typeInfo = typeInfo;
            this.annotation = annotation;
            this.expectedValue = expectedValue;
        }

        @Override
        void validate(Errors.Collector errors) {
            if (typeInfo.findAnnotation(annotation)
                    .stream()
                    .noneMatch(it -> it.value().map(expectedValue::equals).orElse(false))) {
                errors.fatal(typeInfo.typeName(),
                             "Type " + typeInfo.typeName().fqName()
                                     + " must be annotated with " + annotation.fqName() + "(" + expectedValue + ")");
            }
        }
    }

    /**
     * Validate that runtime object has a factory method to be created from prototype.
     * <pre>
     * public static Tls create(TlsConfig tlsConfig) {
     *     return new TlsImpl(tlsConfig);
     * }
     * </pre>
     */
    private static class ValidateCreateMethod extends ValidationTask {
        private final TypeName configObjectType;
        private final TypeInfo runtimeTypeInfo;

        ValidateCreateMethod(TypeName configObjectType, TypeInfo runtimeTypeInfo) {
            this.configObjectType = configObjectType;
            this.runtimeTypeInfo = runtimeTypeInfo;
        }

        @Override
        public void validate(Errors.Collector errors) {
            String fqName = runtimeTypeInfo.typeName().genericTypeName().fqName();

            validateFactoryMethod(errors,
                                  runtimeTypeInfo,
                                  runtimeTypeInfo.typeName(),
                                  "create",
                                  configObjectType,
                                  "As " + fqName + " is annotated with @"
                                          + RUNTIME_PROTOTYPED_BY.classNameWithEnclosingNames()
                                          + "("
                                          + configObjectType.className()
                                          + "), the type must implement the following "
                                          + "method:\n"
                                          + "static " + runtimeTypeInfo.typeName().classNameWithTypes() + " create("
                                          + configObjectType.classNameWithTypes() + ");");
        }
    }

    /**
     * Validate that runtime object has a factory method with prototype builder consumer.
     * <pre>
     * public static Tls create(Consumer<TlsConfig.Builder> consumer) {
     *     TlsConfig.Builder builder = TlsConfig.builder();
     *     consumer.accept(builder);
     *     return builder.build();
     * }
     * </pre>
     */
    private static class ValidateCreateWithConsumerMethod extends ValidationTask {
        private final TypeName configObjectType;
        private final TypeInfo runtimeTypeInfo;

        ValidateCreateWithConsumerMethod(TypeName configObjectType,
                                         TypeInfo runtimeTypeInfo) {
            this.configObjectType = configObjectType;
            this.runtimeTypeInfo = runtimeTypeInfo;
        }

        @Override
        public void validate(Errors.Collector errors) {
            TypeName consumerArgument = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(TypeName.create(configObjectType.fqName() + ".Builder"))
                    .build();
            validateFactoryMethod(errors,
                                  runtimeTypeInfo,
                                  runtimeTypeInfo.typeName(),
                                  "create",
                                  consumerArgument,
                                  "As " + configObjectType.fqName() + " implements "
                                          + PROTOTYPE_FACTORY.classNameWithEnclosingNames()
                                          + "<"
                                          + runtimeTypeInfo.typeName().resolvedName() + ">, the type "
                                          + runtimeTypeInfo.typeName().className()
                                          + " must implement the following "
                                          + "method:\n"
                                          + "static "
                                          + runtimeTypeInfo.typeName().className()
                                          + " create(" + consumerArgument.resolvedName() + " consumer) {\n"
                                          + "  return builder().update(consumer).build();"
                                          + "}");
        }
    }

    /**
     * Validate that a runtime object has static prototype builder method.
     * <pre>
     * public static TlsConfig.Builder builder() {
     *     return TlsConfig.builder();
     * }
     * </pre>
     */
    private static class ValidateBuilderMethod extends ValidationTask {
        private final TypeName configObjectType;
        private final TypeInfo runtimeTypeInfo;
        private final TypeName configObjectBuilder;

        ValidateBuilderMethod(TypeName configObjectType,
                              TypeInfo runtimeTypeInfo,
                              TypeName configObjectBuilder) {
            this.configObjectType = configObjectType;
            this.runtimeTypeInfo = runtimeTypeInfo;
            this.configObjectBuilder = configObjectBuilder;
        }

        @Override
        public void validate(Errors.Collector errors) {

            validateFactoryMethod(errors,
                                  runtimeTypeInfo,
                                  configObjectBuilder,
                                  "builder",
                                  null,
                                  "As " + configObjectType.fqName() + " implements "
                                          + PROTOTYPE_FACTORY.classNameWithEnclosingNames()
                                          + "<"
                                          + runtimeTypeInfo.typeName()
                                          .fqName() + ">, the runtime type must implement the following "
                                          + "method:\n"
                                          + "static " + configObjectType.className() + ".Builder"
                                          + " builder() {\n"
                                          + "  return " + configObjectType.className() + ".builder();\n"
                                          + "}");
        }
    }
}
