/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.spi.InjectAssignment;
import io.helidon.service.codegen.spi.InjectCodegenObserver;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.service.codegen.CodegenHelper.annotationsField;
import static io.helidon.service.codegen.ServiceCodegenTypes.ANY_GENERIC_TYPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.BUILDER_BLUEPRINT;
import static io.helidon.service.codegen.ServiceCodegenTypes.DEPENDENCY_CARDINALITY;
import static io.helidon.service.codegen.ServiceCodegenTypes.GENERIC_T_TYPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_DELEGATE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_EXTERNAL_DELEGATE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_EXCEPTION;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPT_METADATA;
import static io.helidon.service.codegen.ServiceCodegenTypes.LIST_OF_DEPENDENCIES;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_ENTRY_POINT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PER_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PER_LOOKUP;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_RUN_LEVEL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SCOPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_DEPENDENCY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_FACTORY_TYPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_G_DEPENDENCY_SUPPORT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_G_PER_INSTANCE_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_G_QUALIFIED_FACTORY_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_G_SCOPE_HANDLER_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_SCOPE_HANDLER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_SERVICE_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_QUALIFIERS;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_RESOLVED_TYPES;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_STRINGS;
import static java.util.function.Predicate.not;

/**
 * Code generator of service descriptor for Helidon Services.
 */
public class ServiceDescriptorCodegen {
    private static final TypeName GENERATOR = TypeName.create(ServiceExtension.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.builder(ServiceCodegenTypes.SERVICE_DESCRIPTOR)
            .addTypeArgument(TypeName.create("T"))
            .build();

    private final InterceptionSupport interception;
    private final Assignments assignments;
    private final Set<TypeName> scopeMetaAnnotations;
    private final List<InjectCodegenObserver> observers;
    private final RegistryCodegenContext ctx;

    private String packageName;

    ServiceDescriptorCodegen(RegistryCodegenContext ctx,
                             List<InjectCodegenObserver> observers,
                             InterceptionSupport interceptionSupport) {
        this.ctx = ctx;
        this.observers = observers;
        this.interception = interceptionSupport;
        this.assignments = new Assignments(ctx);

        CodegenOptions options = ctx.options();
        this.scopeMetaAnnotations = ServiceOptions.SCOPE_META_ANNOTATIONS.value(options);

        this.packageName = CodegenOptions.CODEGEN_PACKAGE.findValue(options)
                .orElse(null);
    }

    /**
     * When used from outside, we do not support interception
     * This method is intended for types generated by a Maven plugin, that have limited functions.
     *
     * @param ctx codegen context
     * @return a new codegen instance
     */
    public static ServiceDescriptorCodegen create(RegistryCodegenContext ctx) {
        Interception interception = new Interception(InterceptionStrategy.NONE);
        return new ServiceDescriptorCodegen(ctx,
                                            List.of(),
                                            new InterceptionSupport(ctx, interception));
    }

    /**
     * Describe a service type.
     *
     * @param generator type of the generator
     * @param roundCtx  round context
     * @param services  all types that are going to be described
     * @param typeInfo  type info of the service type
     */
    @SuppressWarnings("checkstyle:MethodLength") // this already extracting the necessary steps
    public void service(TypeName generator, RegistryRoundContext roundCtx, Collection<TypeInfo> services, TypeInfo typeInfo) {
        packageName(roundCtx);

        if (typeInfo.kind() == ElementKind.INTERFACE || typeInfo.kind() == ElementKind.ANNOTATION_TYPE) {
            // we cannot support multiple inheritance, so full descriptors for interfaces do not make sense
            return;
        }

        TypeName scope = scope(typeInfo);

        DescribedService service = DescribedService.create(ctx,
                                                           roundCtx,
                                                           interception,
                                                           typeInfo,
                                                           superType(typeInfo, services),
                                                           scope);
        DescribedType serviceDescriptor = service.serviceDescriptor();
        DescribedElements serviceElements = serviceDescriptor.elements();
        TypeName serviceTypeName = serviceDescriptor.typeName();

        List<ParamDefinition> params = new ArrayList<>();
        List<MethodDefinition> methods = new ArrayList<>();

        TypedElementInfo constructorInjectElement = injectConstructor(typeInfo);
        List<TypedElementInfo> fieldInjectElements = fieldInjectElements(typeInfo);

        params(services,
               service,
               methods,
               params,
               constructorInjectElement,
               fieldInjectElements);

        notifyIpObservers(roundCtx, service, params);

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 serviceTypeName,
                                                 service.descriptorType()))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               serviceTypeName,
                                                               service.descriptorType(),
                                                               "1",
                                                               ""))
                .type(service.descriptorType())
                .addGenericArgument(TypeArgument.create("T extends " + serviceTypeName.fqName()))
                .javadoc(Javadoc.builder()
                                 .add("Service descriptor for {@link " + serviceTypeName.fqName() + "}.")
                                 .addGenericArgument("T", "type of the service, for extensibility")
                                 .build())
                // we need to keep insertion order, as constants may depend on each other
                .sortStaticFields(false);

        singletonInstanceField(classModel, service);

        Map<String, GenericTypeDeclaration> genericTypes = genericTypes(classModel, params, methods);

        var contracts = serviceDescriptor.contracts();
        Set<ResolvedType> factoryContracts;

        if (service.isFactory()) {
            if (serviceTypeName.className().endsWith("__Interception_Wrapper")) {
                contracts = service.providedDescriptor().contracts();
                factoryContracts = service.serviceDescriptor().contracts();
            } else {
                // check if contracts are intercepted
                var providedElements = service.providedDescriptor().elements();

                if (providedElements.methodsIntercepted()) {
                    // remove contracts from the original service, service descriptor will only be used to instantiate provider
                    contracts = Set.of();
                    factoryContracts = Set.of();
                    // generate delegate injection (unless already generated) in current package
                    TypeName delegateType = generateProvidedInterceptionDelegate(roundCtx, service);
                    // then generate a service that injects the original service, and wraps provider method(s) using delegation
                    generateDelegationService(roundCtx, service, delegateType);
                } else {
                    contracts = service.providedDescriptor().contracts();
                    factoryContracts = service.serviceDescriptor().contracts();
                }
            }
        } else {
            factoryContracts = Set.of();
        }

        // declare the class
        if (service.superType().present()) {
            classModel.superType(service.superType().descriptorType());
        } else {
            classModel.addInterface(DESCRIPTOR_TYPE);
        }

        // the basic fields and methods
        serviceTypeMethod(classModel, service);
        providedTypeMethod(classModel, service);
        descriptorTypeMethod(classModel, service);
        scopeMethod(classModel, service);
        contractsMethod(classModel, service, contracts, factoryContracts);
        qualifiersMethod(classModel, service);

        // Additional fields

        methodFields(classModel, methods);
        methodElementFields(classModel, service);

        // public fields are last, so they do not intersect with private fields (it is not as nice to read)
        // they cannot be first, as they require some of the private fields
        injectionPointFields(classModel, typeInfo, genericTypes, params);
        // dependencies require IP IDs, so they really must be last
        dependenciesField(classModel, params);
        // annotations of the type
        annotationsField(classModel, serviceDescriptor.typeInfo());

        if (serviceElements.intercepted()) {
            // if constructor intercepted, add its element
            if (serviceElements.constructorIntercepted()) {
                constructorElementField(classModel, constructorInjectElement);
            }
            // if injected field intercepted, add its element (other fields cannot be intercepted)
            fieldInjectElements.stream()
                    .filter(it -> isIntercepted(serviceElements.interceptedElements(), it))
                    .forEach(fieldElement -> fieldElementField(classModel, fieldElement));
            // all other interception is done on method level and is handled by the
            // service descriptor delegating to a generated type
        }

        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));

        // methods (some methods define fields as well)
        dependenciesMethod(classModel, service, params);
        isAbstractMethod(classModel, service);
        instantiateMethod(classModel, service, params);
        injectMethod(classModel, service, params, methods);
        postConstructMethod(classModel, service);
        preDestroyMethod(classModel, service);
        weightMethod(classModel, service);
        runLevelMethod(classModel, service);
        createForMethod(classModel, service);
        qualifiedProvider(classModel, service);
        scopeHandler(typeInfo, classModel, contracts);
        factoryType(classModel, service, service.providerType());

        // service type is an implicit contract
        Set<ResolvedType> metaInfServiceContracts = new HashSet<>(contracts);
        Set<ResolvedType> metaInfFactoryContracts = new HashSet<>(factoryContracts);
        if (service.providedDescriptor() == null || contracts.isEmpty()) {
            // this is either NOT a factory, or it is delegated for interception
            metaInfServiceContracts.add(ResolvedType.create(serviceTypeName));
        } else {
            metaInfFactoryContracts.add(ResolvedType.create(serviceTypeName));
        }

        roundCtx.addDescriptor(serviceTypeName,
                               service.descriptorType(),
                               classModel,
                               weight(typeInfo).orElse(Weighted.DEFAULT_WEIGHT),
                               metaInfServiceContracts,
                               metaInfFactoryContracts,
                               typeInfo.originatingElementValue());

        if (serviceElements.methodsIntercepted()) {
            generateInterceptedType(roundCtx, typeInfo, service, constructorInjectElement);
        }
    }

    /**
     * Describe a service type.
     *
     * @param roundCtx round context
     * @param services all types that are going to be described
     * @param typeInfo type info of the service type
     */
    void service(RegistryRoundContext roundCtx, Collection<TypeInfo> services, TypeInfo typeInfo) {
        service(GENERATOR, roundCtx, services, typeInfo);
    }

    /**
     * Describe a type annotated with {@code @Service.Describe}.
     *
     * @param roundCtx           round context
     * @param typeInfo           type info of the annotated type
     * @param describeAnnotation describe annotation
     */
    void describe(RegistryRoundContext roundCtx, TypeInfo typeInfo, Annotation describeAnnotation) {
        packageName(roundCtx);

        DescribedService service = DescribedService.create(ctx,
                                                           roundCtx,
                                                           interception,
                                                           typeInfo,
                                                           ServiceSuperType.create(),
                                                           describeAnnotation.typeValue().orElse(SERVICE_ANNOTATION_SINGLETON));

        DescribedType serviceDescriptor = service.serviceDescriptor();
        TypeName serviceTypeName = serviceDescriptor.typeName();

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 serviceTypeName,
                                                 service.descriptorType()))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               serviceTypeName,
                                                               service.descriptorType(),
                                                               "1",
                                                               ""))
                .addInterface(DESCRIPTOR_TYPE)
                .type(service.descriptorType())
                .addGenericArgument(TypeArgument.create("T extends " + serviceTypeName.fqName()))
                .javadoc(Javadoc.builder()
                                 .add("Service descriptor for {@link " + serviceTypeName.fqName() + "}.")
                                 .addGenericArgument("T", "type of the service, for extensibility")
                                 .build())
                // we need to keep insertion order, as constants may depend on each other
                .sortStaticFields(false);

        var contracts = serviceDescriptor.contracts();

        singletonInstanceField(classModel, service);

        serviceTypeMethod(classModel, service);
        providedTypeMethod(classModel, service);
        descriptorTypeMethod(classModel, service);
        scopeMethod(classModel, service);
        contractsMethod(classModel, service, contracts, Set.of());

        // annotations of the type
        annotationsField(classModel, serviceDescriptor.typeInfo());
        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));
        // methods (some methods define fields as well)
        qualifiersMethod(classModel, service);
        weightMethod(classModel, service);
        runLevelMethod(classModel, service);
        factoryType(classModel, service, FactoryType.NONE);

        // service type is an implicit contract
        Set<ResolvedType> serviceContracts = new HashSet<>(contracts);
        serviceContracts.add(ResolvedType.create(serviceTypeName));

        roundCtx.addDescriptor(serviceTypeName,
                               service.descriptorType(),
                               classModel,
                               weight(serviceDescriptor.typeInfo()).orElse(Weighted.DEFAULT_WEIGHT),
                               serviceContracts,
                               Set.of(),
                               serviceDescriptor.typeInfo().originatingElementValue());
    }

    private static void addAnnotationValue(ContentBuilder<?> contentBuilder, Object objectValue) {
        switch (objectValue) {
        case String value -> contentBuilder.addContent("\"" + value + "\"");
        case Boolean value -> contentBuilder.addContent(String.valueOf(value));
        case Long value -> contentBuilder.addContent(String.valueOf(value) + 'L');
        case Double value -> contentBuilder.addContent(String.valueOf(value) + 'D');
        case Integer value -> contentBuilder.addContent(String.valueOf(value));
        case Byte value -> contentBuilder.addContent("(byte)" + value);
        case Character value -> contentBuilder.addContent("'" + value + "'");
        case Short value -> contentBuilder.addContent("(short)" + value);
        case Float value -> contentBuilder.addContent(String.valueOf(value) + 'F');
        case Class<?> value -> contentBuilder.addContentCreate(TypeName.create(value));
        case TypeName value -> contentBuilder.addContentCreate(value);
        case Annotation value -> contentBuilder.addContentCreate(value);
        case Enum<?> value -> toEnumValue(contentBuilder, value);
        case List<?> values -> toListValues(contentBuilder, values);
        default -> throw new IllegalStateException("Unexpected annotation value type " + objectValue.getClass()
                .getName() + ": " + objectValue);
        }
    }

    private static void toListValues(ContentBuilder<?> contentBuilder, List<?> values) {
        contentBuilder.addContent(List.class)
                .addContent(".of(");
        int size = values.size();
        for (int i = 0; i < size; i++) {
            Object value = values.get(i);
            addAnnotationValue(contentBuilder, value);
            if (i != size - 1) {
                contentBuilder.addContent(",");
            }
        }
        contentBuilder.addContent(")");
    }

    private static void toEnumValue(ContentBuilder<?> contentBuilder, Enum<?> enumValue) {
        contentBuilder.addContent(enumValue.getDeclaringClass())
                .addContent(".")
                .addContent(enumValue.name());
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

    private static List<ParamDefinition> declareCtrParamsAndGetThem(Method.Builder method, List<ParamDefinition> params) {
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

    private void notifyIpObservers(RegistryRoundContext roundContext, DescribedService service, List<ParamDefinition> params) {
        if (observers.isEmpty()) {
            return;
        }

        for (ParamDefinition param : params) {
            TypeInfo typeInfo = service.serviceDescriptor().typeInfo();
            TypedElementInfo owningElement = param.owningElement();
            TypedElementInfo ipElement = param.elementInfo();
            observers.forEach(it -> it.onInjectionPoint(
                    roundContext,
                    typeInfo,
                    owningElement,
                    ipElement));
        }
    }

    private void packageName(RegistryRoundContext roundCtx) {
        if (this.packageName == null) {
            // first try from module
            this.packageName = ctx.module()
                    .flatMap(ModuleInfo::firstUnqualifiedExport)
                    .orElse(null);
            // then use the first from source code
            if (packageName == null) {
                packageName = roundCtx.types()
                        .stream()
                        .map(TypeInfo::typeName)
                        .map(TypeName::packageName)
                        .findFirst()
                        .orElse(null);
            }
        }
    }

    private TypeName scope(TypeInfo service) {
        Set<TypeName> result = new LinkedHashSet<>();

        for (Annotation anno : service.annotations()) {
            TypeName annoType = anno.typeName();
            if (service.hasMetaAnnotation(annoType, SERVICE_ANNOTATION_SCOPE)) {
                result.add(annoType);
                continue;
            }
            for (TypeName scopeMetaAnnotation : scopeMetaAnnotations) {
                if (service.hasMetaAnnotation(annoType, scopeMetaAnnotation)) {
                    result.add(annoType);
                }
            }
        }

        if (result.size() > 1) {
            throw new CodegenException("Type " + service.typeName().fqName() + " has more than one scope defined. "
                                               + "This is not supported. Scopes. " + result);
        }

        if (!result.isEmpty()) {
            return result.iterator().next();
        }

        if (service.hasAnnotation(SERVICE_ANNOTATION_PER_INSTANCE)) {
            return SERVICE_ANNOTATION_SINGLETON;
        }

        if (service.hasAnnotation(SERVICE_ANNOTATION_PROVIDER)) {
            // if supplier, per lookup, otherwise singleton; will be removed once we remove Service.Provider (if we remove it)
            if (service.interfaceTypeInfo()
                    .stream()
                    .map(TypeInfo::typeName)
                    .anyMatch(TypeNames.SUPPLIER::equals)) {
                return SERVICE_ANNOTATION_PER_LOOKUP;
            }
            return SERVICE_ANNOTATION_SINGLETON;
        }

        return SERVICE_ANNOTATION_PER_LOOKUP;
    }

    private ServiceSuperType superType(TypeInfo typeInfo, Collection<TypeInfo> services) {
        // find super type if it is also a service (or has a service descriptor)

        // check if the super type is part of current annotation processing
        Optional<TypeInfo> superTypeInfoOptional = typeInfo.superTypeInfo();
        if (superTypeInfoOptional.isEmpty()) {
            return ServiceSuperType.create();
        }
        TypeInfo superType = superTypeInfoOptional.get();

        TypeName expectedSuperDescriptor = ctx.descriptorType(superType.typeName());
        TypeName superTypeToExtend = TypeName.builder(expectedSuperDescriptor)
                .addTypeArgument(TypeName.create("T"))
                .build();
        for (TypeInfo service : services) {
            if (service.typeName().equals(superType.typeName())) {
                return ServiceSuperType.create(service, superTypeToExtend);
            }
        }
        // if not found in current list, try checking existing types
        return ctx.typeInfo(expectedSuperDescriptor)
                .map(it -> ServiceSuperType.create(superType, superTypeToExtend))
                .orElseGet(ServiceSuperType::create);
    }

    // find constructor with @Inject, if none, find the first constructor (assume @Inject)
    private TypedElementInfo injectConstructor(TypeInfo typeInfo) {
        var constructors = typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .filter(it -> it.hasAnnotation(SERVICE_ANNOTATION_INJECT))
                .collect(Collectors.toUnmodifiableList());
        if (constructors.size() > 1) {
            throw new CodegenException("There can only be one constructor annotated with "
                                               + SERVICE_ANNOTATION_INJECT.fqName() + ", but there were "
                                               + constructors.size(),
                                       typeInfo.originatingElementValue());
        }
        if (!constructors.isEmpty()) {
            // @Service.Inject
            TypedElementInfo first = constructors.getFirst();
            if (ElementInfoPredicates.isPrivate(first)) {
                throw new CodegenException("Constructor annotated with " + SERVICE_ANNOTATION_INJECT.fqName()
                                                   + " must not be private.");
            }
            return first;
        }

        // or first non-private constructor
        var allConstructors = typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .collect(Collectors.toUnmodifiableList());

        if (allConstructors.isEmpty()) {
            // there is no constructor declared, we can use default
            return TypedElements.DEFAULT_CONSTRUCTOR.element();
        }
        var nonPrivateConstructors = allConstructors.stream()
                .filter(not(ElementInfoPredicates::isPrivate))
                .collect(Collectors.toUnmodifiableList());
        if (nonPrivateConstructors.isEmpty()) {
            throw new CodegenException("There is no non-private constructor defined for " + typeInfo.typeName().fqName(),
                                       typeInfo.originatingElementValue());
        }
        if (nonPrivateConstructors.size() > 1) {
            throw new CodegenException("There are more non-private constructors defined for " + typeInfo.typeName().fqName(),
                                       typeInfo.originatingElementValue());
        }
        return nonPrivateConstructors.getFirst();
    }

    private List<TypedElementInfo> fieldInjectElements(TypeInfo typeInfo) {
        var injectFields = typeInfo.elementInfo()
                .stream()
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::isField)
                .filter(ElementInfoPredicates.hasAnnotation(SERVICE_ANNOTATION_INJECT))
                .toList();
        var firstFound = injectFields.stream()
                .filter(ElementInfoPredicates::isPrivate)
                .findFirst();
        if (firstFound.isPresent()) {
            if (typeInfo.kind() == ElementKind.RECORD) {
                throw new CodegenException("Discovered " + SERVICE_ANNOTATION_INJECT.fqName()
                                                   + " annotation on record field(s). This is not supported. "
                                                   + "If this is the only constructor, you can remove the Inject annotation; "
                                                   + "if you need to inject the default constructor, kindly create an explicit"
                                                   + " default constructor and annotate it with Inject.",
                                           firstFound.get().originatingElementValue());
            }
            throw new CodegenException("Discovered " + SERVICE_ANNOTATION_INJECT.fqName()
                                               + " annotation on private field(s). We cannot support private field injection.",
                                       firstFound.get().originatingElementValue());
        }
        firstFound = injectFields.stream()
                .filter(ElementInfoPredicates::isStatic)
                .findFirst();
        if (firstFound.isPresent()) {
            throw new CodegenException("Discovered " + SERVICE_ANNOTATION_INJECT.fqName()
                                               + " annotation on static field(s).",
                                       firstFound.get().originatingElementValue());
        }
        return injectFields;
    }

    private void params(Collection<TypeInfo> services,
                        DescribedService describedService,
                        List<MethodDefinition> methods,
                        List<ParamDefinition> params,
                        TypedElementInfo constructor,
                        List<TypedElementInfo> fieldInjectElements) {
        AtomicInteger paramCounter = new AtomicInteger();
        AtomicInteger methodCounter = new AtomicInteger();

        if (!constructor.parameterArguments().isEmpty()) {
            injectConstructorParams(describedService, params, paramCounter, constructor);
        }

        fieldInjectElements
                .forEach(it -> fieldParam(describedService, params, paramCounter, it));

        methods.addAll(methodParams(services,
                                    describedService,
                                    params,
                                    methodCounter,
                                    paramCounter));

    }

    private List<MethodDefinition> methodParams(Collection<TypeInfo> services,
                                                DescribedService service,
                                                List<ParamDefinition> allParams,
                                                AtomicInteger methodCounter,
                                                AtomicInteger paramCounter) {
        TypeName serviceType = service.serviceDescriptor().typeName();
        TypeInfo serviceTypeInfo = service.serviceDescriptor().typeInfo();

        // Discover all methods on this type that are not private or static and that have @Inject
        List<TypedElementInfo> atInjectMethods = serviceTypeInfo.elementInfo()
                .stream()
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates.hasAnnotation(SERVICE_ANNOTATION_INJECT))
                .toList();

        List<MethodDefinition> result = new ArrayList<>();
        // add all @Inject methods(always)
        // there is no supertype, no need to check anything else
        atInjectMethods.stream()
                .map(it -> {
                    TypeName declaringType;
                    boolean overrides;

                    if (service.superType().present()) {
                        declaringType = overrides(services,
                                                  service.superType().typeInfo(),
                                                  it,
                                                  it.parameterArguments()
                                                          .stream()
                                                          .map(TypedElementInfo::typeName)
                                                          .toList(),
                                                  serviceType.packageName())
                                .orElse(serviceType);
                        overrides = !declaringType.equals(serviceType);
                    } else {
                        declaringType = serviceType;
                        overrides = false;
                    }
                    return toMethodDefinition(service,
                                              allParams,
                                              methodCounter,
                                              paramCounter,
                                              it,
                                              declaringType,
                                              overrides,
                                              true);
                })
                .forEach(result::add);

        if (service.superType().empty()) {
            return result;
        }

        // discover all methods that are not private or static and that do NOT have @Inject
        List<TypedElementInfo> otherMethods = serviceTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(it -> !it.hasAnnotation(SERVICE_ANNOTATION_INJECT))
                .toList();

        // some of the methods we declare that do not have @Inject may disable injection, we need to check that

        for (TypedElementInfo otherMethod : otherMethods) {
            // now find all methods that override a method that is annotated from any supertype (ouch)
            Optional<TypeName> overrides = overrides(services,
                                                     service.superType().typeInfo(),
                                                     otherMethod,
                                                     otherMethod.parameterArguments()
                                                             .stream()
                                                             .map(TypedElementInfo::typeName)
                                                             .toList(),
                                                     serviceType.packageName(),
                                                     SERVICE_ANNOTATION_INJECT);
            if (overrides.isPresent()) {
                // we do override a method, we need to declare it
                result.add(toMethodDefinition(service,
                                              allParams,
                                              methodCounter,
                                              paramCounter,
                                              otherMethod,
                                              overrides.get(),
                                              true,
                                              false));
            }
        }
        return result;
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // there is no sense in creating an object when all are required
    private MethodDefinition toMethodDefinition(DescribedService service,
                                                List<ParamDefinition> allParams,
                                                AtomicInteger methodCounter,
                                                AtomicInteger paramCounter,
                                                TypedElementInfo method,
                                                TypeName declaringType,
                                                boolean overrides,
                                                boolean isInjectionPoint) {

        int methodIndex = methodCounter.getAndIncrement();
        String methodId = method.elementName() + "_" + methodIndex;
        String constantName = "METHOD_" + methodIndex;
        List<ParamDefinition> methodParams = toMethodParams(service, paramCounter, method, methodId, constantName);

        if (isInjectionPoint) {
            // we want to declare these
            allParams.addAll(methodParams);
        }

        return new MethodDefinition(declaringType,
                                    method.accessModifier(),
                                    methodId,
                                    constantName,
                                    method.elementName(),
                                    overrides,
                                    methodParams,
                                    isInjectionPoint,
                                    method.elementModifiers().contains(Modifier.FINAL));
    }

    private List<ParamDefinition> toMethodParams(DescribedService service,
                                                 AtomicInteger paramCounter,
                                                 TypedElementInfo method,
                                                 String methodId,
                                                 String methodConstantName) {
        return method.parameterArguments()
                .stream()
                .map(param -> {
                    String constantName = "DEP_" + paramCounter.getAndIncrement();
                    var assignment = translateParameter(param.typeName(), constantName);
                    return new ParamDefinition(method,
                                               methodConstantName,
                                               param,
                                               constantName,
                                               param.typeName(),
                                               assignment.usedType(),
                                               assignment.codeGenerator(),
                                               ElementKind.METHOD,
                                               method.elementName(),
                                               param.elementName(),
                                               methodId + "_" + param.elementName(),
                                               method.elementModifiers().contains(Modifier.STATIC),
                                               param.annotations(),
                                               qualifiers(service, param),
                                               contract("Method " + service.serviceDescriptor().typeName()
                                                                .fqName() + "#" + method.elementName() + ", parameter: "
                                                                + param.elementName(),
                                                        assignment.usedType()),
                                               method.accessModifier(),
                                               methodId);
                })
                .toList();
    }

    private void injectConstructorParams(DescribedService service,
                                         List<ParamDefinition> result,
                                         AtomicInteger paramCounter,
                                         TypedElementInfo constructor) {
        constructor.parameterArguments()
                .stream()
                .map(param -> {
                    String constantName = "DEP_" + paramCounter.getAndIncrement();
                    var assignment = translateParameter(param.typeName(), constantName);
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
                                               qualifiers(service, param),
                                               contract(service.serviceDescriptor().typeName()
                                                                .fqName() + " Constructor parameter: " + param.elementName(),
                                                        assignment.usedType()),
                                               constructor.accessModifier(),
                                               "<init>");
                })
                .forEach(result::add);
    }

    private void fieldParam(DescribedService describedService,
                            List<ParamDefinition> result,
                            AtomicInteger paramCounter,
                            TypedElementInfo field) {
        String constantName = "DEP_" + paramCounter.getAndIncrement();
        var assignment = translateParameter(field.typeName(), constantName);

        result.add(new ParamDefinition(field,
                                       null,
                                       field,
                                       constantName,
                                       field.typeName(),
                                       assignment.usedType(),
                                       assignment.codeGenerator(),
                                       ElementKind.FIELD,
                                       field.elementName(),
                                       field.elementName(),
                                       field.elementName(),
                                       field.elementModifiers().contains(Modifier.STATIC),
                                       field.annotations(),
                                       qualifiers(describedService, field),
                                       contract("Field " + describedService.serviceDescriptor().typeName().fqName()
                                                        + "." + field.elementName(),
                                                assignment.usedType()),
                                       field.accessModifier(),
                                       null));
    }

    private InjectAssignment.Assignment translateParameter(TypeName typeName, String constantName) {
        return assignments.assignment(typeName, "ctx__helidonInject.dependency(" + constantName + ")");
    }

    private Set<Annotation> qualifiers(DescribedService service, Annotated element) {
        Set<Annotation> result = new LinkedHashSet<>();

        for (Annotation anno : element.annotations()) {
            if (service.serviceDescriptor()
                    .typeInfo()
                    .hasMetaAnnotation(anno.typeName(), SERVICE_ANNOTATION_QUALIFIER)) {
                result.add(anno);
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec (see note above).
        return result;
    }

    private TypeName contract(String description, TypeName typeName) {
        /*
         get the contract expected for this injection point
         IP may be:
          - Optional
          - List
          - ServiceProvider
          - Supplier
          - Optional<ServiceProvider>
          - Optional<Supplier>
          - List<ServiceProvider>
          - List<Supplier>
          - ServiceInstance
          - List<ServiceInstance>
          - Optional<ServiceInstance>
         */

        if (typeName.isOptional()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with Optional type must have a declared type argument: "
                                                           + description);
            }
            TypeName firstType = typeName.typeArguments().getFirst();
            if (firstType.equals(SERVICE_SERVICE_INSTANCE)) {
                if (typeName.typeArguments().isEmpty()) {
                    throw new IllegalArgumentException("Injection point with Optional<ServiceInstance> type must have a"
                                                               + " declared type argument: " + description);
                }
                return contract(description, firstType.typeArguments().getFirst());
            } else {
                return contract(description, firstType);
            }
        }
        if (typeName.isList()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with List type must have a declared type argument: "
                                                           + description);
            }
            TypeName firstType = typeName.typeArguments().getFirst();
            if (firstType.equals(SERVICE_SERVICE_INSTANCE)) {
                if (typeName.typeArguments().isEmpty()) {
                    throw new IllegalArgumentException("Injection point with List<ServiceInstance> type must have a"
                                                               + " declared type argument: " + description);
                }
                return contract(description, firstType.typeArguments().getFirst());
            } else {
                return contract(description, firstType);
            }
        }
        if (typeName.isSupplier()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with Supplier type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.equals(SERVICE_SERVICE_INSTANCE)) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with ServiceInstance type must have a"
                                                           + " declared type argument: " + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }

        return typeName;
    }

    /**
     * Find if there is a method in the hierarchy that this method overrides.
     *
     * @param services            list of services being processed
     * @param type                first immediate supertype we will be checking
     * @param method              method we are investigating
     * @param arguments           method signature
     * @param currentPackage      package of the current type declaring the method
     * @param expectedAnnotations only look for methods annotated with a specific annotation
     * @return type name of the top level declaring type of this method
     */
    private Optional<TypeName> overrides(Collection<TypeInfo> services,
                                         TypeInfo type,
                                         TypedElementInfo method,
                                         List<TypeName> arguments,
                                         String currentPackage,
                                         TypeName... expectedAnnotations) {

        String methodName = method.elementName();
        // we look only for exact match (including types)
        Optional<TypedElementInfo> found = type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates.elementName(methodName))
                .filter(it -> {
                    for (TypeName expectedAnnotation : expectedAnnotations) {
                        if (!it.hasAnnotation(expectedAnnotation)) {
                            return false;
                        }
                    }
                    return true;
                })
                .filter(ElementInfoPredicates.hasParams(arguments))
                .findFirst();

        // if found, we either return this one, or look further up the hierarchy
        if (found.isPresent()) {
            TypedElementInfo superMethod = found.get();

            // method has same signature, but is package local and is in a different package
            boolean realOverride = superMethod.accessModifier() != AccessModifier.PACKAGE_PRIVATE
                    || currentPackage.equals(type.typeName().packageName());

            if (realOverride) {
                // let's find the declaring type
                ServiceSuperType superType = superType(type, services);
                if (superType.present()) {
                    // do not care about annotations, we already have a match
                    var fromSuperHierarchy = overrides(services, superType.typeInfo(), method, arguments, currentPackage);
                    return Optional.of(fromSuperHierarchy.orElseGet(type::typeName));
                }
                // there is no supertype, this type declares the method
                return Optional.of(type.typeName());
            }
        }

        // we did not find a method on this type, let's look above
        ServiceSuperType superType = superType(type, services);
        if (superType.present()) {
            return overrides(services, superType.typeInfo(), method, arguments, currentPackage, expectedAnnotations);
        }
        return Optional.empty();
    }

    private void singletonInstanceField(ClassModel.Builder classModel, DescribedService service) {
        // singleton instance of the descriptor
        classModel.addField(instance -> instance.description("Global singleton instance for this descriptor.")
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .isFinal(true)
                .type(descriptorInstanceType(service.serviceDescriptor().typeName(), service.descriptorType()))
                .name("INSTANCE")
                .defaultValueContent("new " + service.descriptorType().className() + "<>()"));
    }

    private void injectionPointFields(ClassModel.Builder classModel,
                                      TypeInfo service,
                                      Map<String, GenericTypeDeclaration> genericTypes,
                                      List<ParamDefinition> params) {
        // constant for injection points
        for (ParamDefinition param : params) {
            DependencyMetadata dependencyMetadata = dependencyMetadata(param);
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PUBLIC)
                    .isStatic(true)
                    .isFinal(true)
                    .type(SERVICE_DEPENDENCY)
                    .name(param.constantName())
                    .description(ipIdDescription(service, param))
                    .update(it -> {
                        it.addContent(SERVICE_DEPENDENCY)
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
                                .addContentLine(".descriptor(TYPE)")
                                .addContent(".descriptorConstant(\"")
                                .addContent(param.constantName())
                                .addContentLine("\")")
                                .addContent(".contract(")
                                .addContent(genericTypes.get(param.contract().resolvedName()).constantName())
                                .addContentLine(")")
                                .addContent(".contractType(G")
                                .addContent(genericTypes.get(param.contract().resolvedName()).constantName())
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
                                        .addContentLine(")");
                                qualifier.values()
                                        .keySet()
                                        .forEach(propertyName -> {
                                            it.addContent(".putValue(\"")
                                                    .addContent(propertyName)
                                                    .addContent("\", ");
                                            addAnnotationValue(it, qualifier.objectValue(propertyName).get());
                                            it.addContentLine(")");
                                        });
                                it.addContentLine(")");
                            }
                        }

                        // filter annotations by removing qualifiers
                        var qualifierTypes = param.qualifiers()
                                .stream()
                                .map(Annotation::typeName)
                                .collect(Collectors.toUnmodifiableSet());
                        param.annotations()
                                .stream()
                                .filter(annotation -> !qualifierTypes.contains(annotation.typeName()))
                                .forEach(annotation -> {
                                    it.addContent(".addAnnotation(")
                                            .addContentCreate(annotation)
                                            .addContentLine(")");
                                });

                        if (!dependencyMetadata.cardinality.equals("REQUIRED")) {
                            // only set if not default
                            it.addContent(".cardinality(")
                                    .addContent(DEPENDENCY_CARDINALITY)
                                    .addContent(".")
                                    .addContent(dependencyMetadata.cardinality())
                                    .addContentLine(")");
                        }
                        if (dependencyMetadata.serviceInstance()) {
                            it.addContent(".isServiceInstance(")
                                    .addContent(String.valueOf(dependencyMetadata.serviceInstance()))
                                    .addContentLine(")");
                        }
                        if (dependencyMetadata.supplier()) {
                            it.addContent(".isSupplier(")
                                    .addContent(String.valueOf(dependencyMetadata.supplier()))
                                    .addContentLine(")");
                        }

                        it.addContent(".build()")
                                .decreaseContentPadding()
                                .decreaseContentPadding();
                    }));
        }
    }

    private DependencyMetadata dependencyMetadata(ParamDefinition param) {
        TypeName declared = param.declaredType();

        // supplier is honored only on the first level (i.e. we do not support Optional<Supplier> or List<Supplier>)
        boolean isSupplier = declared.isSupplier();
        boolean isServiceInstance;
        /*
        REQUIRED
        OPTIONAL
        LIST
         */
        String cardinality;

        if (isSupplier) {
            // Supplier<List>, Supplier<ServiceInstance>, Supplier<Contract>, Supplier<Optional<ServiceInstance>> ...
            declared = declared.typeArguments().getFirst();
        }

        if (declared.isSupplier()) {
            throw new CodegenException("Dependency is declared as a Supplier<Supplier<>> - this is not supported",
                                       param.elementInfo().originatingElementValue());
        }

        if (declared.isOptional()) {
            // lists cannot be optional
            cardinality = "OPTIONAL";
            TypeName actualContract = declared.typeArguments().getFirst();
            if (actualContract.isSupplier()) {
                throw new CodegenException("Dependency has Optional<Supplier<>> - this is not supported, please use "
                                                   + "Supplier<Optional>",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isOptional()) {
                throw new CodegenException("Dependency has Optional<Optional<>> - this is not supported",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isList()) {
                throw new CodegenException("Dependency has Optional<List<>> - this is not supported, Lists are empty if "
                                                   + "no service satisfies them, so please use List<> instead",
                                           param.elementInfo().originatingElementValue());
            }
            isServiceInstance = isServiceInstance(actualContract);
        } else if (isServiceInstance(declared)) {
            // service instance is a direct map to an instance, so cannot be list or optional
            cardinality = "REQUIRED";
            isServiceInstance = true;

            TypeName actualContract = declared.typeArguments().getFirst();
            if (actualContract.isSupplier()) {
                throw new CodegenException("Dependency has ServiceInstance<Supplier<>> - this is not supported, please use "
                                                   + "Supplier<ServiceInstance<>>",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isOptional()) {
                throw new CodegenException("Dependency has ServiceInstance<Optional<>> - this is not supported, please use "
                                                   + "Optional<ServiceInstance<?>>",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isList()) {
                throw new CodegenException("Dependency has ServiceInstance<List<>> - this is not supported, please use"
                                                   + "List<ServiceInstance<>>",
                                           param.elementInfo().originatingElementValue());
            }
        } else if (declared.isList()) {
            cardinality = "LIST";
            TypeName actualContract = declared.typeArguments().getFirst();
            if (actualContract.isSupplier()) {
                throw new CodegenException("Dependency has List<Supplier<>> - this is not supported, please use "
                                                   + "Supplier<List<>>",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isOptional()) {
                throw new CodegenException("Dependency has List<Optional<>> - this is not supported",
                                           param.elementInfo().originatingElementValue());
            }
            if (actualContract.isList()) {
                throw new CodegenException("Dependency has List<List<>> - this is not supported",
                                           param.elementInfo().originatingElementValue());
            }
            isServiceInstance = isServiceInstance(actualContract);
        } else {
            cardinality = "REQUIRED";
            isServiceInstance = false;
        }

        return new DependencyMetadata(cardinality, isServiceInstance, isSupplier);
    }

    private boolean isServiceInstance(TypeName typeName) {
        return typeName.equals(SERVICE_SERVICE_INSTANCE);
    }

    private String ipIdDescription(TypeInfo service, ParamDefinition param) {
        TypeName serviceType = service.typeName();
        StringBuilder result = new StringBuilder("Injection point dependency for ");
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
            switch (param.kind()) {
            case CONSTRUCTOR -> result
                    .append("#")
                    .append(serviceType.className())
                    .append("(")
                    .append(toDescriptionSignature(param.owningElement(), true))
                    .append(")");
            case METHOD -> result
                    .append("#")
                    .append(param.owningElement().elementName())
                    .append("(")
                    .append(toDescriptionSignature(param.owningElement(), true))
                    .append(")");
            case FIELD -> result
                    .append("#")
                    .append(param.elementInfo().elementName());
            default -> {
            } // do nothing, this should not be possible
            }
            result.append("}");
        } else {
            // just text
            switch (param.kind()) {
            case CONSTRUCTOR -> result.append("(")
                    .append(toDescriptionSignature(param.owningElement(), false))
                    .append(")");
            case METHOD -> result.append("#")
                    .append(param.owningElement().elementName())
                    .append("(")
                    .append(toDescriptionSignature(param.owningElement(), false))
                    .append(")");
            case FIELD -> result.append(".")
                    .append(param.elementInfo().elementName());
            default -> {
            } // do nothing, this should not be possible
            }
        }

        switch (param.kind()) {
        case CONSTRUCTOR, METHOD -> result
                .append(", parameter ")
                .append(param.elementInfo().elementName());
        default -> {
        } // do nothing, this should not be possible
        }

        result.append(".");
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

    private void methodFields(ClassModel.Builder classModel, List<MethodDefinition> methods) {
        for (MethodDefinition method : methods) {
            classModel.addField(methodField -> methodField
                    .isStatic(true)
                    .isFinal(true)
                    .name(method.constantName())
                    .type(TypeNames.STRING)
                    .update(it -> fieldForMethodConstantBody(method, it)));
        }
    }

    private void fieldForMethodConstantBody(MethodDefinition method, Field.Builder fieldBuilder) {
        // fully.qualified.Type.methodName(fully.qualified.Params<with.qualified.GenericTypes>,another.Param)
        fieldBuilder.addContent("\"")
                .addContent(method.declaringType().fqName())
                .addContent(".")
                .addContent(method.methodName())
                .addContent("(")
                .addContent(method.params().stream()
                                    .map(ParamDefinition::declaredType)
                                    .map(TypeName::resolvedName)
                                    .collect(Collectors.joining(",")))
                .addContent(")\"");
    }

    private void fieldElementField(ClassModel.Builder classModel, TypedElementInfo fieldElement) {
        classModel.addField(ctorElement -> ctorElement
                .isStatic(true)
                .isFinal(true)
                .name(fieldElementConstantName(fieldElement.elementName()))
                .type(TypeNames.TYPED_ELEMENT_INFO)
                .addContentCreate(fieldElement));
    }

    private String fieldElementConstantName(String elementName) {
        return "FIELD_INFO_" + toConstantName(elementName);
    }

    private void constructorElementField(ClassModel.Builder classModel, TypedElementInfo constructorInjectElement) {
        classModel.addField(ctorElement -> ctorElement
                .isStatic(true)
                .isFinal(true)
                .name("CTOR_ELEMENT")
                .type(TypeNames.TYPED_ELEMENT_INFO)
                .addContentCreate(constructorInjectElement));
    }

    private void serviceTypeMethod(ClassModel.Builder classModel, DescribedService service) {
        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("SERVICE_TYPE")
                .addContentCreate(service.serviceDescriptor().typeName().genericTypeName()));

        // TypeName serviceType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("serviceType")
                .addContentLine("return SERVICE_TYPE;"));
    }

    private void providedTypeMethod(ClassModel.Builder classModel, DescribedService service) {
        if (!service.isFactory() && service.superType().empty()) {
            // default will work just fine
            return;
        }
        TypeName providedType = service.isFactory()
                ? service.providedDescriptor().typeName()
                : service.serviceDescriptor().typeName();

        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("PROVIDED_TYPE")
                .addContentCreate(providedType));

        // TypeName providedType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("providedType")
                .addContentLine("return PROVIDED_TYPE;"));
    }

    private void descriptorTypeMethod(ClassModel.Builder classModel, DescribedService service) {
        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("TYPE")
                .addContentCreate(service.descriptorType().genericTypeName()));

        // TypeName descriptorType()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("descriptorType")
                .addContentLine("return TYPE;"));
    }

    private void contractsMethod(ClassModel.Builder classModel,
                                 DescribedService service,
                                 Set<ResolvedType> serviceContracts,
                                 Set<ResolvedType> factoryContracts) {
        var superType = service.superType();

        if (!serviceContracts.isEmpty() || superType.present()) {
            classModel.addField(contractsField -> contractsField
                    .isStatic(true)
                    .isFinal(true)
                    .name("CONTRACTS")
                    .type(SET_OF_RESOLVED_TYPES)
                    .addContent(Set.class)
                    .addContent(".of(")
                    .update(it -> {
                        Iterator<ResolvedType> iterator = serviceContracts.iterator();
                        while (iterator.hasNext()) {
                            it.addContentCreate(iterator.next());
                            if (iterator.hasNext()) {
                                it.addContent(", ");
                            }
                        }
                    })
                    .addContent(")"));

            // Set<Class<?>> contracts()
            classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
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

    private void dependenciesMethod(ClassModel.Builder classModel, DescribedService service, List<ParamDefinition> params) {
        // List<Dependency> dependencies()
        boolean hasSuperType = service.superType().present();
        if (hasSuperType || !params.isEmpty()) {
            classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                    .returnType(LIST_OF_DEPENDENCIES)
                    .name("dependencies")
                    .update(it -> {
                        if (hasSuperType) {
                            it.addContent("return ")
                                    .addContent(SERVICE_G_DEPENDENCY_SUPPORT)
                                    .addContentLine(".combineDependencies(DEPENDENCIES, super.dependencies());");
                        } else {
                            // when super type is a core service, it only can have constructor dependencies - no need to combine
                            it.addContentLine("return DEPENDENCIES;");
                        }
                    }));
        }
    }

    private void instantiateMethod(ClassModel.Builder classModel,
                                   DescribedService service,
                                   List<ParamDefinition> params) {
        DescribedType serviceDescriptor = service.serviceDescriptor();
        if (serviceDescriptor.isAbstract()) {
            return;
        }

        // T instantiate(InjectionContext ctx__helidonInject, InterceptionMetadata interceptMeta__helidonInject)
        var elements = serviceDescriptor.elements();
        TypeName toInstantiate = elements.methodsIntercepted()
                ? interceptedTypeName(serviceDescriptor.typeName())
                : serviceDescriptor.typeName();

        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(serviceDescriptor.typeName())
                .name("instantiate")
                .addParameter(ctxParam -> ctxParam.type(ServiceCodegenTypes.SERVICE_DEPENDENCY_CONTEXT)
                        .name("ctx__helidonInject"))
                .addParameter(interceptMeta -> interceptMeta.type(INTERCEPT_METADATA)
                        .name("interceptMeta__helidonInject"))
                .update(it -> {
                    if (elements.constructorIntercepted()) {
                        createInstantiateInterceptBody(it, params);
                    } else {
                        createInstantiateBody(toInstantiate, it, params, elements.methodsIntercepted());
                    }
                }));

        if (elements.constructorIntercepted()) {
            classModel.addMethod(method -> method.returnType(serviceDescriptor.typeName())
                    .name("doInstantiate")
                    .accessModifier(AccessModifier.PRIVATE)
                    .addParameter(interceptMeta -> interceptMeta.type(INTERCEPT_METADATA)
                            .name("interceptMeta"))
                    .addParameter(ctrParams -> ctrParams.type(TypeName.create("Object..."))
                            .name("params"))
                    .update(it -> createDoInstantiateBody(toInstantiate, it, params, elements.methodsIntercepted())));
        }
    }

    private void createInstantiateInterceptBody(Method.Builder method,
                                                List<ParamDefinition> params) {
        List<ParamDefinition> constructorParams = declareCtrParamsAndGetThem(method, params);

        method.addContentLine("try {")
                .addContentLine("return interceptMeta__helidonInject.createInvoker(this,")
                .increaseContentPadding()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine("QUALIFIERS,")
                .addContentLine("ANNOTATIONS,")
                .addContentLine("CTOR_ELEMENT,")
                .addContentLine("params__helidonInject -> doInstantiate(interceptMeta__helidonInject, params__helidonInject),")
                .addContent(Set.class)
                .addContentLine(".of())") // checked exceptions in constructor not supported, as there is no consumer
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContent(".invoke(")
                .addContent(constructorParams.stream()
                                    .map(ParamDefinition::ipParamName)
                                    .collect(Collectors.joining(", ")))
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("} catch (RuntimeException e__helidonInject) {")
                .addContentLine("throw e__helidonInject;")
                .addContentLine("} catch (Exception e__helidonInject) {")
                .addContent(" throw new ")
                .addContent(INTERCEPT_EXCEPTION)
                .addContentLine("(\"Failed to instantiate \" + SERVICE_TYPE.fqName(), e__helidonInject, false);")
                .addContentLine("}");
    }

    private void createInstantiateBody(TypeName serviceType,
                                       Method.Builder method,
                                       List<ParamDefinition> params,
                                       boolean interceptedMethods) {
        List<ParamDefinition> constructorParams = declareCtrParamsAndGetThem(method, params);
        String paramsDeclaration = constructorParams.stream()
                .map(ParamDefinition::ipParamName)
                .collect(Collectors.joining(", "));

        if (interceptedMethods) {
            // return new MyImpl__Intercepted(interceptMeta, this, ANNOTATIONS, casted params
            method.addContent("return new ")
                    .addContent(serviceType.genericTypeName())
                    .addContentLine("(interceptMeta__helidonInject,")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("this,")
                    .addContentLine("QUALIFIERS,")
                    .addContent("ANNOTATIONS");
            if (!constructorParams.isEmpty()) {
                method.addContentLine(",");
                method.addContent(paramsDeclaration);
            }
            method.addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        } else {
            // return new MyImpl(parameter, parameter2)
            method.addContent("return new ")
                    .addContent(serviceType.genericTypeName())
                    .addContent("(")
                    .addContent(paramsDeclaration)
                    .addContentLine(");");
        }
        boolean hasGenericType = constructorParams.stream()
                .anyMatch(it -> !it.declaredType().typeArguments().isEmpty());

        if (hasGenericType) {
            method.addAnnotation(Annotation.create(TypeName.create(SuppressWarnings.class), "unchecked"));
        }
    }

    private void createDoInstantiateBody(TypeName serviceType,
                                         Method.Builder method,
                                         List<ParamDefinition> params,
                                         boolean interceptedMethods) {
        List<ParamDefinition> constructorParams = params.stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .toList();

        List<String> paramDeclarations = new ArrayList<>();
        for (int i = 0; i < constructorParams.size(); i++) {
            ParamDefinition param = constructorParams.get(i);
            paramDeclarations.add("(" + param.declaredType().resolvedName() + ") params[" + i + "]");
        }
        String paramsDeclaration = String.join(", ", paramDeclarations);

        if (interceptedMethods) {
            method.addContent("return new ")
                    .addContent(serviceType.genericTypeName())
                    .addContentLine("(interceptMeta,")
                    .addContentLine("this,")
                    .addContentLine("QUALIFIERS,")
                    .addContent("ANNOTATIONS");
            if (!constructorParams.isEmpty()) {
                method.addContentLine(",");
                method.addContent(paramsDeclaration);
            }
            method.addContentLine(");");
        } else {
            // return new MyImpl(IP_PARAM_0.type().cast(params[0])
            method.addContent("return new ")
                    .addContent(serviceType.genericTypeName())
                    .addContent("(")
                    .addContent(paramsDeclaration)
                    .addContentLine(");");
        }
    }

    private void isAbstractMethod(ClassModel.Builder classModel, DescribedService service) {
        boolean isAbstract = service.serviceDescriptor().isAbstract();

        if (!isAbstract && service.superType().empty()) {
            return;
        }
        // only override for abstract types (and subtypes, where we do not want to check if super is abstract), default is false
        classModel.addMethod(isAbstractMethod -> isAbstractMethod
                .name("isAbstract")
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .addAnnotation(Annotations.OVERRIDE)
                .addContentLine("return " + isAbstract + ";"));
    }

    private void injectMethod(ClassModel.Builder classModel,
                              DescribedService service,
                              List<ParamDefinition> params,
                              List<MethodDefinition> methods) {

        // method for field and method injections
        List<ParamDefinition> fields = params.stream()
                .filter(it -> it.kind() == ElementKind.FIELD)
                .toList();
        if (fields.isEmpty() && methods.isEmpty()) {
            // only generate this method if we do something
            return;
        }
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .name("inject")
                .addParameter(ctxParam -> ctxParam.type(ServiceCodegenTypes.SERVICE_DEPENDENCY_CONTEXT)
                        .name("ctx__helidonInject"))
                .addParameter(interceptMeta -> interceptMeta.type(INTERCEPT_METADATA)
                        .name("interceptMeta__helidonInject"))
                .addParameter(injectedParam -> injectedParam.type(SET_OF_STRINGS)
                        .name("injected__helidonInject"))
                .addParameter(instanceParam -> instanceParam.type(GENERIC_T_TYPE)
                        .name("instance__helidonInject"))
                .update(it -> createInjectBody(it,
                                               service.superType().present(),
                                               methods,
                                               fields,
                                               service.serviceDescriptor().elements().intercepted(),
                                               service.serviceDescriptor().elements().interceptedElements())));
    }

    private void createInjectBody(Method.Builder methodBuilder,
                                  boolean hasSuperType,
                                  List<MethodDefinition> methods,
                                  List<ParamDefinition> fields,
                                  boolean canIntercept,
                                  List<TypedElements.ElementMeta> maybeIntercepted) {

        boolean hasGenericType = methods.stream()
                .flatMap(it -> it.params().stream())
                .anyMatch(it -> !it.declaredType().typeArguments().isEmpty())
                || fields.stream()
                .anyMatch(it -> !it.declaredType().typeArguments().isEmpty());

        if (hasGenericType) {
            methodBuilder.addAnnotation(Annotation.create(TypeName.create(SuppressWarnings.class), "unchecked"));
        }

        // two passes for methods - first mark method to be injected, then call super, then inject
        for (MethodDefinition method : methods) {
            if (method.isInjectionPoint() && !method.isFinal()) {
                methodBuilder.addContentLine("boolean " + method.invokeName()
                                                     + " = injected__helidonInject.add(" + method.constantName() + ")"
                                                     + ";");
            } else {
                methodBuilder.addContentLine("injected__helidonInject.add(" + method.constantName() + ");");
            }
        }
        methodBuilder.addContentLine("");

        if (hasSuperType) {
            // must be done at the very end, so the same method is not injected first in the supertype
            methodBuilder.addContentLine("super.inject(ctx__helidonInject, interceptMeta__helidonInject, "
                                                 + "injected__helidonInject, instance__helidonInject);");
            methodBuilder.addContentLine("");
        }

        /*
        Inject fields
         */
        for (ParamDefinition field : fields) {
            /*
            instance.myField  = ctx__helidonInject(IP_PARAM_X)
             */
            injectFieldBody(methodBuilder, field, canIntercept, maybeIntercepted);
        }

        if (!fields.isEmpty()) {
            methodBuilder.addContentLine("");
        }

        // now finally invoke the methods
        for (MethodDefinition method : methods) {
            if (!method.isInjectionPoint()) {
                // this method "disabled" injection point from superclass
                continue;
            }
            if (!method.isFinal()) {
                methodBuilder.addContentLine("if (" + method.invokeName() + ") {");
            }
            List<ParamDefinition> params = method.params();

            methodBuilder.addContent("instance__helidonInject." + method.methodName() + "(");
            if (params.size() > 2) {
                methodBuilder.addContentLine("");
                methodBuilder.increaseContentPadding();
                methodBuilder.increaseContentPadding();

                // multiline
                for (int i = 0; i < params.size(); i++) {
                    ParamDefinition param = params.get(i);
                    param.assignmentHandler().accept(methodBuilder);
                    if (i != params.size() - 1) {
                        // not the last one
                        methodBuilder.addContentLine(",");
                    }
                }
                methodBuilder.decreaseContentPadding();
                methodBuilder.decreaseContentPadding();
            } else {
                for (int i = 0; i < params.size(); i++) {
                    ParamDefinition param = params.get(i);
                    param.assignmentHandler().accept(methodBuilder);
                    if (i != params.size() - 1) {
                        // not the last one
                        methodBuilder.addContent(",");
                    }
                }
            }
            // single line
            methodBuilder.addContentLine(");");

            if (!method.isFinal()) {
                methodBuilder.addContentLine("}");
            }
            methodBuilder.addContentLine("");
        }
    }

    private void injectFieldBody(Method.Builder methodBuilder,
                                 ParamDefinition field,
                                 boolean canIntercept,
                                 List<TypedElements.ElementMeta> maybeIntercepted) {
        if (canIntercept && isIntercepted(maybeIntercepted, field.elementInfo())) {
            methodBuilder.addContentLine(field.declaredType().resolvedName() + " "
                                                 + field.ipParamName()
                                                 + " = ctx__helidonInject.dependency(" + field.constantName() + ");");
            String interceptorsName = field.ipParamName() + "__interceptors";
            String constantName = fieldElementConstantName(field.ipParamName());
            methodBuilder.addContent("var ")
                    .addContent(interceptorsName)
                    .addContent(" = interceptMeta__helidonInject.interceptors(QUALIFIERS, ANNOTATIONS, ")
                    .addContent(constantName)
                    .addContentLine(");")
                    .addContent("if(")
                    .addContent(interceptorsName)
                    .addContentLine(".isEmpty() {")
                    .addContent("instance__helidonInject.")
                    .addContent(field.ipParamName())
                    .addContent(" = ")
                    .addContent(field.ipParamName())
                    .addContentLine(";")
                    .addContentLine("} else {")
                    .addContent("instance__helidonInject.")
                    .addContent(field.ipParamName())
                    .addContent(" = interceptMeta__helidonInject.invoke(this,")
                    .addContentLine("ANNOTATIONS,")
                    .addContent(constantName)
                    .addContentLine(",")
                    .addContent(interceptorsName)
                    .addContentLine(",")
                    .addContent("params__helidonInject -> ")
                    .addContent(field.constantName())
                    .addContentLine(".type().cast(params__helidonInject[0]),")
                    .addContent(field.ipParamName())
                    .addContentLine(");")
                    .addContentLine("}");
        } else {
            methodBuilder.addContent("instance__helidonInject." + field.ipParamName() + " = ")
                    .update(it -> field.assignmentHandler().accept(it))
                    .addContentLine(";");
        }
    }

    private boolean isIntercepted(List<TypedElements.ElementMeta> maybeIntercepted, TypedElementInfo typedElementInfo) {
        for (TypedElements.ElementMeta methodMetadata : maybeIntercepted) {
            // yes, we want instance comparison, as we must use the same instances from the same type info
            if (methodMetadata.element() == typedElementInfo) {
                return true;
            }
        }
        return false;
    }

    private void postConstructMethod(ClassModel.Builder classModel, DescribedService service) {
        TypeInfo typeInfo = service.serviceDescriptor().typeInfo();
        TypeName typeName = service.serviceDescriptor().typeName();

        // postConstruct()
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT).ifPresent(method -> {
            classModel.addMethod(postConstruct -> postConstruct.name("postConstruct")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(typeName)
                            .name("instance"))
                    .addContentLine("instance." + method.elementName() + "();"));
        });
    }

    private void preDestroyMethod(ClassModel.Builder classModel, DescribedService service) {
        TypeInfo typeInfo = service.serviceDescriptor().typeInfo();
        TypeName typeName = service.serviceDescriptor().typeName();

        // preDestroy
        lifecycleMethod(typeInfo, ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY).ifPresent(method -> {
            classModel.addMethod(preDestroy -> preDestroy.name("preDestroy")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(typeName)
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
            throw new IllegalStateException("Method annotated with " + annotationType.fqName()
                                                    + ", is private, which is not supported: " + typeInfo.typeName().fqName()
                                                    + "#" + method.elementName());
        }
        if (!method.parameterArguments().isEmpty()) {
            throw new IllegalStateException("Method annotated with " + annotationType.fqName()
                                                    + ", has parameters, which is not supported: " + typeInfo.typeName().fqName()
                                                    + "#" + method.elementName());
        }
        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw new IllegalStateException("Method annotated with " + annotationType.fqName()
                                                    + ", is not void, which is not supported: " + typeInfo.typeName().fqName()
                                                    + "#" + method.elementName());
        }
        return Optional.of(method);
    }

    private void qualifiersMethod(ClassModel.Builder classModel, DescribedService service) {
        Set<Annotation> qualifiers = service.qualifiers();
        // qualifier field is always needed, as it is used for interception
        Qualifiers.generateQualifiersConstant(classModel, qualifiers);

        if (qualifiers.isEmpty() && service.superType().empty()) {
            return;
        }

        // List<Qualifier> qualifiers()
        classModel.addMethod(qualifiersMethod -> qualifiersMethod.name("qualifiers")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(SET_OF_QUALIFIERS)
                .addContentLine("return QUALIFIERS;"));
    }

    private void scopeMethod(ClassModel.Builder classModel, DescribedService service) {
        // TypeName scope()
        TypeName scope = service.scope();

        if (scope.packageName().equals(SERVICE_ANNOTATION_SINGLETON.packageName())
                && scope.enclosingNames().size() == 1 && scope.enclosingNames().getFirst().equals("Service")) {
            // only method
            classModel.addMethod(scopeMethod -> scopeMethod.name("scope")
                    .addAnnotation(Annotations.OVERRIDE)
                    .returnType(TypeNames.TYPE_NAME)
                    .addContent("return ")
                    .addContent(scope)
                    .addContentLine(".TYPE;"));
        } else {
            // field and method
            classModel.addField(scopesField -> scopesField
                    .isStatic(true)
                    .isFinal(true)
                    .name("SCOPE")
                    .type(TypeNames.TYPE_NAME)
                    .addContentCreate(scope));

            classModel.addMethod(scopeMethod -> scopeMethod.name("scope")
                    .addAnnotation(Annotations.OVERRIDE)
                    .returnType(TypeNames.TYPE_NAME)
                    .addContentLine("return SCOPE;"));
        }
    }

    private void weightMethod(ClassModel.Builder classModel, DescribedService service) {
        TypeInfo typeInfo = service.serviceDescriptor().typeInfo();

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

    private void runLevelMethod(ClassModel.Builder classModel, DescribedService service) {
        TypeInfo typeInfo = service.serviceDescriptor().typeInfo();

        boolean hasSuperType = service.superType().present();
        // double runLevel()
        Optional<Double> runLevel = runLevel(typeInfo);

        if (!hasSuperType && runLevel.isEmpty()) {
            return;
        }

        if (runLevel.isEmpty()) {
            classModel.addMethod(runLevelMethod -> runLevelMethod.name("runLevel")
                    .addAnnotation(Annotations.OVERRIDE)
                    .returnType(TypeName.builder(TypeNames.OPTIONAL)
                                        .addTypeArgument(TypeNames.BOXED_DOUBLE)
                                        .build())
                    .addContent("return ")
                    .addContent(Optional.class)
                    .addContentLine(".empty();"));
            return;
        }

        double usedRunLevel = runLevel.get();

        classModel.addMethod(runLevelMethod -> runLevelMethod.name("runLevel")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeName.builder(TypeNames.OPTIONAL)
                                    .addTypeArgument(TypeNames.BOXED_DOUBLE)
                                    .build())
                .addContent("return ")
                .addContent(Optional.class)
                .addContent(".of(")
                .addContent(String.valueOf(usedRunLevel))
                .addContentLine("D);"));
    }

    private Optional<Double> runLevel(TypeInfo typeInfo) {
        return typeInfo.findAnnotation(SERVICE_ANNOTATION_RUN_LEVEL)
                .flatMap(Annotation::doubleValue);
    }

    private TypeName descriptorInstanceType(TypeName serviceType, TypeName descriptorType) {
        return TypeName.builder(descriptorType)
                .addTypeArgument(serviceType)
                .build();
    }

    private TypeName generateProvidedInterceptionDelegate(RegistryRoundContext roundContext, DescribedService service) {
        DescribedType providedDescriptor = service.providedDescriptor();
        TypeInfo typeInfo = providedDescriptor
                .typeInfo();

        TypeName expectedInterceptionDelegate = interception.interceptedDelegateType(typeInfo.typeName());

        // now we need to check this is generated
        if (ctx.typeInfo(expectedInterceptionDelegate).isPresent()) {
            // it was already generated for another provider maybe
            return expectedInterceptionDelegate;
        } else {
            if (canDelegate(service.serviceDescriptor().typeInfo(),
                            typeInfo)) {
                // we must generate it right now
                interception.generateDelegateInterception(roundContext,
                                                          typeInfo,
                                                          providedDescriptor.elements(),
                                                          expectedInterceptionDelegate);

                return expectedInterceptionDelegate;
            }

            throw new CodegenException("Attempting to create delegate interception for non interface type. "
                                               + "If the type is ready to be delegated, annotate it with "
                                               + INTERCEPTION_DELEGATE.fqName() + ", or annotate the service provider with "
                                               + INTERCEPTION_EXTERNAL_DELEGATE.fqName()
                                               + "(" + typeInfo.typeName().classNameWithEnclosingNames()
                                               + ".class) if it is not under your control",
                                       service.serviceDescriptor().typeInfo().originatingElementValue());
        }
    }

    private boolean canDelegate(TypeInfo providerType, TypeInfo providedType) {
        // interfaces are always supported
        if (providedType.kind() == ElementKind.INTERFACE) {
            return true;
        }
        // it is itself marked as a delegate
        if (providedType.hasAnnotation(INTERCEPTION_DELEGATE)) {
            return true;
        }
        // only if marked as external delegate
        return providerType.hasAnnotation(INTERCEPTION_EXTERNAL_DELEGATE)
                && providerType.annotation(INTERCEPTION_EXTERNAL_DELEGATE)
                .typeValue().map(it -> providedType.typeName().equals(it))
                .orElse(false);
    }

    private void generateDelegationService(RegistryRoundContext roundContext,
                                           DescribedService service,
                                           TypeName delegateType) {
        TypeName typeName = service.serviceDescriptor().typeName();

        String typeNameSuffix = "__Interception_Wrapper";
        TypeName wrapperType = TypeName.builder()
                .packageName(typeName.packageName())
                .className(typeName.classNameWithEnclosingNames().replace('.', '_') + typeNameSuffix)
                .build();
        var classModel = ClassModel.builder()
                .type(wrapperType)
                .addAnnotation(Annotation.create(service.scope()))
                .addInterface(service.providerInterface())
                .superType(service.interceptionWrapperSuperType())
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 typeName,
                                                 wrapperType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               typeName,
                                                               wrapperType,
                                                               "1",
                                                               ""));
        service.qualifiers()
                .forEach(classModel::addAnnotation);

        classModel.addField(interceptMeta -> interceptMeta
                .type(INTERCEPT_METADATA)
                .name("interceptMeta")
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE));

        classModel.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_INJECT))
                .addParameter(delegate -> delegate
                        .update(it -> service.qualifiers().forEach(it::addAnnotation))
                        .type(typeName)
                        .name("delegate"))
                .addParameter(interceptMeta -> interceptMeta
                        .type(INTERCEPT_METADATA)
                        .name("interceptMeta"))
                .addContentLine("super(delegate);")
                .addContentLine("this.interceptMeta = interceptMeta;")
        );

        TypeName descriptorType = service.descriptorType();
        classModel.addMethod(wrap -> wrap
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PROTECTED)
                .name("wrap")
                .returnType(service.providedDescriptor().typeName())
                .addParameter(instance -> instance
                        .type(service.providedDescriptor().typeName())
                        .name("instance"))
                .addContent("return ")
                .addContent(delegateType)
                .addContentLine(".create(")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine("interceptMeta,")
                .addContent(descriptorType)
                .addContentLine(".INSTANCE,")
                .addContentLine("instance);")
        );

        roundContext.addGeneratedType(wrapperType,
                                      classModel,
                                      typeName,
                                      service.serviceDescriptor().typeInfo().originatingElementValue());
    }

    private void factoryType(ClassModel.Builder classModel, DescribedService service, FactoryType factoryType) {
        if (service.superType().empty() && factoryType == FactoryType.SERVICE) {
            // default
            return;
        }
        classModel.addMethod(providerTypeMethod -> providerTypeMethod
                .name("factoryType")
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(SERVICE_FACTORY_TYPE)
                .addContent("return ")
                .addContent(SERVICE_FACTORY_TYPE)
                .addContent(".")
                .addContent(factoryType.name())
                .addContentLine(";")
        );
    }

    private void methodElementFields(ClassModel.Builder classModel,
                                     DescribedService service) {
        Set<ElementSignature> elementSignatures = service.serviceDescriptor()
                .elements()
                .interceptedMethods();
        List<TypedElements.ElementMeta> interceptedElements = service.serviceDescriptor()
                .elements()
                .interceptedElements()
                .stream()
                .filter(it -> elementSignatures.contains(it.element().signature()))
                .collect(Collectors.toUnmodifiableList());
        TypeInfo typeInfo = service.serviceDescriptor().typeInfo();

        for (TypedElements.ElementMeta element : interceptedElements) {
            addMethodElementField(typeInfo, classModel, element);
        }

        // now go through all methods that have meta-annotation of an entry point and add them
        service.serviceDescriptor()
                .elements()
                .plainElements()
                .stream()
                .filter(this::isEntryPoint)
                .forEach(element -> {
                    addMethodElementField(typeInfo, classModel, element);
                });
    }

    private boolean isEntryPoint(TypedElements.ElementMeta element) {
        TypedElementInfo method = element.element();
        if (!ElementInfoPredicates.isMethod(method)) {
            return false;
        }
        if (ElementInfoPredicates.isAbstract(method)) {
            return false;
        }
        if (ElementInfoPredicates.isStatic(method)) {
            return false;
        }
        List<Annotation> elementAnnotations = new ArrayList<>(method.annotations());
        addInterfaceAnnotations(elementAnnotations, element.abstractMethods());

        for (Annotation elementAnnotation : elementAnnotations) {
            if (elementAnnotation.hasMetaAnnotation(SERVICE_ANNOTATION_ENTRY_POINT)) {
                return true;
            }
        }
        return false;
    }

    private void addMethodElementField(TypeInfo typeInfo,
                                       ClassModel.Builder classModel,
                                       TypedElements.ElementMeta element) {

        var method = element.element();
        String uniqueName = ctx.uniqueName(typeInfo, method);
        String constantName = "METHOD_" + toConstantName(uniqueName);

        // add inherited annotations from interfaces
        List<Annotation> elementAnnotations = new ArrayList<>(method.annotations());
        addInterfaceAnnotations(elementAnnotations, element.abstractMethods());
        TypedElementInfo typedElementInfo = TypedElementInfo.builder()
                .from(method)
                .annotations(elementAnnotations)
                .build();
        classModel.addField(constant -> constant
                .description("Element info for method: {@code " + method.signature() + "}.")
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .isFinal(true)
                .type(TypeNames.TYPED_ELEMENT_INFO)
                .name(constantName)
                .addContentCreate(typedElementInfo));
    }

    private void generateInterceptedType(RegistryRoundContext roundContext,
                                         TypeInfo typeInfo,
                                         DescribedService service,
                                         TypedElementInfo constructorInjectElement) {
        TypeName typeName = service.serviceDescriptor().typeName();

        TypeName interceptedType = interceptedTypeName(typeName);

        var generator = new InterceptedTypeGenerator(ctx,
                                                     typeInfo,
                                                     typeName,
                                                     service.descriptorType(),
                                                     interceptedType,
                                                     constructorInjectElement,
                                                     service.serviceDescriptor()
                                                             .elements()
                                                             .interceptedElements()
                                                             .stream()
                                                             .filter(it -> it.element().kind() == ElementKind.METHOD)
                                                             .toList());

        roundContext.addGeneratedType(interceptedType,
                                      generator.generate(),
                                      typeName,
                                      typeInfo.originatingElementValue());
    }

    private TypeName interceptedTypeName(TypeName serviceType) {
        return TypeName.builder(serviceType)
                .className(serviceType.classNameWithEnclosingNames().replace('.', '_') + "__Intercepted")
                .enclosingNames(List.of())
                .build();
    }

    private void qualifiedProvider(ClassModel.Builder classModel, DescribedService service) {
        var typeName = service.qualifiedProviderQualifier();
        if (typeName == null) {
            // just use default from interface, we only need to declare this on a type that explicitly implements
            // QualifiedProvider
            return;
        }
        classModel.addInterface(SERVICE_G_QUALIFIED_FACTORY_DESCRIPTOR);

        classModel.addField(qpField -> qpField
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .name("QP_QUALIFIER")
                .type(TypeNames.TYPE_NAME)
                .addContentCreate(typeName)
        );
        classModel.addMethod(qpMethod ->
                                     qpMethod
                                             .accessModifier(AccessModifier.PUBLIC)
                                             .returnType(TypeNames.TYPE_NAME)
                                             .name("qualifierType")
                                             .addAnnotation(Annotations.OVERRIDE)
                                             .addContentLine("return QP_QUALIFIER;")
        );
    }

    private void scopeHandler(TypeInfo typeInfo, ClassModel.Builder classModel, Set<ResolvedType> contracts) {
        if (contracts.stream()
                .noneMatch(it -> it.type().equals(SERVICE_SCOPE_HANDLER))) {
            return;
        }

        TypeName handledScope = findHandledScope(typeInfo);

        classModel.addInterface(SERVICE_G_SCOPE_HANDLER_DESCRIPTOR);
        classModel.addField(scopeField -> scopeField
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .name("SCOPE_HANDLER_SCOPE")
                .type(TypeNames.TYPE_NAME)
                .addContentCreate(handledScope));

        classModel.addMethod(handledScopeMethod -> handledScopeMethod
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.TYPE_NAME)
                .name("handledScope")
                .addContentLine("return SCOPE_HANDLER_SCOPE;"));
    }

    private TypeName findHandledScope(TypeInfo typeInfo) {
        return typeInfo.findAnnotation(SERVICE_ANNOTATION_NAMED)
                .flatMap(Annotation::value)
                .map(TypeName::create)
                .orElseThrow(() -> new CodegenException(
                        "Type implementing ScopeHandler must be qualified with the scope type name: " + typeInfo.typeName()));
    }

    private void createForMethod(ClassModel.Builder classModel,
                                 DescribedService service) {

        TypeInfo serviceTypeInfo = service.serviceDescriptor().typeInfo();
        TypeName serviceTypeName = service.serviceDescriptor().typeName();

        Optional<Annotation> createFor = serviceTypeInfo.findAnnotation(SERVICE_ANNOTATION_PER_INSTANCE);
        if (service.superType().empty() && createFor.isEmpty()) {
            // this is the default
            return;
        }
        if (createFor.isPresent()) {
            if (service.providerType() != FactoryType.SERVICE) {
                throw new CodegenException("Service " + serviceTypeName.classNameWithEnclosingNames()
                                                   + " is annotated with @"
                                                   + SERVICE_ANNOTATION_PER_INSTANCE.classNameWithEnclosingNames()
                                                   + ", and as such it must not implement any "
                                                   + "provider interfaces. Provider type: " + service.providerType(),
                                           serviceTypeInfo.originatingElementValue());
            }

            TypeName createForType = createFor.get()
                    .typeValue()
                    .orElseThrow(() -> new CodegenException(SERVICE_ANNOTATION_PER_INSTANCE.fqName()
                                                                    + ".value() is required, yet not found on type: "
                                                                    + serviceTypeName.fqName()));

            String createForClassName = createForType.className();
            if (createForClassName.endsWith("Blueprint")) {
                // this may be a config blueprint, use the config instance
                Optional<TypeInfo> createForTypeInfo = ctx.typeInfo(createForType);
                if (createForTypeInfo.isPresent()) {
                    if (createForTypeInfo.get().hasAnnotation(BUILDER_BLUEPRINT)) {
                        createForType = TypeName.builder(createForType)
                                .className(createForClassName.substring(0, createForClassName.length() - "Blueprint".length()))
                                .build();
                    }
                }
            }

            if (createForType.packageName().isBlank()) {
                throw new CodegenException(SERVICE_ANNOTATION_PER_INSTANCE.classNameWithEnclosingNames()
                                                   + " type used on " + serviceTypeName.fqName() + " does not have a "
                                                   + "package defined. Package is mandatory. If the type is a generated"
                                                   + " prototype, please use the Blueprint type instead.");
            }

            // used from lambda
            TypeName createForTypeFinal = createForType;
            classModel.addInterface(SERVICE_G_PER_INSTANCE_DESCRIPTOR);

            classModel.addField(createForField -> createForField
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .name("CREATE_FOR")
                    .type(TypeNames.TYPE_NAME)
                    .addContentCreate(createForTypeFinal));

            classModel.addMethod(createForMethod -> createForMethod
                    .accessModifier(AccessModifier.PUBLIC)
                    .addAnnotation(Annotations.OVERRIDE)
                    .returnType(TypeNames.TYPE_NAME)
                    .name("createFor")
                    .addContentLine("return CREATE_FOR;"));
        }
    }

    private Map<String, GenericTypeDeclaration> genericTypes(ClassModel.Builder classModel,
                                                             List<ParamDefinition> params,
                                                             List<MethodDefinition> methods) {
        // we must use map by string (as type name is equal if the same class, not full generic declaration)
        Map<String, GenericTypeDeclaration> result = new LinkedHashMap<>();
        AtomicInteger counter = new AtomicInteger();

        for (ParamDefinition param : params) {
            result.computeIfAbsent(param.translatedType().resolvedName(),
                                   type -> {
                                       var response = new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                                 param.declaredType());
                                       addTypeConstant(classModel, param.translatedType(), response);
                                       return response;
                                   });
            result.computeIfAbsent(param.contract().resolvedName(),
                                   type -> {
                                       var response = new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                                 param.declaredType());
                                       addTypeConstant(classModel, param.contract(), response);
                                       return response;
                                   });
        }

        for (MethodDefinition method : methods) {
            for (ParamDefinition param : method.params()) {
                result.computeIfAbsent(param.declaredType().resolvedName(),
                                       type -> {
                                           var response =
                                                   new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                              param.declaredType());
                                           addTypeConstant(classModel,
                                                           param.declaredType(),
                                                           response
                                           );
                                           return response;
                                       });
            }
        }

        return result;
    }

    private void addTypeConstant(ClassModel.Builder classModel,
                                 TypeName typeName,
                                 GenericTypeDeclaration generic) {
        String stringType = typeName.resolvedName();
        // constants for injection point parameter types (used by next section)
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

    private record DependencyMetadata(String cardinality,
                                      boolean serviceInstance,
                                      boolean supplier) {
    }

    private record GenericTypeDeclaration(String constantName,
                                          TypeName typeName) {
    }
}
