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

package io.helidon.service.codegen;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_APPLICATION_MAIN;

/**
 * Utility for {@code GeneratedMain} class generation.
 */
public final class GeneratedMainCodegen {
    /**
     * Class name of the generated main class.
     */
    public static final String CLASS_NAME = "GeneratedMain";

    private GeneratedMainCodegen() {
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
     * @param generator        generator type name
     * @param generatedType    the type to generate
     * @param discoverServices whether to discover services (false when all services are manually registered to the builder)
     * @param handler          handler of the service descriptor method
     * @return class model builder
     */
    public static ClassModel.Builder generate(TypeName generator,
                                              TypeName generatedType,
                                              boolean discoverServices,
                                              ServiceDescriptorsHandler handler) {
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
                .superType(INJECT_APPLICATION_MAIN)
                .addDescriptionLine("Main class generated for Helidon Inject.");

        classModel.addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PROTECTED))
                .description("Default constructor with no side effects.");

        classModel.addMethod(main -> main
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .name("main")
                .addParameter(args -> args
                        .type(String[].class)
                        .name("args"))
                .update(it -> mainMethodBody(generatedType, it)));

        classModel.addMethod(methodModel -> methodModel
                .name("serviceDescriptors")
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PROTECTED)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .addParameter(config -> config
                        .type(ServiceCodegenTypes.INJECT_CONFIG_BUILDER)
                        .name("config"))
                .update(it -> handler.handle(classModel, it, "config")));

        if (discoverServices) {
            classModel.addMethod(discoverServicesMethod -> discoverServicesMethod
                    .name("discoverServices")
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(AccessModifier.PROTECTED)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .addContentLine("return true;"));
        }

        return classModel;
    }

    private static void mainMethodBody(TypeName type, Method.Builder method) {
        method.addContent("new ")
                .addContent(type)
                .addContentLine("().start(args);");
    }

    /**
     * Handler to generated method serviceDescriptors in GeneratedMain class.
     */
    @FunctionalInterface
    public interface ServiceDescriptorsHandler {
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
