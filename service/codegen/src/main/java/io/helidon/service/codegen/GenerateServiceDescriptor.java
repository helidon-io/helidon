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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
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
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER;
import static java.util.function.Predicate.not;

/**
 * Generates a service descriptor.
 */
class GenerateServiceDescriptor {
    static final TypeName SET_OF_TYPES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.TYPE_NAME)
            .build();
    private static final TypeName LIST_OF_DEPENDENCIES = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(ServiceCodegenTypes.SERVICE_DEPENDENCY)
            .build();
    private static final TypeName DESCRIPTOR_TYPE = TypeName.builder(ServiceCodegenTypes.SERVICE_DESCRIPTOR)
            .addTypeArgument(TypeName.create("T"))
            .build();
    private static final TypedElementInfo DEFAULT_CONSTRUCTOR = TypedElementInfo.builder()
            .typeName(TypeNames.OBJECT)
            .accessModifier(AccessModifier.PUBLIC)
            .kind(ElementKind.CONSTRUCTOR)
            .build();
    private static final TypeName ANY_GENERIC_TYPE = TypeName.builder(TypeNames.GENERIC_TYPE)
            .addTypeArgument(TypeName.create("?"))
            .build();

    private final TypeName generator;
    private final RegistryCodegenContext ctx;
    private final Collection<TypeInfo> services;
    private final TypeInfo typeInfo;
    private final boolean autoAddContracts;

    private GenerateServiceDescriptor(TypeName generator,
                                      RegistryCodegenContext ctx,
                                      Collection<TypeInfo> allServices,
                                      TypeInfo service) {
        this.generator = generator;
        this.ctx = ctx;
        this.services = allServices;
        this.typeInfo = service;
        this.autoAddContracts = ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES.value(ctx.options());
    }

    /**
     * Generate a service descriptor for the provided service type info.
     *
     * @param generator   type of the generator responsible for this event
     * @param ctx         context of code generation
     * @param allServices all services processed in this round of processing
     * @param service     service to create a descriptor for
     * @return class model builder of the service descriptor
     */
    static ClassModel.Builder generate(TypeName generator,
                                       RegistryCodegenContext ctx,
                                       Collection<TypeInfo> allServices,
                                       TypeInfo service) {
        return new GenerateServiceDescriptor(generator, ctx, allServices, service)
                .generate();
    }

    static List<ParamDefinition> declareCtrParamsAndGetThem(Method.Builder method, List<ParamDefinition> params) {
        List<ParamDefinition> constructorParams = params.stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .toList();

        // for each parameter, obtain its value from context
        for (ParamDefinition param : constructorParams) {
            method.addContent(param.declaredType())
                    .addContent(" ")
                    .addContent(param.ipParamName())
                    .addContent(" = ")
                    .update(it -> param.assignmentHandler().accept(it))
                    .addContentLine(";");
        }
        if (!params.isEmpty()) {
            method.addContentLine("");
        }
        return constructorParams;
    }

    private ClassModel.Builder generate() {
        TypeName serviceType = typeInfo.typeName();

        if (typeInfo.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("We can only generated service descriptors for classes, interface was requested: ",
                                       typeInfo.originatingElement().orElse(serviceType));
        }
        boolean isAbstractClass = typeInfo.elementModifiers().contains(Modifier.ABSTRACT)
                && typeInfo.kind() == ElementKind.CLASS;

        SuperType superType = superType(typeInfo, services);

        // this must result in generating a service descriptor file
        TypeName descriptorType = ctx.descriptorType(serviceType);

        List<ParamDefinition> params = params(typeInfo, constructor(typeInfo));

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

        Map<String, GenericTypeDeclaration> genericTypes = genericTypes(classModel, params);
        Set<TypeName> contracts = new HashSet<>();
        Set<String> collectedFullyQualifiedContracts = new HashSet<>();
        contracts(typeInfo, autoAddContracts, contracts, collectedFullyQualifiedContracts);

        // declare the class

        if (superType.hasSupertype()) {
            classModel.superType(superType.superDescriptorType());
        } else {
            classModel.addInterface(DESCRIPTOR_TYPE);
        }

        // Fields
        singletonInstanceField(classModel, serviceType, descriptorType);
        serviceTypeFields(classModel, serviceType, descriptorType);

        // public fields are last, so they do not intersect with private fields (it is not as nice to read)
        // they cannot be first, as they require some of the private fields
        dependencyFields(classModel, typeInfo, genericTypes, params);
        // dependencies require IP IDs, so they really must be last
        dependenciesField(classModel, params);

        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));

        // methods (some methods define fields as well)
        serviceTypeMethod(classModel);
        descriptorTypeMethod(classModel);
        contractsMethod(classModel, contracts);
        dependenciesMethod(classModel, params, superType);
        isAbstractMethod(classModel, superType, isAbstractClass);
        instantiateMethod(classModel, serviceType, params, isAbstractClass);
        postConstructMethod(typeInfo, classModel, serviceType);
        preDestroyMethod(typeInfo, classModel, serviceType);
        weightMethod(typeInfo, classModel, superType);

        // service type is an implicit contract
        Set<TypeName> allContracts = new HashSet<>(contracts);
        allContracts.add(serviceType);

        ctx.addDescriptor("core",
                          serviceType,
                          descriptorType,
                          classModel,
                          weight(typeInfo).orElse(Weighted.DEFAULT_WEIGHT),
                          allContracts,
                          typeInfo.originatingElement().orElseGet(typeInfo::typeName));

        return classModel;
    }

    private SuperType superType(TypeInfo typeInfo, Collection<TypeInfo> services) {
        // find super type if it is also a service (or has a service descriptor)

        // check if the super type is part of current annotation processing
        Optional<TypeInfo> superTypeInfoOptional = typeInfo.superTypeInfo();
        if (superTypeInfoOptional.isEmpty()) {
            return SuperType.noSuperType();
        }
        TypeInfo superType = superTypeInfoOptional.get();
        TypeName expectedSuperDescriptor = ctx.descriptorType(superType.typeName());
        TypeName superTypeToExtend = TypeName.builder(expectedSuperDescriptor)
                .addTypeArgument(TypeName.create("T"))
                .build();
        boolean isCore = superType.hasAnnotation(SERVICE_ANNOTATION_PROVIDER);
        if (!isCore) {
            throw new CodegenException("Service annotated with @Service.Provider extends invalid supertype,"
                                               + " the super type must also be a @Service.Provider. Type: "
                                               + typeInfo.typeName().fqName() + ", super type: "
                                               + superType.typeName().fqName());
        }
        for (TypeInfo service : services) {
            if (service.typeName().equals(superType.typeName())) {
                return new SuperType(true, superTypeToExtend, service, true);
            }
        }
        // if not found in current list, try checking existing types
        return ctx.typeInfo(expectedSuperDescriptor)
                .map(it -> new SuperType(true, superTypeToExtend, superType, true))
                .orElseGet(SuperType::noSuperType);
    }

    // there must be none, or one non-private constructor (actually there may be more, we just use the first)
    private TypedElementInfo constructor(TypeInfo typeInfo) {
        return typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .filter(not(ElementInfoPredicates::isPrivate))
                .findFirst()
                // or default constructor
                .orElse(DEFAULT_CONSTRUCTOR);
    }

    private List<ParamDefinition> params(
            TypeInfo service,
            TypedElementInfo constructor) {
        AtomicInteger paramCounter = new AtomicInteger();

        return constructor.parameterArguments()
                .stream()
                .map(param -> {
                    String constantName = "PARAM_" + paramCounter.getAndIncrement();
                    RegistryCodegenContext.Assignment assignment = translateParameter(param.typeName(), constantName);
                    return new ParamDefinition(constructor,
                                               null,
                                               param,
                                               constantName,
                                               param.typeName(),
                                               assignment.usedType(),
                                               assignment.codeGenerator(),
                                               ElementKind.CONSTRUCTOR,
                                               constructor.elementName(),
                                               param.elementName(),
                                               param.elementName(),
                                               false,
                                               param.annotations(),
                                               Set.of(),
                                               contract(service.typeName()
                                                                .fqName() + " Constructor parameter: " + param.elementName(),
                                                        assignment.usedType()),
                                               constructor.accessModifier(),
                                               "<init>");
                })
                .toList();
    }

    private TypeName contract(String description, TypeName typeName) {
        /*
         get the contract expected for this dependency
         IP may be:
          - Optional
          - List
          - ServiceProvider
          - Supplier
          - Optional<ServiceProvider>
          - Optional<Supplier>
          - List<ServiceProvider>
          - List<Supplier>
         */

        if (typeName.isOptional()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Dependency with Optional type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.isList()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Dependency with List type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.isSupplier()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Dependency with Supplier type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }

        return typeName;
    }

    private Map<String, GenericTypeDeclaration> genericTypes(ClassModel.Builder classModel,
                                                             List<ParamDefinition> params) {
        // we must use map by string (as type name is equal if the same class, not full generic declaration)
        Map<String, GenericTypeDeclaration> result = new LinkedHashMap<>();
        AtomicInteger counter = new AtomicInteger();

        for (ParamDefinition param : params) {
            result.computeIfAbsent(param.translatedType().resolvedName(),
                                   type -> {
                                       var response =
                                               new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                          param.declaredType());
                                       addTypeConstant(classModel, param.translatedType(), response);
                                       return response;
                                   });
            result.computeIfAbsent(param.contract().fqName(),
                                   type -> {
                                       var response =
                                               new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                          param.declaredType());
                                       addTypeConstant(classModel, param.contract(), response);
                                       return response;
                                   });
        }

        return result;
    }

    private void addTypeConstant(ClassModel.Builder classModel,
                                 TypeName typeName,
                                 GenericTypeDeclaration generic) {
        String stringType = typeName.resolvedName();
        // constants for dependency parameter types (used by next section)
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(TypeNames.TYPE_NAME)
                .name(generic.constantName())
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
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(ANY_GENERIC_TYPE)
                .name("G" + generic.constantName())
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
        typeInfo.findAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT)
                .ifPresent(it -> collectedContracts.add(typeInfo.typeName()));

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

    private void serviceTypeFields(ClassModel.Builder classModel, TypeName serviceType, TypeName descriptorType) {
        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("SERVICE_TYPE")
                .addContentCreate(serviceType.genericTypeName()));

        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("DESCRIPTOR_TYPE")
                .addContentCreate(descriptorType.genericTypeName()));
    }

    private void dependencyFields(ClassModel.Builder classModel,
                                  TypeInfo service,
                                  Map<String, GenericTypeDeclaration> genericTypes,
                                  List<ParamDefinition> params) {
        // constant for dependency
        for (ParamDefinition param : params) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PUBLIC)
                    .isStatic(true)
                    .isFinal(true)
                    .type(ServiceCodegenTypes.SERVICE_DEPENDENCY)
                    .name(param.constantName())
                    .description(dependencyDescription(service, param))
                    .update(it -> {
                        it.addContent(ServiceCodegenTypes.SERVICE_DEPENDENCY)
                                .addContentLine(".builder()")
                                .increaseContentPadding()
                                .increaseContentPadding()
                                .addContent(".typeName(")
                                .addContent(genericTypes.get(param.translatedType().resolvedName()).constantName())
                                .addContentLine(")")
                                .update(maybeElementKind -> {
                                    if (param.kind() != ElementKind.CONSTRUCTOR) {
                                        // constructor is default and does not need to be defined
                                        maybeElementKind.addContent(".elementKind(")
                                                .addContent(TypeNames.ELEMENT_KIND)
                                                .addContent(".")
                                                .addContent(param.kind().name())
                                                .addContentLine(")");
                                    }
                                })
                                .update(maybeMethod -> {
                                    if (param.kind() == ElementKind.METHOD) {
                                        maybeMethod.addContent(".method(")
                                                .addContent(param.methodConstantName())
                                                .addContentLine(")");
                                    }
                                })
                                .addContent(".name(\"")
                                .addContent(param.fieldId())
                                .addContentLine("\")")
                                .addContentLine(".service(SERVICE_TYPE)")
                                .addContentLine(".descriptor(DESCRIPTOR_TYPE)")
                                .addContent(".descriptorConstant(\"")
                                .addContent(param.constantName())
                                .addContentLine("\")")
                                .addContent(".contract(")
                                .addContent(genericTypes.get(param.contract().fqName()).constantName())
                                .addContentLine(")")
                                .addContent(".contractType(G")
                                .addContent(genericTypes.get(param.contract().fqName()).constantName())
                                .addContentLine(")");
                        if (param.access() != AccessModifier.PACKAGE_PRIVATE) {
                            it.addContent(".access(")
                                    .addContent(TypeNames.ACCESS_MODIFIER)
                                    .addContent(".")
                                    .addContent(param.access().name())
                                    .addContentLine(")");
                        }

                        if (param.isStatic()) {
                            it.addContentLine(".isStatic(true)");
                        }

                        if (!param.qualifiers().isEmpty()) {
                            for (Annotation qualifier : param.qualifiers()) {
                                it.addContent(".addQualifier(qualifier -> qualifier.typeName(")
                                        .addContentCreate(qualifier.typeName().genericTypeName())
                                        .addContent(")");
                                qualifier.value().ifPresent(q -> it.addContent(".value(\"")
                                        .addContent(q)
                                        .addContent("\")"));
                                it.addContentLine(")");
                            }
                        }

                        it.addContent(".build()")
                                .decreaseContentPadding()
                                .decreaseContentPadding();
                    }));
        }
    }

    private String dependencyDescription(TypeInfo service, ParamDefinition param) {
        TypeName serviceType = service.typeName();
        StringBuilder result = new StringBuilder("Dependency for ");
        boolean servicePublic = service.accessModifier() == AccessModifier.PUBLIC;
        boolean elementPublic = param.owningElement().accessModifier() == AccessModifier.PUBLIC;

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
                    .append(toDescriptionSignature(param.owningElement(), true))
                    .append(")")
                    .append("}");
        } else {
            // just text
            result.append("(")
                    .append(toDescriptionSignature(param.owningElement(), false))
                    .append(")");
        }

        result
                .append(", parameter ")
                .append(param.elementInfo().elementName())
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

    private void dependenciesField(ClassModel.Builder classModel, List<ParamDefinition> params) {
        classModel.addField(dependencies -> dependencies
                .isStatic(true)
                .isFinal(true)
                .name("DEPENDENCIES")
                .type(LIST_OF_DEPENDENCIES)
                .addContent(List.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<ParamDefinition> iterator = params.iterator();
                    while (iterator.hasNext()) {
                        it.addContent(iterator.next().constantName());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }

    private void serviceTypeMethod(ClassModel.Builder classModel) {
        // TypeName serviceType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("serviceType")
                .addContentLine("return SERVICE_TYPE;"));
    }

    private void descriptorTypeMethod(ClassModel.Builder classModel) {
        // TypeName descriptorType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("descriptorType")
                .addContentLine("return DESCRIPTOR_TYPE;"));
    }

    private void contractsMethod(ClassModel.Builder classModel, Set<TypeName> contracts) {
        if (contracts.isEmpty()) {
            return;
        }
        classModel.addField(contractsField -> contractsField
                .isStatic(true)
                .isFinal(true)
                .name("CONTRACTS")
                .type(SET_OF_TYPES)
                .addContent(Set.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<TypeName> iterator = contracts.iterator();
                    while (iterator.hasNext()) {
                        it.addContentCreate(iterator.next().genericTypeName());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));

        // Set<Class<?>> contracts()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .name("contracts")
                .returnType(SET_OF_TYPES)
                .addContentLine("return CONTRACTS;"));
    }

    private void dependenciesMethod(ClassModel.Builder classModel, List<ParamDefinition> params, SuperType superType) {
        // List<Dependency> dependencies()
        boolean hasSuperType = superType.hasSupertype();
        if (hasSuperType || !params.isEmpty()) {
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

    private void instantiateMethod(ClassModel.Builder classModel,
                                   TypeName serviceType,
                                   List<ParamDefinition> params,
                                   boolean isAbstractClass) {
        if (isAbstractClass) {
            return;
        }

        // T instantiate(DependencyContext ctx__helidonRegistry)
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(serviceType)
                .name("instantiate")
                .addParameter(ctxParam -> ctxParam.type(ServiceCodegenTypes.SERVICE_DEPENDENCY_CONTEXT)
                        .name("ctx__helidonRegistry"))
                .update(it -> createInstantiateBody(serviceType, it, params)));
    }

    private void postConstructMethod(TypeInfo typeInfo, ClassModel.Builder classModel, TypeName serviceType) {
        // postConstruct()
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT).ifPresent(method -> {
            classModel.addMethod(postConstruct -> postConstruct.name("postConstruct")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(serviceType)
                            .name("instance"))
                    .addContentLine("instance." + method.elementName() + "();"));
        });
    }

    private void preDestroyMethod(TypeInfo typeInfo, ClassModel.Builder classModel, TypeName serviceType) {
        // preDestroy
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY).ifPresent(method -> {
            classModel.addMethod(preDestroy -> preDestroy.name("preDestroy")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(serviceType)
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
                                       method.originatingElement().orElseGet(method::elementName));
        }
        if (!method.parameterArguments().isEmpty()) {
            throw new CodegenException("Method annotated with " + annotationType.fqName()
                                                    + ", has parameters, which is not supported: " + typeInfo.typeName().fqName()
                                                    + "#" + method.elementName(),
                                       method.originatingElement().orElseGet(method::elementName));
        }
        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw new CodegenException("Method annotated with " + annotationType.fqName()
                                                    + ", is not void, which is not supported: " + typeInfo.typeName().fqName()
                                                    + "#" + method.elementName(),
                                       method.originatingElement().orElseGet(method::elementName));
        }
        return Optional.of(method);
    }

    private void createInstantiateBody(TypeName serviceType,
                                       Method.Builder method,
                                       List<ParamDefinition> params) {
        List<ParamDefinition> constructorParams = declareCtrParamsAndGetThem(method, params);
        String paramsDeclaration = constructorParams.stream()
                .map(ParamDefinition::ipParamName)
                .collect(Collectors.joining(", "));

        // return new MyImpl(parameter, parameter2)
        method.addContent("return new ")
                .addContent(serviceType.genericTypeName())
                .addContent("(")
                .addContent(paramsDeclaration)
                .addContentLine(");");
    }

    private void isAbstractMethod(ClassModel.Builder classModel, SuperType superType, boolean isAbstractClass) {
        if (!isAbstractClass && !superType.hasSupertype()) {
            return;
        }
        // only override for abstract types (and subtypes, where we do not want to check if super is abstract), default is false
        classModel.addMethod(isAbstract -> isAbstract
                .name("isAbstract")
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .addAnnotation(Annotations.OVERRIDE)
                .addContentLine("return " + isAbstractClass + ";"));
    }

    private void weightMethod(TypeInfo typeInfo, ClassModel.Builder classModel, SuperType superType) {
        boolean hasSuperType = superType.hasSupertype();
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

    private RegistryCodegenContext.Assignment translateParameter(TypeName typeName, String constantName) {
        return ctx.assignment(typeName, "ctx__helidonRegistry.dependency(" + constantName + ")");
    }

    private TypeName descriptorInstanceType(TypeName serviceType, TypeName descriptorType) {
        return TypeName.builder(descriptorType)
                .addTypeArgument(serviceType)
                .build();
    }

    private record GenericTypeDeclaration(String constantName,
                                          TypeName typeName) {
    }
}
