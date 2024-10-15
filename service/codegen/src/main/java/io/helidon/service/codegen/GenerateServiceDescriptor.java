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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Generates a service descriptor.
 */
class GenerateServiceDescriptor {
    static final TypeName SET_OF_RESOLVED_TYPES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.RESOLVED_TYPE_NAME)
            .build();
    private static final TypeName LIST_OF_DEPENDENCIES = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(ServiceCodegenTypes.SERVICE_DEPENDENCY)
            .build();
    private static final TypeName DESCRIPTOR_TYPE = TypeName.builder(ServiceCodegenTypes.SERVICE_DESCRIPTOR)
            .addTypeArgument(TypeName.create("T"))
            .build();
    private static final TypeName ANY_GENERIC_TYPE = TypeName.builder(TypeNames.GENERIC_TYPE)
            .addTypeArgument(TypeName.create("?"))
            .build();

    private final TypeName generator;
    private final RegistryCodegenContext ctx;
    private final Collection<TypeInfo> services;
    private final TypeInfo typeInfo;
    private final boolean autoAddContracts;
    private final Function<TypeName, Optional<TypeInfo>> typeInfoFactory;
    private final DescriptorConsumer descriptorConsumer;

    private GenerateServiceDescriptor(TypeName generator,
                                      RegistryCodegenContext ctx,
                                      Collection<TypeInfo> allServices,
                                      TypeInfo service,
                                      Function<TypeName, Optional<TypeInfo>> typeInfoFactory,
                                      DescriptorConsumer descriptorConsumer) {
        this.generator = generator;
        this.ctx = ctx;
        this.services = allServices;
        this.typeInfo = service;
        this.autoAddContracts = ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES.value(ctx.options());
        this.typeInfoFactory = typeInfoFactory;
        this.descriptorConsumer = descriptorConsumer;
    }

    /**
     * Generate a service descriptor for the provided service type info.
     *
     * @param generator    type of the generator responsible for this event
     * @param ctx          context of code generation
     * @param roundContext current round context
     * @param allServices  all services processed in this round of processing
     * @param service      service to create a descriptor for
     * @return class model builder of the service descriptor
     */
    static ClassModel.Builder generate(TypeName generator,
                                              RegistryCodegenContext ctx,
                                              RegistryRoundContext roundContext,
                                              Collection<TypeInfo> allServices,
                                              TypeInfo service) {
        return new GenerateServiceDescriptor(generator,
                                             ctx,
                                             allServices,
                                             service,
                                             roundContext::typeInfo,
                                             roundContext::addDescriptor)
                .generate();
    }

    static void declareConstructorParams(Method.Builder method, List<CoreDependency> params) {
        // for each parameter, obtain its value from context
        for (CoreDependency param : params) {
            method.addContent(param.typeName())
                    .addContent(" ")
                    .addContent(param.name())
                    .addContent(" = ctx__helidonRegistry.dependency(")
                    .addContent(param.dependencyConstant())
                    .addContentLine(");");
        }
        if (!params.isEmpty()) {
            method.addContentLine("");
        }
    }

    private ClassModel.Builder generate() {
        if (typeInfo.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("We can only generate service descriptors for classes, interface was requested: ",
                                       typeInfo.originatingElementValue());
        }

        CoreService service = CoreService.create(ctx, typeInfoFactory, typeInfo, services, autoAddContracts);
        TypeName serviceType = service.serviceType();
        TypeName descriptorType = service.descriptorType();

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(generator,
                                                 serviceType,
                                                 descriptorType))
                .addAnnotation(CodegenUtil.generatedAnnotation(generator,
                                                               serviceType,
                                                               descriptorType,
                                                               "1",
                                                               ""))
                .type(descriptorType)
                .addGenericArgument(TypeArgument.create("T extends " + serviceType.fqName()))
                .javadoc(Javadoc.builder()
                                 .add("Service descriptor for {@link " + serviceType.fqName() + "}.")
                                 .addGenericArgument("T", "type of the service, for extensibility")
                                 .build())
                // we need to keep insertion order, as constants may depend on each other
                .sortStaticFields(false);

        // declare the class

        if (service.superType().present()) {
            classModel.superType(service.superType().descriptorType());
        } else {
            classModel.addInterface(DESCRIPTOR_TYPE);
        }

        singletonInstanceField(classModel, serviceType, descriptorType);

        serviceTypeMethod(classModel, service);
        descriptorTypeMethod(classModel, service);
        contractsMethod(classModel, service);

        // public fields are last, so they do not intersect with private fields (it is not as nice to read)
        // they cannot be first, as they require some of the private fields
        dependencyFields(classModel, service);
        // dependencies require IP IDs, so they really must be last
        dependenciesMethod(classModel, service);

        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));

        // methods (some methods define fields as well)
        isAbstractMethod(classModel, service);
        instantiateMethod(classModel, service);
        postConstructMethod(classModel, service);
        preDestroyMethod(classModel, service);
        weightMethod(classModel, service);

        // service type is an implicit contract
        Set<ResolvedType> serviceContracts = new HashSet<>(service.contracts());
        Set<ResolvedType> factoryContracts = new HashSet<>(service.factoryContracts());
        if (factoryContracts.isEmpty()) {
            serviceContracts.add(ResolvedType.create(serviceType));
        } else {
            factoryContracts.add(ResolvedType.create(serviceType));
        }

        descriptorConsumer.addDescriptor("core",
                                         serviceType,
                                         descriptorType,
                                         classModel,
                                         weight(typeInfo).orElse(Weighted.DEFAULT_WEIGHT),
                                         serviceContracts,
                                         factoryContracts,
                                         typeInfo.originatingElementValue());

        return classModel;
    }

    private void addTypeConstant(ClassModel.Builder classModel,
                                 TypeName typeName,
                                 String constantName) {
        String stringType = typeName.resolvedName();
        // constants for dependency parameter types (used by next section)
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(TypeNames.TYPE_NAME)
                .name(constantName)
                .update(it -> {
                    if (stringType.indexOf('.') < 0) {
                        // there is no package, we must use class (if this is a generic type, we have a problem)
                        it.addContent(TypeNames.TYPE_NAME)
                                .addContent(".create(")
                                .addContent(typeName)
                                .addContent(".class)");
                    } else {
                        it.addContentCreate(typeName);
                    }
                }));
    }

    private void addGenericTypeConstant(ClassModel.Builder classModel,
                                        TypeName typeName,
                                        String constantName) {
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(ANY_GENERIC_TYPE)
                .name(constantName)
                .update(it -> {
                    if (typeName.primitive()) {
                        it.addContent(TypeNames.GENERIC_TYPE)
                                .addContent(".create(")
                                .addContent(typeName.className())
                                .addContent(".class)");
                    } else {
                        it.addContent("new ")
                                .addContent(TypeNames.GENERIC_TYPE)
                                .addContent("<")
                                .addContent(typeName)
                                .addContent(">() {}");
                    }
                })
        );
    }

    private void contracts(TypeInfo typeInfo,
                           boolean contractEligible,
                           Set<TypeName> collectedContracts,
                           Set<String> collectedFullyQualified) {
        TypeName typeName = typeInfo.typeName();

        boolean addedThisContract = false;
        if (contractEligible) {
            collectedContracts.add(typeName);
            addedThisContract = true;
            if (!collectedFullyQualified.add(typeName.resolvedName())) {
                // let us go no further, this type was already processed
                return;
            }
        }

        if (typeName.isSupplier()) {
            // this may be the interface itself, and then it does not have a type argument
            if (!typeName.typeArguments().isEmpty()) {
                // provider must have a type argument (and the type argument is an automatic contract
                TypeName providedType = typeName.typeArguments().getFirst();
                // and we support Supplier<Optional<X>> as well
                if (!providedType.generic()) {
                    // supplier is a contract
                    collectedContracts.add(TypeNames.SUPPLIER);

                    if (providedType.isOptional() && !providedType.typeArguments().isEmpty()) {
                        providedType = providedType.typeArguments().getFirst();
                        // we still have supplier as a contract
                        collectedFullyQualified.add(TypeNames.SUPPLIER.fqName());
                        collectedContracts.add(TypeNames.SUPPLIER);
                        if (providedType.generic()) {
                            providedType = null;
                        } else {
                            // and also optional
                            collectedFullyQualified.add(TypeNames.OPTIONAL.fqName());
                            collectedContracts.add(TypeNames.OPTIONAL);
                            contractsFromProvidedType(collectedContracts, collectedFullyQualified, providedType);
                        }
                    } else {
                        contractsFromProvidedType(collectedContracts, collectedFullyQualified, providedType);
                    }

                    if (providedType != null && !collectedFullyQualified.add(providedType.resolvedName())) {
                        // let us go no further, this type was already processed
                        return;
                    }
                }
            }

            // provider itself is a contract
            if (!addedThisContract) {
                collectedContracts.add(typeName);
                if (!collectedFullyQualified.add(typeName.resolvedName())) {
                    // let us go no further, this type was already processed
                    return;
                }
            }
        }

        // add contracts from interfaces and types annotated as @Contract
        if (Annotations.findFirst(ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT,
                                  TypeHierarchy.hierarchyAnnotations(ctx, typeInfo)).isPresent()) {
            collectedContracts.add(typeInfo.typeName());
        }

        // add contracts from @ExternalContracts
        typeInfo.findAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_EXTERNAL_CONTRACTS)
                .ifPresent(it -> collectedContracts.addAll(it.typeValues().orElseGet(List::of)));

        // go through hierarchy
        typeInfo.superTypeInfo().ifPresent(it -> contracts(it,
                                                           contractEligible,
                                                           collectedContracts,
                                                           collectedFullyQualified
        ));
        // interfaces are considered contracts by default
        typeInfo.interfaceTypeInfo().forEach(it -> contracts(it,
                                                             contractEligible,
                                                             collectedContracts,
                                                             collectedFullyQualified
        ));
    }

    private void contractsFromProvidedType(Set<TypeName> collectedContracts,
                                           Set<String> collectedFullyQualified,
                                           TypeName providedType) {
        Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(providedType);
        if (providedTypeInfo.isPresent()) {
            contracts(providedTypeInfo.get(),
                      true,
                      collectedContracts,
                      collectedFullyQualified
            );
        } else {
            collectedContracts.add(providedType);
        }
    }

    private void singletonInstanceField(ClassModel.Builder classModel, TypeName serviceType, TypeName descriptorType) {
        // singleton instance of the descriptor
        classModel.addField(instance -> instance.description("Global singleton instance for this descriptor.")
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .isFinal(true)
                .type(descriptorInstanceType(serviceType, descriptorType))
                .name("INSTANCE")
                .defaultValueContent("new " + descriptorType.className() + "<>()"));
    }

    private void dependencyFields(ClassModel.Builder classModel, CoreService service) {

        // first add all types
        for (var genericConstant : service.constants().genericConstants()) {
            addGenericTypeConstant(classModel, genericConstant.type(), genericConstant.constantName());
        }
        for (var typeNameConstant : service.constants().typeNameConstants()) {
            addTypeConstant(classModel, typeNameConstant.type(), typeNameConstant.constantName());
        }

        // and then add all dependencies (these use the types created above)
        for (CoreDependency dependency : service.dependencies()) {
            classModel.addField(field -> field
                    // must be public, used in generated Injection__Binding to bind services
                    .accessModifier(AccessModifier.PUBLIC)
                    .isStatic(true)
                    .isFinal(true)
                    .type(ServiceCodegenTypes.SERVICE_DEPENDENCY)
                    .name(dependency.dependencyConstant())
                    .description(dependencyDescription(service, dependency))
                    .addContent(ServiceCodegenTypes.SERVICE_DEPENDENCY)
                    .addContentLine(".builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".typeName(")
                    .addContent(dependency.typeNameConstant())
                    .addContentLine(")")
                    .addContent(".name(\"")
                    .addContent(dependency.name())
                    .addContentLine("\")")
                    .addContentLine(".service(SERVICE_TYPE)")
                    .addContentLine(".descriptor(DESCRIPTOR_TYPE)")
                    .addContent(".descriptorConstant(\"")
                    .addContent(dependency.dependencyConstant())
                    .addContentLine("\")")
                    .addContent(".contract(")
                    .addContent(dependency.contractTypeConstant())
                    .addContentLine(")")
                    .addContent(".contractType(")
                    .addContent(dependency.genericTypeConstant())
                    .addContentLine(")")
                    .addContent(".build()")
                    .decreaseContentPadding()
                    .decreaseContentPadding());
        }
    }

    private String dependencyDescription(CoreService service, CoreDependency dependency) {
        TypedElementInfo constructor = dependency.constructor();
        TypeName serviceType = service.serviceType();
        StringBuilder result = new StringBuilder("Dependency for ");
        boolean servicePublic = typeInfo.accessModifier() == AccessModifier.PUBLIC;
        boolean elementPublic = constructor.accessModifier() == AccessModifier.PUBLIC;

        if (servicePublic) {
            result.append("{@link ")
                    .append(serviceType.fqName());
            if (!elementPublic) {
                result.append("}");
            }
        } else {
            result.append(serviceType.classNameWithEnclosingNames());
        }

        if (servicePublic && elementPublic) {
            // full javadoc reference
            result
                    .append("#")
                    .append(serviceType.className())
                    .append("(")
                    .append(toDescriptionSignature(constructor, true))
                    .append(")")
                    .append("}");
        } else {
            // just text
            result.append("(")
                    .append(toDescriptionSignature(constructor, false))
                    .append(")");
        }

        result
                .append(", parameter ")
                .append(dependency.name())
                .append(".");
        return result.toString();
    }

    private String toDescriptionSignature(TypedElementInfo method, boolean javadoc) {
        if (javadoc) {
            return method.parameterArguments()
                    .stream()
                    .map(it -> it.typeName().fqName())
                    .collect(Collectors.joining(", "));
        } else {
            return method.parameterArguments()
                    .stream()
                    .map(it -> it.typeName().classNameWithEnclosingNames() + " " + it.elementName())
                    .collect(Collectors.joining(", "));
        }
    }

    private void dependenciesMethod(ClassModel.Builder classModel, CoreService service) {
        classModel.addField(dependencies -> dependencies
                .isStatic(true)
                .isFinal(true)
                .name("DEPENDENCIES")
                .type(LIST_OF_DEPENDENCIES)
                .addContent(List.class)
                .addContent(".of(")
                .update(it -> {
                    var iterator = service.dependencies().iterator();
                    while (iterator.hasNext()) {
                        it.addContent(iterator.next().dependencyConstant());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));

        // List<Dependency> dependencies()
        boolean hasSuperType = service.superType().present();
        if (hasSuperType || !service.dependencies().isEmpty()) {
            classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                    .returnType(LIST_OF_DEPENDENCIES)
                    .name("dependencies")
                    .update(it -> {
                        if (hasSuperType) {
                            it.addContentLine("return combineDependencies(DEPENDENCIES, super.dependencies());");
                        } else {
                            it.addContentLine("return DEPENDENCIES;");
                        }
                    }));
        }
    }

    private void serviceTypeMethod(ClassModel.Builder classModel, CoreService service) {
        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("SERVICE_TYPE")
                .addContentCreate(service.serviceType().genericTypeName()));

        // TypeName serviceType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("serviceType")
                .addContentLine("return SERVICE_TYPE;"));
    }

    private void descriptorTypeMethod(ClassModel.Builder classModel, CoreService service) {
        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("DESCRIPTOR_TYPE")
                .addContentCreate(service.descriptorType().genericTypeName()));

        // TypeName descriptorType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("descriptorType")
                .addContentLine("return DESCRIPTOR_TYPE;"));
    }

    private void contractsMethod(ClassModel.Builder classModel, CoreService service) {
        var contracts = service.contracts();
        var factoryContracts = service.factoryContracts();
        var superType = service.superType();

        if (!contracts.isEmpty() || superType.present()) {
            // we must declare the contracts method
            classModel.addField(contractsField -> contractsField
                    .isStatic(true)
                    .isFinal(true)
                    .name("CONTRACTS")
                    .type(SET_OF_RESOLVED_TYPES)
                    .addContent(Set.class)
                    .addContent(".of(")
                    .update(it -> {
                        Iterator<ResolvedType> iterator = contracts.iterator();
                        while (iterator.hasNext()) {
                            it.addContentCreate(iterator.next());
                            if (iterator.hasNext()) {
                                it.addContent(", ");
                            }
                        }
                    })
                    .addContent(")"));

            // Set<Class<?>> contracts()
            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .name("contracts")
                    .returnType(SET_OF_RESOLVED_TYPES)
                    .addContentLine("return CONTRACTS;"));
        }

        if (!factoryContracts.isEmpty() || superType.present()) {
            // we must declare the contracts method
            classModel.addField(contractsField -> contractsField
                    .isStatic(true)
                    .isFinal(true)
                    .name("FACTORY_CONTRACTS")
                    .type(SET_OF_RESOLVED_TYPES)
                    .addContent(Set.class)
                    .addContent(".of(")
                    .update(it -> {
                        Iterator<ResolvedType> iterator = factoryContracts.iterator();
                        while (iterator.hasNext()) {
                            it.addContentCreate(iterator.next());
                            if (iterator.hasNext()) {
                                it.addContent(", ");
                            }
                        }
                    })
                    .addContent(")"));

            // Set<Class<?>> factoryContracts()
            classModel.addMethod(method -> method
                    .addAnnotation(Annotations.OVERRIDE)
                    .name("factoryContracts")
                    .returnType(SET_OF_RESOLVED_TYPES)
                    .addContentLine("return FACTORY_CONTRACTS;"));
        }
    }

    private void instantiateMethod(ClassModel.Builder classModel, CoreService service) {
        if (service.isAbstract()) {
            return;
        }

        // T instantiate(DependencyContext ctx__helidonRegistry)
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(service.serviceType())
                .name("instantiate")
                .addParameter(ctxParam -> ctxParam.type(ServiceCodegenTypes.SERVICE_DEPENDENCY_CONTEXT)
                        .name("ctx__helidonRegistry"))
                .update(it -> createInstantiateBody(service.serviceType(), it, service.dependencies())));
    }

    private void postConstructMethod(ClassModel.Builder classModel, CoreService service) {
        // postConstruct()
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT).ifPresent(method -> {
            classModel.addMethod(postConstruct -> postConstruct.name("postConstruct")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(service.serviceType())
                            .name("instance"))
                    .addContentLine("instance." + method.elementName() + "();"));
        });
    }

    private void preDestroyMethod(ClassModel.Builder classModel, CoreService service) {
        // preDestroy
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY).ifPresent(method -> {
            classModel.addMethod(preDestroy -> preDestroy.name("preDestroy")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(service.serviceType())
                            .name("instance"))
                    .addContentLine("instance." + method.elementName() + "();"));
        });
    }

    private Optional<TypedElementInfo> lifecycleMethod(TypeInfo typeInfo, TypeName annotationType) {
        List<TypedElementInfo> list = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.hasAnnotation(annotationType))
                .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        }
        if (list.size() > 1) {
            throw new IllegalStateException("There is more than one method annotated with " + annotationType.fqName()
                                                    + ", which is not allowed on type " + typeInfo.typeName().fqName());
        }
        TypedElementInfo method = list.getFirst();
        if (method.accessModifier() == AccessModifier.PRIVATE) {
            throw new CodegenException("Method annotated with " + annotationType.fqName()
                                               + ", is private, which is not supported: " + typeInfo.typeName().fqName()
                                               + "#" + method.elementName(),
                                       method.originatingElementValue());
        }
        if (!method.parameterArguments().isEmpty()) {
            throw new CodegenException("Method annotated with " + annotationType.fqName()
                                               + ", has parameters, which is not supported: " + typeInfo.typeName().fqName()
                                               + "#" + method.elementName(),
                                       method.originatingElementValue());
        }
        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw new CodegenException("Method annotated with " + annotationType.fqName()
                                               + ", is not void, which is not supported: " + typeInfo.typeName().fqName()
                                               + "#" + method.elementName(),
                                       method.originatingElementValue());
        }
        return Optional.of(method);
    }

    private void createInstantiateBody(TypeName serviceType,
                                       Method.Builder method,
                                       List<CoreDependency> params) {
        declareConstructorParams(method, params);
        String paramsDeclaration = params.stream()
                .map(CoreDependency::name)
                .collect(Collectors.joining(", "));

        // return new MyImpl(parameter, parameter2)
        method.addContent("return new ")
                .addContent(serviceType.genericTypeName())
                .addContent("(")
                .addContent(paramsDeclaration)
                .addContentLine(");");
    }

    private void isAbstractMethod(ClassModel.Builder classModel, CoreService service) {
        if (!service.isAbstract() && service.superType().empty()) {
            return;
        }
        // only override for abstract types (and subtypes, where we do not want to check if super is abstract), default is false
        classModel.addMethod(isAbstract -> isAbstract
                .name("isAbstract")
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .addAnnotation(Annotations.OVERRIDE)
                .addContentLine("return " + service.isAbstract() + ";"));
    }

    private void weightMethod(ClassModel.Builder classModel, CoreService service) {
        boolean hasSuperType = service.superType().present();
        // double weight()
        Optional<Double> weight = weight(typeInfo);

        if (!hasSuperType && weight.isEmpty()) {
            return;
        }
        double usedWeight = weight.orElse(Weighted.DEFAULT_WEIGHT);
        if (!hasSuperType && usedWeight == Weighted.DEFAULT_WEIGHT) {
            return;
        }

        classModel.addMethod(weightMethod -> weightMethod.name("weight")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.PRIMITIVE_DOUBLE)
                .addContentLine("return " + usedWeight + ";"));
    }

    private Optional<Double> weight(TypeInfo typeInfo) {
        return typeInfo.findAnnotation(TypeName.create(Weight.class))
                .flatMap(Annotation::doubleValue);
    }

    private TypeName descriptorInstanceType(TypeName serviceType, TypeName descriptorType) {
        return TypeName.builder(descriptorType)
                .addTypeArgument(serviceType)
                .build();
    }

    private interface DescriptorConsumer {
        void addDescriptor(String registryType,
                           TypeName serviceType,
                           TypeName descriptorType,
                           ClassModel.Builder descriptor,
                           double weight,
                           Set<ResolvedType> contracts,
                           Set<ResolvedType> factoryContracts,
                           Object... originatingElements);
    }
}
