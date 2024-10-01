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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.spi.InjectCodegenObserver;
import io.helidon.service.codegen.spi.InjectCodegenObserverProvider;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.service.codegen.ServiceCodegenTypes.BUILDER_BLUEPRINT;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_CREATE_FOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_DESCRIBE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_POINT_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_REQUEST_SCOPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_IP_SUPPORT;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_QUALIFIED_PROVIDER_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_SCOPE_HANDLER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_SCOPE_HANDLER_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_SERVICE_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_DELEGATE;
import static io.helidon.service.codegen.ServiceCodegenTypes.QUALIFIED_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICES_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER;
import static java.util.function.Predicate.not;

class InjectionExtension implements RegistryCodegenExtension {
    static final TypeName LIST_OF_ANNOTATIONS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.ANNOTATION)
            .build();
    static final TypeName SET_OF_QUALIFIERS = TypeName.builder(TypeNames.SET)
            .addTypeArgument(ServiceCodegenTypes.INJECT_QUALIFIER)
            .build();
    static final TypeName SET_OF_TYPES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.TYPE_NAME)
            .build();
    static final TypeName SET_OF_SIGNATURES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.STRING)
            .build();
    private static final TypeName LIST_OF_IP_IDS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(ServiceCodegenTypes.INJECTION_POINT)
            .build();
    private static final TypeName SERVICE_SOURCE_TYPE = TypeName.builder(ServiceCodegenTypes.INJECT_SERVICE_DESCRIPTOR)
            .addTypeArgument(TypeName.create("T"))
            .build();
    private static final TypeName GENERATOR = TypeName.create(InjectionExtension.class);
    private static final TypeName GENERIC_T_TYPE = TypeName.createFromGenericDeclaration("T");
    private static final TypeName ANY_GENERIC_TYPE = TypeName.builder(TypeNames.GENERIC_TYPE)
            .addTypeArgument(TypeName.create("?"))
            .build();
    private static final Annotation WILDCARD_NAMED = Annotation.create(INJECTION_NAMED, "*");

    private final RegistryCodegenContext ctx;
    private final boolean autoAddContracts;
    private final Interception interception;
    private final InterceptionSupport interceptionSupport;
    private final Set<TypeName> scopeMetaAnnotations;
    private final List<InjectCodegenObserver> observers;

    InjectionExtension(RegistryCodegenContext codegenContext) {
        this.ctx = codegenContext;
        CodegenOptions options = codegenContext.options();
        this.autoAddContracts = ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES.value(options);
        this.interception = new Interception(InjectOptions.INTERCEPTION_STRATEGY.value(options));
        this.interceptionSupport = InterceptionSupport.create(ctx);
        this.scopeMetaAnnotations = InjectOptions.SCOPE_META_ANNOTATIONS.value(options);
        this.observers = HelidonServiceLoader.create(ServiceLoader.load(InjectCodegenObserverProvider.class,
                                                                        InjectionExtension.class.getClassLoader()))
                .stream()
                .map(it -> it.create(codegenContext))
                .toList();
    }

    static void annotationsField(ClassModel.Builder classModel, TypeInfo typeInfo) {
        classModel.addField(annotations -> annotations
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .name("ANNOTATIONS")
                .type(LIST_OF_ANNOTATIONS)
                .addContent(List.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = typeInfo.annotations().iterator();
                    while (iterator.hasNext()) {
                        it.addContentCreate(iterator.next());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> mainClass = roundContext.annotatedTypes(ServiceCodegenTypes.INJECTION_MAIN);
        if (!mainClass.isEmpty()) {
            generateMain(mainClass.iterator().next());
        }

        List<TypeInfo> descriptorsRequired = new ArrayList<>(roundContext.types());
        mainClass.forEach(descriptorsRequired::remove);

        for (TypeInfo typeInfo : descriptorsRequired) {
            if (typeInfo.hasAnnotation(INJECTION_DESCRIBE)) {
                generateScopeDescriptor(typeInfo, typeInfo.annotation(INJECTION_DESCRIBE));
            } else if (typeInfo.hasAnnotation(INTERCEPTION_DELEGATE)) {
                generateInterceptionDelegate(typeInfo);
            } else {
                generateDescriptor(descriptorsRequired, typeInfo);
            }
        }

        notifyObservers(roundContext, descriptorsRequired);
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

    private void generateMain(TypeInfo customMain) {
        // main class is ONLY generated if required by the user (through Injection.Main annotation)
        // generate the `ApplicationMain` that just starts the registry (with auto-discovery)
        // this class may be overridden by the maven plugin, but it allows for customization of main
        // class by the user (as otherwise the class does not exist during compilation)
        // creates an empty method body, to look everything up at runtime
        String className = InjectOptions.INJECTION_MAIN_CLASS.value(ctx.options());
        TypeName generatedType = TypeName.builder()
                .packageName(customMain.typeName().packageName())
                .className(className)
                .build();

        ClassModel.Builder applicationMain = ApplicationMainCodegen.generate(GENERATOR,
                                                                             generatedType,
                                                                             true,
                                                                             false,
                                                                             (a, b, c) -> {
                                                                             },
                                                                             (a, b, c) -> {
                                                                             });
        ctx.addType(generatedType,
                    applicationMain,
                    GENERATOR);
    }

    private void generateInterceptionDelegate(TypeInfo typeInfo) {
        if (typeInfo.kind() != ElementKind.INTERFACE) {
            throw new CodegenException("Attempting to create delegate interception for non interface type",
                                       typeInfo.originatingElementValue());
        }
        interceptionSupport.generateDelegateInterception(typeInfo, typeInfo.typeName());
    }

    private void generateScopeDescriptor(TypeInfo typeInfo, Annotation describeAnnotation) {
        TypeName serviceType = typeInfo.typeName();
        TypeName scope = describeAnnotation.typeValue().orElse(INJECTION_REQUEST_SCOPE);
        // this must result in generating a service descriptor file
        TypeName descriptorType = ctx.descriptorType(serviceType);

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 serviceType,
                                                 descriptorType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               serviceType,
                                                               descriptorType,
                                                               "1",
                                                               ""))
                .addInterface(SERVICE_SOURCE_TYPE)
                .type(descriptorType)
                .addGenericArgument(TypeArgument.create("T extends " + serviceType.fqName()))
                .javadoc(Javadoc.builder()
                                 .add("Service descriptor for {@link " + serviceType.fqName() + "}.")
                                 .addGenericArgument("T", "type of the service, for extensibility")
                                 .build())
                // we need to keep insertion order, as constants may depend on each other
                .sortStaticFields(false);
        SuperType superType = new SuperType();

        singletonInstanceField(classModel, serviceType, descriptorType);
        serviceTypeFields(classModel, serviceType, descriptorType);

        Set<TypeName> contracts = new HashSet<>();
        Set<String> collectedFullyQualifiedContracts = new HashSet<>();
        AtomicReference<TypeName> qualifiedProviderQualifier = new AtomicReference<>();
        contracts(typeInfo, autoAddContracts, contracts, collectedFullyQualifiedContracts, qualifiedProviderQualifier);

        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers(typeInfo, qualifiers);

        // annotations of the type
        annotationsField(classModel, typeInfo);
        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));
        // methods (some methods define fields as well)
        serviceTypeMethod(classModel);
        descriptorTypeMethod(classModel);
        contractsMethod(classModel, contracts);
        qualifiersMethod(classModel, qualifiers, superType);
        scopeMethod(classModel, scope);
        weightMethod(typeInfo, classModel, superType);
        runLevelMethod(typeInfo, classModel, superType);

        // service type is an implicit contract
        Set<TypeName> allContracts = new HashSet<>(contracts);
        allContracts.add(serviceType);

        ctx.addDescriptor("inject",
                          serviceType,
                          descriptorType,
                          classModel,
                          weight(typeInfo).orElse(Weighted.DEFAULT_WEIGHT),
                          allContracts,
                          typeInfo.originatingElementValue());
    }

    private void generateDescriptor(Collection<TypeInfo> services,
                                    TypeInfo typeInfo) {
        if (typeInfo.kind() == ElementKind.INTERFACE || typeInfo.kind() == ElementKind.ANNOTATION_TYPE) {
            // we cannot support multiple inheritance, so descriptors for interfaces do not make sense
            return;
        }
        boolean isAbstractClass = typeInfo.elementModifiers().contains(Modifier.ABSTRACT)
                && typeInfo.kind() == ElementKind.CLASS;

        SuperType superType = superType(typeInfo, services);

        // this set now contains all fields, constructors, and methods that may be intercepted, as they contain
        // an annotation that is an interception trigger (based on interceptionStrategy)
        List<TypedElements.ElementMeta> maybeIntercepted = interception.maybeIntercepted(typeInfo);
        boolean canIntercept = !maybeIntercepted.isEmpty();
        boolean methodsIntercepted = maybeIntercepted.stream()
                .map(TypedElements.ElementMeta::element)
                .anyMatch(ElementInfoPredicates::isMethod);
        boolean constructorIntercepted = maybeIntercepted.stream()
                .map(TypedElements.ElementMeta::element)
                .anyMatch(it -> it.kind() == ElementKind.CONSTRUCTOR);

        TypeName serviceType = typeInfo.typeName();
        // this must result in generating a service descriptor file
        TypeName descriptorType = ctx.descriptorType(serviceType);

        List<ParamDefinition> params = new ArrayList<>();
        List<MethodDefinition> methods = new ArrayList<>();

        TypedElementInfo constructorInjectElement = injectConstructor(typeInfo);
        List<TypedElementInfo> fieldInjectElements = fieldInjectElements(typeInfo);

        params(services,
               typeInfo,
               superType,
               methods,
               params,
               constructorInjectElement,
               fieldInjectElements);

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 serviceType,
                                                 descriptorType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
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

        singletonInstanceField(classModel, serviceType, descriptorType);

        Map<String, GenericTypeDeclaration> genericTypes = genericTypes(classModel, params, methods);
        Optional<TypeName> scope = scope(typeInfo);
        Set<TypeName> contracts = new HashSet<>();
        Set<String> collectedFullyQualifiedContracts = new HashSet<>();
        AtomicReference<TypeName> qualifiedProviderQualifier = new AtomicReference<>();
        contracts(typeInfo, autoAddContracts, contracts, collectedFullyQualifiedContracts, qualifiedProviderQualifier);

        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers(typeInfo, qualifiers);

        // declare the class

        if (superType.hasSupertype()) {
            classModel.superType(superType.superDescriptorType());
            if (superType.superTypeIsCore()) {
                classModel.addInterface(SERVICE_SOURCE_TYPE);
            }
        } else {
            classModel.addInterface(SERVICE_SOURCE_TYPE);
        }

        // Additional fields
        serviceTypeFields(classModel, serviceType, descriptorType);
        methodFields(classModel, methods);
        methodElementFields(classModel, typeInfo);

        // public fields are last, so they do not intersect with private fields (it is not as nice to read)
        // they cannot be first, as they require some of the private fields
        injectionPointFields(classModel, typeInfo, genericTypes, params);
        // dependencies require IP IDs, so they really must be last
        dependenciesField(classModel, params);
        // annotations of the type
        annotationsField(classModel, typeInfo);

        if (canIntercept) {
            // if constructor intercepted, add its element
            if (constructorIntercepted) {
                constructorElementField(classModel, constructorInjectElement);
            }
            // if injected field intercepted, add its element (other fields cannot be intercepted)
            fieldInjectElements.stream()
                    .filter(it -> isIntercepted(maybeIntercepted, it))
                    .forEach(fieldElement -> fieldElementField(classModel, fieldElement));
            // all other interception is done on method level and is handled by the
            // service descriptor delegating to a generated type
        }

        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));

        // methods (some methods define fields as well)
        serviceTypeMethod(classModel);
        descriptorTypeMethod(classModel);
        contractsMethod(classModel, contracts);
        dependenciesMethod(classModel, params, superType);
        isAbstractMethod(classModel, superType, isAbstractClass);
        instantiateMethod(classModel, serviceType, params, isAbstractClass, constructorIntercepted, methodsIntercepted);
        injectMethod(classModel, params, superType, methods, canIntercept, maybeIntercepted);
        postConstructMethod(typeInfo, classModel, serviceType);
        preDestroyMethod(typeInfo, classModel, serviceType);
        qualifiersMethod(classModel, qualifiers, superType);
        scopeMethod(classModel, scope.orElse(INJECTION_INSTANCE));
        weightMethod(typeInfo, classModel, superType);
        runLevelMethod(typeInfo, classModel, superType);
        createForMethod(typeInfo, classModel, superType, contracts);
        qualifiedProvider(classModel, qualifiedProviderQualifier.get());
        scopeHandler(typeInfo, classModel, contracts);

        // service type is an implicit contract
        Set<TypeName> allContracts = new HashSet<>(contracts);
        allContracts.add(serviceType);

        ctx.addDescriptor("inject",
                          serviceType,
                          descriptorType,
                          classModel,
                          weight(typeInfo).orElse(Weighted.DEFAULT_WEIGHT),
                          allContracts,
                          typeInfo.originatingElementValue());

        if (methodsIntercepted) {
            generateInterceptedType(typeInfo, serviceType, descriptorType, constructorInjectElement, maybeIntercepted);
        }
    }

    private void methodElementFields(ClassModel.Builder classModel,
                                     TypeInfo typeInfo) {
        TypedElements.gatherElements(typeInfo)
                .stream()
                .filter(element -> ElementInfoPredicates.isMethod(element.element()))
                .filter(element -> !ElementInfoPredicates.isPrivate(element.element()))
                .forEach(element -> {
                    var method = element.element();
                    String uniqueName = ctx.uniqueName(typeInfo, method);
                    String constantName = "METHOD_" + toConstantName(uniqueName);

                    // add inherited annotations from interfaces
                    List<Annotation> elementAnnotations = new ArrayList<>(method.annotations());
                    addInterfaceAnnotations(elementAnnotations, element.interfaceMethods());
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
                });
    }

    private void generateInterceptedType(TypeInfo typeInfo,
                                         TypeName serviceType,
                                         TypeName descriptorType,
                                         TypedElementInfo constructorInjectElement,
                                         List<TypedElements.ElementMeta> maybeIntercepted) {
        TypeName interceptedType = interceptedTypeName(serviceType);

        var generator = new InterceptedTypeGenerator(ctx,
                                                     typeInfo,
                                                     serviceType,
                                                     descriptorType,
                                                     interceptedType,
                                                     constructorInjectElement,
                                                     maybeIntercepted.stream()
                                                             .filter(it -> it.element().kind() == ElementKind.METHOD)
                                                             .toList());

        ctx.addType(interceptedType,
                    generator.generate(),
                    serviceType,
                    typeInfo.originatingElementValue());
    }

    private TypeName interceptedTypeName(TypeName serviceType) {
        return TypeName.builder(serviceType)
                .className(serviceType.classNameWithEnclosingNames().replace('.', '_') + "__Intercepted")
                .enclosingNames(List.of())
                .build();
    }

    private void qualifiedProvider(ClassModel.Builder classModel, TypeName typeName) {
        if (typeName == null) {
            // just use default from interface, we only need to declare this on a type that explicitly implements
            // QualifiedProvider
            return;
        }
        classModel.addInterface(INJECT_QUALIFIED_PROVIDER_DESCRIPTOR);

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

    private void scopeHandler(TypeInfo typeInfo, ClassModel.Builder classModel, Set<TypeName> contracts) {
        if (!contracts.contains(INJECT_SCOPE_HANDLER)) {
            return;
        }

        TypeName handledScope = findHandledScope(typeInfo);

        classModel.addInterface(INJECT_SCOPE_HANDLER_DESCRIPTOR);
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
        for (TypeInfo info : typeInfo.interfaceTypeInfo()) {
            // first level only
            TypeName type = info.typeName();
            if (type.equals(INJECT_SCOPE_HANDLER) && !type.typeArguments().isEmpty()) {
                return type.typeArguments().getFirst();
            }
        }
        throw new CodegenException("Type implementing ScopeHandler did not declare the scope type: " + typeInfo.typeName()
                                           + ", ScopeHandler<Type> must be directly implemented by the service.");
    }

    private void createForMethod(TypeInfo typeInfo, ClassModel.Builder classModel, SuperType superType, Set<TypeName> contracts) {
        Optional<Annotation> createFor = typeInfo.findAnnotation(INJECTION_CREATE_FOR);
        if (!superType.hasSupertype() && createFor.isEmpty()) {
            // this is the default
            return;
        }
        if (createFor.isPresent()) {
            // make sure that driven by does not implement providers
            if (contracts.contains(INJECTION_POINT_PROVIDER)
                    || contracts.contains(SERVICES_PROVIDER)
                    || contracts.contains(QUALIFIED_PROVIDER)) {
                throw new CodegenException("Service " + typeInfo.typeName().classNameWithEnclosingNames()
                                                   + " is annotated with @CreateFor, and as such it must not implement any "
                                                   + "provider interfaces. Contracts: " + contracts,
                                           typeInfo.originatingElementValue());
            }

            TypeName createForType = createFor.get()
                    .typeValue()
                    .orElseThrow(() -> new CodegenException(INJECTION_CREATE_FOR.fqName()
                                                                    + ".value() is required, yet not found on type: "
                                                                    + typeInfo.typeName().fqName()));

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
                throw new CodegenException("CreateFor type used on " + typeInfo.typeName().fqName() + " does not have a "
                                                   + "package defined. Package is mandatory. If the type is a generated"
                                                   + " prototype, please use the Blueprint type instead.");
            }

            // used from lambda
            TypeName createForTypeFinal = createForType;
            classModel.addInterface(ServiceCodegenTypes.INJECT_CREATE_FOR_DESCRIPTOR);

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

    private SuperType superType(TypeInfo typeInfo, Collection<TypeInfo> services) {
        // find super type if it is also a service (or has a service descriptor)

        // check if the super type is part of current annotation processing
        Optional<TypeInfo> superTypeInfoOptional = typeInfo.superTypeInfo();
        if (superTypeInfoOptional.isEmpty()) {
            return new SuperType();
        }
        TypeInfo superType = superTypeInfoOptional.get();
        boolean isCore = superType.hasAnnotation(SERVICE_ANNOTATION_PROVIDER);

        TypeName expectedSuperDescriptor = ctx.descriptorType(superType.typeName());
        TypeName superTypeToExtend = TypeName.builder(expectedSuperDescriptor)
                .addTypeArgument(TypeName.create("T"))
                .build();
        for (TypeInfo service : services) {
            if (service.typeName().equals(superType.typeName())) {
                return new SuperType(true, superTypeToExtend, service, isCore);
            }
        }
        // if not found in current list, try checking existing types
        return ctx.typeInfo(expectedSuperDescriptor)
                .map(it -> new SuperType(true, superTypeToExtend, superType, isCore))
                .orElseGet(SuperType::new);
    }

    // find constructor with @Inject, if none, find the first constructor (assume @Inject)
    private TypedElementInfo injectConstructor(TypeInfo typeInfo) {
        var constructors = typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .filter(it -> it.hasAnnotation(ServiceCodegenTypes.INJECTION_INJECT))
                .collect(Collectors.toUnmodifiableList());
        if (constructors.size() > 1) {
            throw new CodegenException("There can only be one constructor annotated with "
                                               + ServiceCodegenTypes.INJECTION_INJECT.fqName() + ", but there were "
                                               + constructors.size(),
                                       typeInfo.originatingElementValue());
        }
        if (!constructors.isEmpty()) {
            // @Injection.Inject
            TypedElementInfo first = constructors.getFirst();
            if (ElementInfoPredicates.isPrivate(first)) {
                throw new CodegenException("Constructor annotated with " + ServiceCodegenTypes.INJECTION_INJECT.fqName()
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
                .filter(ElementInfoPredicates.hasAnnotation(ServiceCodegenTypes.INJECTION_INJECT))
                .toList();
        var firstFound = injectFields.stream()
                .filter(ElementInfoPredicates::isPrivate)
                .findFirst();
        if (firstFound.isPresent()) {
            throw new CodegenException("Discovered " + ServiceCodegenTypes.INJECTION_INJECT.fqName()
                                               + " annotation on private field(s). We cannot support private field injection.",
                                       firstFound.get().originatingElementValue());
        }
        firstFound = injectFields.stream()
                .filter(ElementInfoPredicates::isStatic)
                .findFirst();
        if (firstFound.isPresent()) {
            throw new CodegenException("Discovered " + ServiceCodegenTypes.INJECTION_INJECT.fqName()
                                               + " annotation on static field(s).",
                                       firstFound.get().originatingElementValue());
        }
        return injectFields;
    }

    private List<MethodDefinition> methodParams(Collection<TypeInfo> services,
                                                TypeInfo service,
                                                SuperType superType,
                                                List<ParamDefinition> allParams,
                                                AtomicInteger methodCounter,
                                                AtomicInteger paramCounter) {
        TypeName serviceType = service.typeName();
        // Discover all methods on this type that are not private or static and that have @Inject
        List<TypedElementInfo> atInjectMethods = service.elementInfo()
                .stream()
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates.hasAnnotation(ServiceCodegenTypes.INJECTION_INJECT))
                .toList();

        List<MethodDefinition> result = new ArrayList<>();
        // add all @Inject methods(always)
        // there is no supertype, no need to check anything else
        atInjectMethods.stream()
                .map(it -> {
                    TypeName declaringType;
                    boolean overrides;

                    if (superType.hasSupertype()) {
                        declaringType = overrides(services,
                                                  superType.superType(),
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

        if (!superType.hasSupertype()) {
            return result;
        }

        // discover all methods that are not private or static and that do NOT have @Inject
        List<TypedElementInfo> otherMethods = service.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(it -> !it.hasAnnotation(ServiceCodegenTypes.INJECTION_INJECT))
                .toList();

        // some of the methods we declare that do not have @Inject may disable injection, we need to check that

        for (TypedElementInfo otherMethod : otherMethods) {
            // now find all methods that override a method that is annotated from any supertype (ouch)
            Optional<TypeName> overrides = overrides(services,
                                                     superType.superType(),
                                                     otherMethod,
                                                     otherMethod.parameterArguments()
                                                             .stream()
                                                             .map(TypedElementInfo::typeName)
                                                             .toList(),
                                                     serviceType.packageName(),
                                                     ServiceCodegenTypes.INJECTION_INJECT);
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
    private MethodDefinition toMethodDefinition(TypeInfo service,
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

    private List<ParamDefinition> toMethodParams(TypeInfo service,
                                                 AtomicInteger paramCounter,
                                                 TypedElementInfo method,
                                                 String methodId,
                                                 String methodConstantName) {
        return method.parameterArguments()
                .stream()
                .map(param -> {
                    String constantName = "IP_PARAM_" + paramCounter.getAndIncrement();
                    RegistryCodegenContext.Assignment assignment = translateParameter(param.typeName(), constantName);
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
                                               contract("Method " + service.typeName()
                                                                .fqName() + "#" + method.elementName() + ", parameter: "
                                                                + param.elementName(),
                                                        assignment.usedType()),
                                               method.accessModifier(),
                                               methodId);
                })
                .toList();
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
                SuperType superType = superType(type, services);
                if (superType.hasSupertype()) {
                    // do not care about annotations, we already have a match
                    var fromSuperHierarchy = overrides(services, superType.superType(), method, arguments, currentPackage);
                    return Optional.of(fromSuperHierarchy.orElseGet(type::typeName));
                }
                // there is no supertype, this type declares the method
                return Optional.of(type.typeName());
            }
        }

        // we did not find a method on this type, let's look above
        SuperType superType = superType(type, services);
        if (superType.hasSupertype()) {
            return overrides(services, superType.superType(), method, arguments, currentPackage, expectedAnnotations);
        }
        return Optional.empty();
    }

    private void params(Collection<TypeInfo> services,
                        TypeInfo service,
                        SuperType superType,
                        List<MethodDefinition> methods,
                        List<ParamDefinition> params,
                        TypedElementInfo constructor,
                        List<TypedElementInfo> fieldInjectElements) {
        AtomicInteger paramCounter = new AtomicInteger();
        AtomicInteger methodCounter = new AtomicInteger();

        if (!constructor.parameterArguments().isEmpty()) {
            injectConstructorParams(service, params, paramCounter, constructor);
        }

        fieldInjectElements
                .forEach(it -> fieldParam(service, params, paramCounter, it));

        methods.addAll(methodParams(services,
                                    service,
                                    superType,
                                    params,
                                    methodCounter,
                                    paramCounter));

    }

    private void injectConstructorParams(TypeInfo service,
                                         List<ParamDefinition> result,
                                         AtomicInteger paramCounter,
                                         TypedElementInfo constructor) {
        constructor.parameterArguments()
                .stream()
                .map(param -> {
                    String constantName = "IP_PARAM_" + paramCounter.getAndIncrement();
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
                                               qualifiers(service, param),
                                               contract(service.typeName()
                                                                .fqName() + " Constructor parameter: " + param.elementName(),
                                                        assignment.usedType()),
                                               constructor.accessModifier(),
                                               "<init>");
                })
                .forEach(result::add);
    }

    private void fieldParam(TypeInfo service, List<ParamDefinition> result, AtomicInteger paramCounter, TypedElementInfo field) {
        String constantName = "IP_PARAM_" + paramCounter.getAndIncrement();
        RegistryCodegenContext.Assignment assignment = translateParameter(field.typeName(), constantName);

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
                                       qualifiers(service, field),
                                       contract("Field " + service.typeName().fqName() + "." + field.elementName(),
                                                assignment.usedType()),
                                       field.accessModifier(),
                                       null));
    }

    private void qualifiers(TypeInfo service, Set<Annotation> qualifiers) {
        qualifiers.addAll(qualifiers(service, service));
        // the type info itself
        if (service.hasAnnotation(INJECTION_CREATE_FOR)) {
            // only for the type, not for injection points
            qualifiers.add(WILDCARD_NAMED);
        }
    }

    private Set<Annotation> qualifiers(TypeInfo service, Annotated element) {
        Set<Annotation> result = new LinkedHashSet<>();

        for (Annotation anno : element.annotations()) {
            if (service.hasMetaAnnotation(anno.typeName(), ServiceCodegenTypes.INJECTION_QUALIFIER)) {
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
            if (firstType.equals(INJECT_SERVICE_INSTANCE)) {
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
            if (firstType.equals(INJECT_SERVICE_INSTANCE)) {
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
        if (typeName.equals(INJECT_SERVICE_INSTANCE)) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with ServiceInstance type must have a"
                                                           + " declared type argument: " + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }

        return typeName;
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
            result.computeIfAbsent(param.contract().fqName(),
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

    private Optional<TypeName> scope(TypeInfo service) {
        Set<TypeName> result = new LinkedHashSet<>();

        for (Annotation anno : service.annotations()) {
            TypeName annoType = anno.typeName();
            if (service.hasMetaAnnotation(annoType, ServiceCodegenTypes.INJECTION_SCOPE)) {
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

        if (result.isEmpty() && service.hasAnnotation(INJECTION_CREATE_FOR)) {
            result.add(INJECTION_SINGLETON);
        }

        return result.stream().findFirst();
    }

    private void contracts(TypeInfo typeInfo,
                           boolean contractEligible,
                           Set<TypeName> collectedContracts,
                           Set<String> collectedFullyQualified,
                           AtomicReference<TypeName> qualifiedProviderQualifier) {
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

        if (typeName.equals(QUALIFIED_PROVIDER)) {
            // a very special case
            if (typeName.typeArguments().isEmpty()) {
                // this is the QualifiedProvider interface itself, no need to do anything
                return;
            }
            TypeName providedType = typeName.typeArguments().get(1); // second type
            if (providedType.generic()) {
                // just a <T> or similar
                return;
            }
            // add the qualifier of this qualified provider (we care about the first one - if supertype implements it, ignore
            qualifiedProviderQualifier.compareAndSet(null, typeName.typeArguments().get(0));
            collectedContracts.add(QUALIFIED_PROVIDER);
            if (TypeNames.OBJECT.equals(providedType)) {
                collectedContracts.add(TypeNames.OBJECT);
            } else {
                Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(providedType);
                if (providedTypeInfo.isPresent()) {
                    contracts(providedTypeInfo.get(),
                              true,
                              collectedContracts,
                              collectedFullyQualified,
                              qualifiedProviderQualifier);
                } else {
                    collectedContracts.add(providedType);
                    if (!collectedFullyQualified.add(providedType.resolvedName())) {
                        // let us go no further, this type was already processed
                        return;
                    }
                }
            }
        } else if (typeName.isSupplier()
                || typeName.equals(INJECTION_POINT_PROVIDER)
                || typeName.equals(SERVICES_PROVIDER)) {
            // this may be the interface itself, and then it does not have a type argument
            if (!typeName.typeArguments().isEmpty()) {
                // provider must have a type argument (and the type argument is an automatic contract
                TypeName providedType = typeName.typeArguments().getFirst();
                if (!providedType.generic()) {
                    Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(providedType);
                    if (providedTypeInfo.isPresent()) {
                        contracts(providedTypeInfo.get(),
                                  true,
                                  collectedContracts,
                                  collectedFullyQualified,
                                  qualifiedProviderQualifier);
                    } else {
                        collectedContracts.add(providedType);
                        if (!collectedFullyQualified.add(providedType.resolvedName())) {
                            // let us go no further, this type was already processed
                            return;
                        }
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
                                                           collectedFullyQualified,
                                                           qualifiedProviderQualifier
        ));
        // interfaces are considered contracts by default
        typeInfo.interfaceTypeInfo().forEach(it -> contracts(it,
                                                             contractEligible,
                                                             collectedContracts,
                                                             collectedFullyQualified,
                                                             qualifiedProviderQualifier
        ));
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
                .name("TYPE")
                .addContentCreate(descriptorType.genericTypeName()));

        classModel.addField(field -> field
                .isStatic(true)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
                .type(TypeNames.TYPE_NAME)
                .name("SERVICE_TYPE")
                .addContentCreate(serviceType.genericTypeName()));
    }

    private void injectionPointFields(ClassModel.Builder classModel,
                                      TypeInfo service,
                                      Map<String, GenericTypeDeclaration> genericTypes,
                                      List<ParamDefinition> params) {
        // constant for injection points
        for (ParamDefinition param : params) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PUBLIC)
                    .isStatic(true)
                    .isFinal(true)
                    .type(ServiceCodegenTypes.INJECTION_POINT)
                    .name(param.constantName())
                    .description(ipIdDescription(service, param))
                    .update(it -> {
                        it.addContent(ServiceCodegenTypes.INJECTION_POINT)
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

                        it.addContent(".build()")
                                .decreaseContentPadding()
                                .decreaseContentPadding();
                    }));
        }
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
                .type(LIST_OF_IP_IDS)
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

    private void codeGenQualifier(Field.Builder field, Annotation qualifier) {
        if (qualifier.value().isPresent()) {
            field.addContent(ServiceCodegenTypes.INJECT_QUALIFIER)
                    .addContent(".create(")
                    .addContentCreate(qualifier.typeName())
                    .addContent(", \"" + qualifier.value().get() + "\")");
            return;
        }

        field.addContent(ServiceCodegenTypes.INJECT_QUALIFIER)
                .addContent(".create(")
                .addContentCreate(qualifier.typeName())
                .addContent(")");
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
                .addContentLine("return TYPE;"));
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
        // List<Ip> dependencies()
        boolean hasSuperType = superType.hasSupertype();
        if (hasSuperType || !params.isEmpty()) {
            classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                    .returnType(LIST_OF_IP_IDS)
                    .name("dependencies")
                    .update(it -> {
                        if (hasSuperType && !superType.superTypeIsCore()) {
                            it.addContent("return ")
                                    .addContent(INJECT_IP_SUPPORT)
                                    .addContentLine(".combineIps(DEPENDENCIES, super.dependencies());");
                        } else {
                            // when super type is a core service, it only can have constructor dependencies - no need to combine
                            it.addContentLine("return DEPENDENCIES;");
                        }
                    }));
        }
    }

    private void instantiateMethod(ClassModel.Builder classModel,
                                   TypeName serviceType,
                                   List<ParamDefinition> params,
                                   boolean isAbstractClass,
                                   boolean constructorIntercepted,
                                   boolean interceptedMethods) {
        if (isAbstractClass) {
            return;
        }

        // T instantiate(InjectionContext ctx__helidonInject, InterceptionMetadata interceptMeta__helidonInject)
        TypeName toInstantiate = interceptedMethods
                ? interceptedTypeName(serviceType)
                : serviceType;

        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(serviceType)
                .name("instantiate")
                .addParameter(ctxParam -> ctxParam.type(ServiceCodegenTypes.SERVICE_DEPENDENCY_CONTEXT)
                        .name("ctx__helidonInject"))
                .addParameter(interceptMeta -> interceptMeta.type(ServiceCodegenTypes.INTERCEPTION_METADATA)
                        .name("interceptMeta__helidonInject"))
                .update(it -> {
                    if (constructorIntercepted) {
                        createInstantiateInterceptBody(it, params);
                    } else {
                        createInstantiateBody(toInstantiate, it, params, interceptedMethods);
                    }
                }));

        if (constructorIntercepted) {
            classModel.addMethod(method -> method.returnType(serviceType)
                    .name("doInstantiate")
                    .accessModifier(AccessModifier.PRIVATE)
                    .addParameter(interceptMeta -> interceptMeta.type(ServiceCodegenTypes.INTERCEPTION_METADATA)
                            .name("interceptMeta"))
                    .addParameter(ctrParams -> ctrParams.type(TypeName.create("Object..."))
                            .name("params"))
                    .update(it -> createDoInstantiateBody(toInstantiate, it, params, interceptedMethods)));
        }
    }

    private void createInstantiateInterceptBody(Method.Builder method,
                                                List<ParamDefinition> params) {
        List<ParamDefinition> constructorParams = GenerateServiceDescriptor.declareCtrParamsAndGetThem(method, params);

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
                .addContent(ServiceCodegenTypes.INVOCATION_EXCEPTION)
                .addContentLine("(\"Failed to instantiate \" + SERVICE_TYPE.fqName(), e__helidonInject, false);")
                .addContentLine("}");
    }

    private void createInstantiateBody(TypeName serviceType,
                                       Method.Builder method,
                                       List<ParamDefinition> params,
                                       boolean interceptedMethods) {
        List<ParamDefinition> constructorParams = GenerateServiceDescriptor.declareCtrParamsAndGetThem(method, params);
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

    private void injectMethod(ClassModel.Builder classModel,
                              List<ParamDefinition> params,
                              SuperType superType,
                              List<MethodDefinition> methods,
                              boolean canIntercept,
                              List<TypedElements.ElementMeta> maybeIntercepted) {

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
                .addParameter(interceptMeta -> interceptMeta.type(ServiceCodegenTypes.INTERCEPTION_METADATA)
                        .name("interceptMeta__helidonInject"))
                .addParameter(injectedParam -> injectedParam.type(SET_OF_SIGNATURES)
                        .name("injected__helidonInject"))
                .addParameter(instanceParam -> instanceParam.type(GENERIC_T_TYPE)
                        .name("instance__helidonInject"))
                .update(it -> createInjectBody(it,
                                               superType.hasSupertype(),
                                               methods,
                                               fields,
                                               canIntercept,
                                               maybeIntercepted)));
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

    private void qualifiersMethod(ClassModel.Builder classModel, Set<Annotation> qualifiers, SuperType superType) {
        // qualifier field is always needed, as it is used for interception
        classModel.addField(qualifiersField -> qualifiersField
                .isStatic(true)
                .isFinal(true)
                .name("QUALIFIERS")
                .type(SET_OF_QUALIFIERS)
                .addContent(Set.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = qualifiers.iterator();
                    while (iterator.hasNext()) {
                        codeGenQualifier(it, iterator.next());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));

        if (qualifiers.isEmpty() && !superType.hasSupertype()) {
            return;
        }

        // List<Qualifier> qualifiers()
        classModel.addMethod(qualifiersMethod -> qualifiersMethod.name("qualifiers")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(SET_OF_QUALIFIERS)
                .addContentLine("return QUALIFIERS;"));
    }

    private void scopeMethod(ClassModel.Builder classModel, TypeName scope) {
        // TypeName scope()
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

    private void runLevelMethod(TypeInfo typeInfo, ClassModel.Builder classModel, SuperType superType) {
        boolean hasSuperType = superType.hasSupertype();
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
        return typeInfo.findAnnotation(ServiceCodegenTypes.INJECTION_RUN_LEVEL)
                .flatMap(Annotation::doubleValue);
    }

    private void notifyObservers(RegistryRoundContext roundContext, Collection<TypeInfo> descriptorsRequired) {
        // we have correct classloader set in current thread context
        if (!observers.isEmpty()) {
            Set<TypedElementInfo> elements = descriptorsRequired.stream()
                    .flatMap(it -> it.elementInfo().stream())
                    .collect(Collectors.toSet());
            observers.forEach(it -> it.onProcessingEvent(roundContext, elements));
        }
    }

    private RegistryCodegenContext.Assignment translateParameter(TypeName typeName, String constantName) {
        return ctx.assignment(typeName, "ctx__helidonInject.dependency(" + constantName + ")");
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
