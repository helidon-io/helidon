/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.codegen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.service.inject.codegen.InjectCodegenTypes.DOUBLE_ARRAY;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_CONFIG;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_CONFIG_BUILDER;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_MAIN;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_REGISTRY;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.STRING_ARRAY;
import static java.util.function.Predicate.not;

/**
 * Utility for {@value #CLASS_NAME} class generation.
 */
public final class ApplicationMainGenerator {
    /**
     * Default class name of the generated main class.
     */
    public static final String CLASS_NAME = "ApplicationMain";
    /**
     * Signature of the {@code serviceDescriptors} method (this method is abstract in InjectionMain).
     */
    private static final ElementSignature METHOD_SERVICE_DESCRIPTORS = ElementSignature.createMethod(
            TypeNames.PRIMITIVE_VOID,
            "serviceDescriptors",
            List.of(INJECT_CONFIG_BUILDER));
    /**
     * Signature of the {@code discoverServices} method.
     */
    private static final ElementSignature METHOD_DISCOVER_SERVICES = ElementSignature.createMethod(
            TypeNames.PRIMITIVE_BOOLEAN,
            "discoverServices",
            List.of());
    /**
     * Signature of the {@code runLevels} method.
     */
    private static final ElementSignature METHOD_RUN_LEVELS = ElementSignature.createMethod(
            DOUBLE_ARRAY,
            "runLevels",
            List.of(INJECT_CONFIG, INJECT_REGISTRY));

    private ApplicationMainGenerator() {
    }

    /**
     * Generate the common parts of the type.
     *
     * <ul>
     *     <li>Class declaration with javadoc, @Generated, and copyright, with name {@value #CLASS_NAME}</li>
     *     <li>Protected constructor with javadoc</li>
     *     <li>{@code public static void main} method with javadoc</li>
     * </ul>
     *
     * @param generator                 generator type name
     * @param declaredSignatures        signatures declared on custom main class (if any)
     * @param superType                 super type of the generated class
     * @param generatedType             the type to generate
     * @param discoverServices          whether to discover services (false when all services are manually registered to the
     *                                  builder)
     * @param addRunLevels              whether to add run levels from discovered services
     * @param serviceDescriptorsHandler handler of the service descriptor method
     * @param runLevelHandler           handler of the run level method
     * @return class model builder
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // all parameters are mandatory
    public static ClassModel.Builder generate(TypeName generator,
                                              Set<ElementSignature> declaredSignatures,
                                              TypeName superType,
                                              TypeName generatedType,
                                              boolean discoverServices,
                                              boolean addRunLevels,
                                              CodeGeneratorHandler serviceDescriptorsHandler,
                                              CodeGeneratorHandler runLevelHandler) {
        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .accessModifier(AccessModifier.PUBLIC)
                .copyright(CodegenUtil.copyright(generator,
                                                 generator,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(generator,
                                                               generatedType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .superType(superType)
                .addDescriptionLine("Main class generated for Helidon Inject Application.")
                .isFinal(true);

        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE));

        classModel.addMethod(main -> main
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .description("Start the application.")
                .name("main")
                .addParameter(args -> args
                        .type(STRING_ARRAY)
                        .description("Command line arguments.")
                        .name("args"))
                .update(it -> mainMethodBody(generatedType, it)));

        if (!declaredSignatures.contains(METHOD_SERVICE_DESCRIPTORS)) {
            // only create this method if it is not created by the user
            classModel.addMethod(methodModel -> methodModel
                    .name("serviceDescriptors")
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PROTECTED)
                    .returnType(TypeNames.PRIMITIVE_VOID)
                    .addParameter(config -> config
                            .type(INJECT_CONFIG_BUILDER)
                            .name("config"))
                    .update(it -> serviceDescriptorsHandler.handle(classModel, it, "config")));
        }

        if (discoverServices && !declaredSignatures.contains(METHOD_DISCOVER_SERVICES)) {
            classModel.addMethod(discoverServicesMethod -> discoverServicesMethod
                    .name("discoverServices")
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PROTECTED)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .addContentLine("return true;"));
        }

        if (addRunLevels && !declaredSignatures.contains(METHOD_RUN_LEVELS)) {
            classModel.addMethod(runLevels -> runLevels
                    .name("runLevels")
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(DOUBLE_ARRAY)
                    .addParameter(config -> config
                            .type(INJECT_CONFIG)
                            .name("config"))
                    .addParameter(registry -> registry
                            .type(INJECT_REGISTRY)
                            .name("registry"))
                    .addContentLine("return new double[] {")
                    .update(it -> runLevelHandler.handle(classModel, it, "config"))
                    .addContentLine("};"));
        }

        return classModel;
    }

    /**
     * Provides all relevant signatures that may override methods from {@code InjectionMain}.
     *
     * @param customMain type to analyze
     * @return set of method signatures that are non-private, non-static
     */
    public static Set<ElementSignature> declaredSignatures(TypeInfo customMain) {
        return customMain.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isPrivate))
                .map(TypedElementInfo::signature)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validate a type, to make sure it is a valid custom main class.
     *
     * @param customMain type to validate
     */
    public static void validate(TypeInfo customMain) {
        Optional<TypeInfo> superType = customMain.superTypeInfo();
        if (superType.isEmpty()) {
            throw new CodegenException("Custom main class must directly extend " + INJECT_MAIN.fqName() + ", but "
                                               + customMain.typeName().fqName() + " does not extend any class",
                                       customMain.originatingElementValue());
        }
        if (!superType.get().typeName().equals(INJECT_MAIN)) {
            throw new CodegenException("Custom main class must directly extend " + INJECT_MAIN.fqName() + ", but "
                                               + customMain.typeName().fqName() + " extends " + superType.get().typeName(),
                                       customMain.originatingElementValue());
        }
        if (customMain.accessModifier() == AccessModifier.PRIVATE) {
            throw new CodegenException("Custom main class must be accessible (non-private) class, but "
                                               + customMain.typeName().fqName() + " is private.",
                                       customMain.originatingElementValue());
        }
        if (customMain.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates.elementName("main"))
                .anyMatch(ElementInfoPredicates.hasParams(TypeName.create(String[].class)))) {
            throw new CodegenException("Custom main class must not declare a static main(String[]) method, as it is code "
                                               + "generated into the ApplicationMain class, but "
                                               + customMain.typeName().fqName() + " declares it.",
                                       customMain.originatingElementValue());
        }
        if (customMain.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isConstructor)
                .filter(not(ElementInfoPredicates::isPrivate))
                .noneMatch(ElementInfoPredicates.hasParams())) {
            throw new CodegenException("Custom main class must have an accessible no-argument constructor, but "
                                               + customMain.typeName().fqName() + " does not.",
                                       customMain.originatingElementValue());
        }
    }

    private static void mainMethodBody(TypeName type, Method.Builder method) {
        method.addContent("new ")
                .addContent(type)
                .addContentLine("().start(args);");
    }

    /**
     * Handler to generated method serviceDescriptors in {@value #CLASS_NAME} class.
     */
    @FunctionalInterface
    public interface CodeGeneratorHandler {
        /**
         * Handle the class model (to allow adding constants and helper methods), and method model (to add the body).
         *
         * @param classModel      class model of the generated main
         * @param methodModel     method model of the serviceDescriptors method
         * @param configParamName name of the parameter of {@code InjectConfig.Builder}
         */
        void handle(ClassModel.Builder classModel, Method.Builder methodModel, String configParamName);
    }
}
