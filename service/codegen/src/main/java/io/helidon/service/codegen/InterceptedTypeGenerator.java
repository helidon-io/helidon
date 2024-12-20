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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_INVOKER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_METADATA;
import static io.helidon.service.codegen.ServiceCodegenTypes.LIST_OF_ANNOTATIONS;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_QUALIFIERS;

class InterceptedTypeGenerator {
    public static final String INTERCEPT_META_PARAM = "helidonInject__interceptMeta";
    public static final String SERVICE_DESCRIPTOR_PARAM = "helidonInject__serviceDescriptor";
    public static final String TYPE_QUALIFIERS_PARAM = "helidonInject__typeQualifiers";
    public static final String TYPE_ANNOTATIONS_PARAM = "helidonInject__typeAnnotations";
    private static final TypeName GENERATOR = TypeName.create(InterceptedTypeGenerator.class);
    private static final TypeName RUNTIME_EXCEPTION_TYPE = TypeName.create(RuntimeException.class);
    private final TypeName serviceType;
    private final TypeName descriptorType;
    private final TypeName interceptedType;
    private final TypedElementInfo constructor;
    private final List<MethodDefinition> interceptedMethods;

    InterceptedTypeGenerator(RegistryCodegenContext ctx,
                             TypeInfo typeInfo,
                             TypeName serviceType,
                             TypeName descriptorType,
                             TypeName interceptedType,
                             TypedElementInfo constructor,
                             List<TypedElements.ElementMeta> interceptedMethods) {
        this.serviceType = serviceType;
        this.descriptorType = descriptorType;
        this.interceptedType = interceptedType;
        this.constructor = constructor;
        this.interceptedMethods = MethodDefinition.toDefinitions(ctx, typeInfo, interceptedMethods);
    }

    static void generateElementInfoFields(ClassModel.Builder classModel, List<MethodDefinition> interceptedMethods) {
        for (MethodDefinition interceptedMethod : interceptedMethods) {
            classModel.addField(methodField -> methodField
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(TypeNames.TYPED_ELEMENT_INFO)
                    .name(interceptedMethod.constantName())
                    .addContentCreate(interceptedMethod.info()));
        }
    }

    static void generateInvokerFields(ClassModel.Builder classModel,
                                      List<MethodDefinition> interceptedMethods) {
        for (MethodDefinition interceptedMethod : interceptedMethods) {
            classModel.addField(methodField -> methodField
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(invokerType(interceptedMethod.info().typeName()))
                    .name(interceptedMethod.invokerName()));
        }
    }

    static void generateInterceptedMethods(ClassModel.Builder classModel, List<MethodDefinition> interceptedMethods) {
        for (MethodDefinition interceptedMethod : interceptedMethods) {
            TypedElementInfo info = interceptedMethod.info();
            String invoker = interceptedMethod.invokerName();

            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .accessModifier(info.accessModifier())
                    .name(info.elementName())
                    .returnType(info.typeName())
                    .update(it -> info.parameterArguments().forEach(arg -> it.addParameter(param -> param.type(arg.typeName())
                            .name(arg.elementName()))))
                    .update(it -> {
                        // add throws statements
                        if (!interceptedMethod.exceptionTypes().isEmpty()) {
                            for (TypeName exceptionType : interceptedMethod.exceptionTypes()) {
                                it.addThrows(exceptionType, "thrown by intercepted method");
                            }
                        }
                    })
                    .update(it -> {
                        String invokeLine = invoker
                                + ".invoke("
                                + info.parameterArguments()
                                .stream().map(TypedElementInfo::elementName)
                                .collect(Collectors.joining(", "))
                                + ");";
                        // body of the method
                        it.addContentLine("try {")
                                .addContent(interceptedMethod.isVoid() ? "" : "return ")
                                .addContentLine(invokeLine)
                                .addContent("}");
                        for (TypeName exceptionType : interceptedMethod.exceptionTypes()) {
                            it.addContent(" catch (")
                                    .addContent(exceptionType)
                                    .addContentLine(" helidonInject__e) {")
                                    .addContentLine(" throw helidonInject__e;")
                                    .addContent("}");

                        }
                        if (!interceptedMethod.exceptionTypes().contains(RUNTIME_EXCEPTION_TYPE)) {
                            it.addContent(" catch (")
                                    .addContent(RuntimeException.class)
                                    .addContentLine(" helidonInject__e) {")
                                    .addContentLine("throw helidonInject__e;")
                                    .addContent("}");
                        }
                        it.addContent(" catch (")
                                .addContent(Exception.class)
                                .addContentLine(" helidonInject__e) {")
                                .addContent("throw new ")
                                .addContent(RuntimeException.class)
                                .addContentLine("(helidonInject__e);")
                                .addContentLine("}");

                    }));

        }
    }

    /**
     * Create invokes for intercepted methods.
     *
     * @param cModel                model to add the invokers to (constructor)
     * @param descriptorType        type of the descriptor we are processing
     * @param interceptedMethods    list of intercepted methods
     * @param useDescriptorConstant whether to use descriptor constant, or local constant for method info
     * @param interceptMetaName     name of the interceptor meta parameter
     * @param descriptorName        name of the descriptor parameter
     * @param qualifiersName        name of the qualifiers parameter
     * @param annotationsName       name of the annotations parameter
     * @param invocationTargetName  name of the invocation target parameter
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    static void createInvokers(Constructor.Builder cModel,
                               TypeName descriptorType,
                               List<MethodDefinition> interceptedMethods,
                               boolean useDescriptorConstant,
                               String interceptMetaName,
                               String descriptorName,
                               String qualifiersName,
                               String annotationsName,
                               String invocationTargetName) {
        boolean hasGenericType = false;

        for (MethodDefinition interceptedMethod : interceptedMethods) {
            cModel.addContent("this.")
                    .addContent(interceptedMethod.invokerName)
                    .addContent(" = ")
                    .addContent(interceptMetaName)
                    .addContentLine(".createInvoker(")
                    .increaseContentPadding()
                    .addContentLine("this,")
                    .addContent(descriptorName)
                    .addContentLine(",")
                    .addContent(qualifiersName)
                    .addContentLine(",")
                    .addContent(annotationsName)
                    .addContentLine(",")
                    .update(it -> {
                        if (useDescriptorConstant) {
                            it.addContent(descriptorType)
                                    .addContent(".");
                        }
                    })
                    .addContent(interceptedMethod.constantName())
                    .addContentLine(",")
                    .addContent("helidonInject__params -> ");
            if (interceptedMethod.isVoid()) {
                cModel.addContentLine("{");
            }
            cModel.addContent(invocationTargetName)
                    .addContent(".")
                    .addContent(interceptedMethod.info().elementName())
                    .addContent("(");

            List<String> allArgs = new ArrayList<>();
            List<TypedElementInfo> args = interceptedMethod.info().parameterArguments();
            for (int i = 0; i < args.size(); i++) {
                TypedElementInfo arg = args.get(i);
                allArgs.add("(" + arg.typeName().resolvedName() + ") helidonInject__params[" + i + "]");
                if (!arg.typeName().typeArguments().isEmpty()) {
                    hasGenericType = true;
                }
            }
            cModel.addContent(String.join(", ", allArgs));
            cModel.addContent(")");

            if (interceptedMethod.isVoid()) {
                cModel.addContentLine(";");
                cModel.addContentLine("return null;");
                cModel.addContent("}");
            }
            cModel.addContent(", ")
                    .addContent(Set.class)
                    .addContent(".of(")
                    .addContent(interceptedMethod.exceptionTypes()
                                        .stream()
                                        .map(it -> it.fqName() + ".class")
                                        .collect(Collectors.joining(", ")))
                    .addContentLine("));")
                    .decreaseContentPadding();
        }
        if (hasGenericType) {
            cModel.addAnnotation(Annotation.create(TypeName.create(SuppressWarnings.class), "unchecked"));
        }
    }

    ClassModel.Builder generate() {
        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 serviceType,
                                                 interceptedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               serviceType,
                                                               interceptedType,
                                                               "1",
                                                               ""))
                .description("Intercepted sub-type for {@link " + serviceType.fqName() + "}.")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(interceptedType)
                .superType(serviceType);

        generateInvokerFields(classModel, interceptedMethods);

        generateConstructor(classModel);

        generateInterceptedMethods(classModel, interceptedMethods);

        return classModel;
    }

    private static TypeName invokerType(TypeName type) {
        return TypeName.builder(INTERCEPT_INVOKER)
                .addTypeArgument(type.boxed())
                .build();
    }

    private void generateConstructor(ClassModel.Builder classModel) {
        classModel.addConstructor(constructor -> constructor
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(interceptMeta -> interceptMeta.type(INTERCEPT_METADATA)
                        .name(INTERCEPT_META_PARAM))
                .addParameter(descriptor -> descriptor.type(descriptorType)
                        .name(SERVICE_DESCRIPTOR_PARAM))
                .addParameter(qualifiers -> qualifiers.type(SET_OF_QUALIFIERS)
                        .name(TYPE_QUALIFIERS_PARAM))
                .addParameter(qualifiers -> qualifiers.type(LIST_OF_ANNOTATIONS)
                        .name(TYPE_ANNOTATIONS_PARAM))
                .update(this::addConstructorParameters)
                .update(this::callSuperConstructor)
                .update(it -> createInvokers(it,
                                             descriptorType,
                                             interceptedMethods,
                                             true,
                                             INTERCEPT_META_PARAM,
                                             SERVICE_DESCRIPTOR_PARAM,
                                             TYPE_QUALIFIERS_PARAM,
                                             TYPE_ANNOTATIONS_PARAM,
                                             "super"))
        );
    }

    private void callSuperConstructor(Constructor.Builder cModel) {
        cModel.addContent("super(");
        cModel.addContent(this.constructor.parameterArguments()
                                  .stream()
                                  .map(TypedElementInfo::elementName)
                                  .collect(Collectors.joining(", ")));
        cModel.addContentLine(");");
        cModel.addContentLine("");
    }

    private void addConstructorParameters(Constructor.Builder cModel) {
        // for each constructor parameter, add it as is (same type and name as super type)
        // this will not create conflicts, unless somebody names their constructor parameters same
        // as the ones above (which we will not do, and others should not do)
        this.constructor.parameterArguments().forEach(constructorArg -> {
            cModel.addParameter(generatedCtrParam -> generatedCtrParam.type(constructorArg.typeName())
                    .name(constructorArg.elementName()));
        });
    }

    record MethodDefinition(TypedElementInfo info,
                            String constantName,
                            String invokerName,
                            boolean isVoid,
                            Set<TypeName> exceptionTypes) {

        static List<MethodDefinition> toDefinitions(RegistryCodegenContext ctx,
                                                    TypeInfo typeInfo,
                                                    List<TypedElements.ElementMeta> interceptedMethods) {
            List<TypedElements.ElementMeta> sortedMethods = new ArrayList<>(interceptedMethods);

            List<MethodDefinition> result = new ArrayList<>();
            for (int i = 0; i < sortedMethods.size(); i++) {
                TypedElements.ElementMeta elementMeta = sortedMethods.get(i);

                List<Annotation> elementAnnotations = new ArrayList<>(elementMeta.element().annotations());
                addInterfaceAnnotations(elementAnnotations, elementMeta.abstractMethods());

                TypedElementInfo typedElementInfo = TypedElementInfo.builder()
                        .from(elementMeta.element())
                        .annotations(elementAnnotations)
                        .build();

                String constantName = "METHOD_" + toConstantName(ctx.uniqueName(typeInfo, elementMeta.element()));
                String invokerName = typedElementInfo.elementName() + "_" + i + "_invoker";

                result.add(new MethodDefinition(typedElementInfo,
                                                constantName,
                                                invokerName,
                                                TypeNames.PRIMITIVE_VOID.equals(typedElementInfo.typeName()),
                                                typedElementInfo.throwsChecked()));
            }
            result.sort(Comparator.comparing(o -> o.invokerName));
            return result;
        }

        private static void addInterfaceAnnotations(List<Annotation> elementAnnotations,
                                                    List<TypedElements.DeclaredElement> declaredElements) {

            for (TypedElements.DeclaredElement declaredElement : declaredElements) {
                declaredElement.element()
                        .annotations()
                        .forEach(it -> addInterfaceAnnotation(elementAnnotations, it));
            }
        }

        private static void addInterfaceAnnotation(List<Annotation> elementAnnotations, Annotation annotation) {
            // only add if not already there
            if (!elementAnnotations.contains(annotation)) {
                elementAnnotations.add(annotation);
            }
        }
    }
}
