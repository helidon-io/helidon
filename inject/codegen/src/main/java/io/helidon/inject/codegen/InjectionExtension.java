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

package io.helidon.inject.codegen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
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
import io.helidon.inject.codegen.spi.InjectCodegenExtension;
import io.helidon.inject.codegen.spi.InjectCodegenObserver;
import io.helidon.inject.codegen.spi.InjectCodegenObserverProvider;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.codegen.CodegenUtil.toConstantName;
import static java.util.function.Predicate.not;

class InjectionExtension implements InjectCodegenExtension {
    static final TypeName LIST_OF_ANNOTATIONS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.ANNOTATION)
            .build();
    static final TypeName SET_OF_QUALIFIERS = TypeName.builder(TypeNames.SET)
            .addTypeArgument(InjectCodegenTypes.QUALIFIER)
            .build();
    static final TypeName SET_OF_TYPES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.TYPE_NAME)
            .build();

    static final TypeName SET_OF_SIGNATURES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.STRING)
            .build();
    private static final TypeName LIST_OF_IP_IDS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(InjectCodegenTypes.IP_ID)
            .build();

    private static final TypeName SERVICE_SOURCE_TYPE = TypeName.builder(InjectCodegenTypes.SERVICE_DESCRIPTOR)
            .addTypeArgument(TypeName.create("T"))
            .build();
    private static final TypeName GENERATOR = TypeName.create(InjectionExtension.class);
    private static final Annotation RUNTIME_RETENTION = Annotation.create(Retention.class, RetentionPolicy.RUNTIME.name());
    private static final Annotation CLASS_RETENTION = Annotation.create(Retention.class, RetentionPolicy.CLASS.name());
    private static final TypedElementInfo DEFAULT_CONSTRUCTOR = TypedElementInfo.builder()
            .typeName(TypeNames.OBJECT)
            .accessModifier(AccessModifier.PUBLIC)
            .kind(ElementKind.CONSTRUCTOR)
            .build();
    private static final TypeName GENERIC_T_TYPE = TypeName.createFromGenericDeclaration("T");

    private final InjectionCodegenContext ctx;
    private final boolean autoAddContracts;
    private final InterceptionStrategy interceptionStrategy;
    private final Set<TypeName> scopeMetaAnnotations;
    private final List<InjectCodegenObserver> observers;

    InjectionExtension(InjectionCodegenContext codegenContext) {
        this.ctx = codegenContext;
        CodegenOptions options = codegenContext.options();
        this.autoAddContracts = InjectOptions.AUTO_ADD_NON_CONTRACT_INTERFACES.value(options);
        this.interceptionStrategy = InjectOptions.INTERCEPTION_STRATEGY.value(options);
        this.scopeMetaAnnotations = InjectOptions.SCOPE_META_ANNOTATIONS.value(options);
        this.observers = HelidonServiceLoader.create(ServiceLoader.load(InjectCodegenObserverProvider.class,
                                                                        InjectionExtension.class.getClassLoader()))
                .stream()
                .map(it -> it.create(codegenContext))
                .toList();
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> descriptorsRequired = roundContext.types();

        for (TypeInfo typeInfo : descriptorsRequired) {
            generateDescriptor(descriptorsRequired, typeInfo);
        }

        notifyObservers(roundContext, descriptorsRequired);
    }

    private void generateDescriptor(Collection<TypeInfo> services,
                                    TypeInfo typeInfo) {
        if (typeInfo.kind() == ElementKind.INTERFACE) {
            // we cannot support multiple inheritance, so descriptors for interfaces do not make sense
            return;
        }
        boolean isAbstractClass = typeInfo.elementModifiers().contains(Modifier.ABSTRACT)
                && typeInfo.kind() == ElementKind.CLASS;

        SuperType superType = superType(typeInfo, services);

        // this set now contains all fields, constructors, and methods that may be intercepted, as they contain
        // an annotation that is an interception trigger (based on interceptionStrategy)
        Set<TypedElementInfo> maybeIntercepted = maybeIntercepted(typeInfo);
        boolean canIntercept = !maybeIntercepted.isEmpty();
        boolean methodsIntercepted = maybeIntercepted.stream()
                .anyMatch(ElementInfoPredicates::isMethod);

        TypeName serviceType = typeInfo.typeName();
        // this must result in generating a service descriptor file
        TypeName descriptorType = ctx.descriptorType(serviceType);

        List<ParamDefinition> params = new ArrayList<>();
        List<MethodDefinition> methods = new ArrayList<>();

        TypedElementInfo constructorInjectElement = injectConstructor(typeInfo);
        List<TypedElementInfo> fieldInjectElements = fieldInjectElements(typeInfo);

        boolean constructorIntercepted = maybeIntercepted.contains(constructorInjectElement);

        params(services,
               typeInfo,
               superType,
               methods,
               params,
               constructorInjectElement,
               fieldInjectElements);

        Map<String, GenericTypeDeclaration> genericTypes = genericTypes(params, methods);
        Optional<TypeName> scope = scope(typeInfo);
        Set<Annotation> qualifiers = qualifiers(typeInfo, typeInfo);
        Set<TypeName> contracts = new HashSet<>();
        contracts(contracts, typeInfo, autoAddContracts);

        // declare the class
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

        if (superType.hasSupertype()) {
            classModel.superType(superType.superDescriptorType());
        } else {
            classModel.addInterface(SERVICE_SOURCE_TYPE);
        }

            /*
            Fields
             */
        singletonInstanceField(classModel, descriptorType);
        serviceTypeFields(classModel, serviceType, descriptorType);
        typeFields(classModel, genericTypes);
        contractsField(classModel, contracts);
        qualifiersField(classModel, qualifiers);
        scopeField(classModel, scope.orElse(InjectCodegenTypes.INJECTION_SERVICE));
        methodFields(classModel, methods);

        // public fields are last, so they do not intersect with private fields (it is not as nice to read)
        // they cannot be first, as they require some of the private fields
        injectionPointFields(classModel, typeInfo, genericTypes, params);
        // dependencies require IP IDs, so they really must be last
        dependenciesField(classModel, params);

        if (canIntercept) {
            annotationsField(classModel, typeInfo);
            // if constructor intercepted, add its element
            if (constructorIntercepted) {
                constructorElementField(classModel, constructorInjectElement);
            }
            // if injected field intercepted, add its element (other fields cannot be intercepted)
            fieldInjectElements.stream()
                    .filter(maybeIntercepted::contains)
                    .forEach(fieldElement -> fieldElementField(classModel, fieldElement));
            // all other interception is done on method level and is handled by the
            // service descriptor delegating to a generated type
        }

            /*
            Constructor
             */
        // add protected constructor
        classModel.addConstructor(constructor -> constructor.description("Constructor with no side effects")
                .accessModifier(AccessModifier.PROTECTED));

            /*
            Methods
             */
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
        scopesMethod(classModel);
        weightMethod(typeInfo, classModel, superType);
        runLevelMethod(typeInfo, classModel, superType);

        ctx.addDescriptor(serviceType,
                          descriptorType,
                          classModel);

        if (methodsIntercepted) {
            TypeName interceptedType = TypeName.builder(serviceType)
                    .className(serviceType.classNameWithEnclosingNames().replace('.', '_') + "__Intercepted")
                    .build();

            var generator = new InterceptedTypeGenerator(serviceType,
                                                         descriptorType,
                                                         interceptedType,
                                                         constructorInjectElement,
                                                         maybeIntercepted.stream()
                                                                 .filter(ElementInfoPredicates::isMethod)
                                                                 .toList());

            ctx.addType(interceptedType,
                        generator.generate(),
                        serviceType,
                        typeInfo.originatingElement().orElse(serviceType));
        }
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
        for (TypeInfo service : services) {
            if (service.typeName().equals(superType.typeName())) {
                return new SuperType(true, superTypeToExtend, service);
            }
        }
        // if not found in current list, try checking existing types
        return ctx.typeInfo(expectedSuperDescriptor)
                .map(it -> new SuperType(true, superTypeToExtend, superType))
                .orElseGet(SuperType::noSuperType);
    }

    private Set<TypedElementInfo> maybeIntercepted(TypeInfo typeInfo) {
        if (interceptionStrategy == InterceptionStrategy.NONE) {
            return Set.of();
        }

        Set<TypedElementInfo> result = Collections.newSetFromMap(new IdentityHashMap<>());

        // depending on strategy
        if (hasInterceptTrigger(typeInfo, typeInfo)) {
            // we cannot intercept private stuff (never modify source code or bytecode!)
            result.addAll(typeInfo.elementInfo()
                                  .stream()
                                  .filter(it -> it.accessModifier() != AccessModifier.PRIVATE)
                                  .toList());
            result.add(DEFAULT_CONSTRUCTOR); // we must intercept construction as well
        } else {
            result.addAll(typeInfo.elementInfo().stream()
                                  .filter(elementInfo -> hasInterceptTrigger(typeInfo, elementInfo))
                                  .peek(it -> {
                                      if (it.accessModifier() == AccessModifier.PRIVATE) {
                                          throw new CodegenException(typeInfo.typeName()
                                                                             .fqName() + "#" + it.elementName() + " is declared "
                                                                             + "as private, but has interceptor trigger "
                                                                             + "annotation declared. "
                                                                             + "This cannot be supported, as we do not modify "
                                                                             + "sources or bytecode.",
                                                                     it.originatingElement().orElse(typeInfo.typeName()));
                                      }
                                  })
                                  .toList());
        }

        return result;
    }

    private boolean hasInterceptTrigger(TypeInfo typeInfo, Annotated element) {
        for (Annotation annotation : element.annotations()) {
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.EXPLICIT.ordinal()) {
                if (typeInfo.hasMetaAnnotation(annotation.typeName(), InjectCodegenTypes.INTERCEPTED_TRIGGER)) {
                    return true;
                }
            }
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.ALL_RUNTIME.ordinal()) {
                Optional<Annotation> retention = typeInfo.metaAnnotation(annotation.typeName(),
                                                                         TypeNames.RETENTION);
                boolean isRuntime = retention.map(RUNTIME_RETENTION::equals).orElse(false);
                if (isRuntime) {
                    return true;
                }
            }
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.ALL_RETAINED.ordinal()) {
                Optional<Annotation> retention = typeInfo.metaAnnotation(annotation.typeName(),
                                                                         TypeNames.RETENTION);
                boolean isClass = retention.map(CLASS_RETENTION::equals).orElse(false);
                if (isClass) {
                    return true;
                }
            }
        }
        return false;
    }

    // find constructor with @Inject, if none, find the first constructor (assume @Inject)
    private TypedElementInfo injectConstructor(TypeInfo typeInfo) {
        // first @Inject
        return typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .filter(it -> it.hasAnnotation(InjectCodegenTypes.INJECTION_INJECT))
                .findFirst()
                // or first non-private constructor
                .or(() -> typeInfo.elementInfo()
                        .stream()
                        .filter(not(ElementInfoPredicates::isPrivate))
                        .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                        .findFirst())
                // or default constructor
                .orElse(DEFAULT_CONSTRUCTOR);
    }

    private List<TypedElementInfo> fieldInjectElements(TypeInfo typeInfo) {
        return typeInfo.elementInfo()
                .stream()
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::isField)
                .filter(ElementInfoPredicates.hasAnnotation(InjectCodegenTypes.INJECTION_INJECT))
                .toList();
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
                .filter(ElementInfoPredicates.hasAnnotation(InjectCodegenTypes.INJECTION_INJECT))
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
                .filter(it -> !it.hasAnnotation(InjectCodegenTypes.INJECTION_INJECT))
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
                                                     InjectCodegenTypes.INJECTION_INJECT);
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
                    InjectionCodegenContext.Assignment assignment = translateParameter(param.typeName(), constantName);
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

            boolean realOverride = true;
            if (superMethod.accessModifier() == AccessModifier.PACKAGE_PRIVATE
                    && !currentPackage.equals(type.typeName().packageName())) {
                // method has same signature, but is package local and is in a different package
                realOverride = false;
            }

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
        if (superType.hasSupertype) {
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
                    InjectionCodegenContext.Assignment assignment = translateParameter(param.typeName(), constantName);
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
        InjectionCodegenContext.Assignment assignment = translateParameter(field.typeName(), constantName);

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

    private Set<Annotation> qualifiers(TypeInfo service, Annotated element) {
        Set<Annotation> result = new LinkedHashSet<>();

        for (Annotation anno : element.annotations()) {
            if (service.hasMetaAnnotation(anno.typeName(), InjectCodegenTypes.INJECTION_QUALIFIER)) {
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
         */

        if (typeName.isOptional()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with Optional type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.isList()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with List type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.isSupplier()) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException("Injection point with Supplier type must have a declared type argument: "
                                                           + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }
        if (typeName.equals(InjectCodegenTypes.SERVICE_PROVIDER)) {
            if (typeName.typeArguments().isEmpty()) {
                throw new IllegalArgumentException(
                        "Injection point with ServiceProvider type must have a declared type argument: " + description);
            }
            return contract(description, typeName.typeArguments().getFirst());
        }

        return typeName;
    }

    private Map<String, GenericTypeDeclaration> genericTypes(List<ParamDefinition> params, List<MethodDefinition> methods) {
        // we must use map by string (as type name is equal if the same class, not full generic declaration)
        Map<String, GenericTypeDeclaration> result = new LinkedHashMap<>();
        AtomicInteger counter = new AtomicInteger();

        for (ParamDefinition param : params) {
            result.computeIfAbsent(param.translatedType().resolvedName(),
                                   type -> new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                      param.declaredType()));
            result.computeIfAbsent(param.contract().fqName(),
                                   type -> new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                      param.declaredType()));
        }

        for (MethodDefinition method : methods) {
            method.params()
                    .forEach(it -> result.computeIfAbsent(it.declaredType().resolvedName(),
                                                          type -> new GenericTypeDeclaration("TYPE_" + counter.getAndIncrement(),
                                                                                             it.declaredType())));
        }

        return result;
    }

    private Optional<TypeName> scope(TypeInfo service) {
        Set<TypeName> result = new LinkedHashSet<>();

        for (Annotation anno : service.annotations()) {
            TypeName annoType = anno.typeName();
            if (service.hasMetaAnnotation(annoType, InjectCodegenTypes.INJECTION_SCOPE)) {
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

        return result.stream().findFirst();
    }

    private void contracts(Set<TypeName> collectedContracts, TypeInfo typeInfo, boolean contractEligible) {
        TypeName typeName = typeInfo.typeName();

        if (contractEligible) {
            collectedContracts.add(typeName);
        }

        if (typeName.isSupplier()
                || typeName.equals(InjectCodegenTypes.INJECTION_POINT_PROVIDER)
                || typeName.equals(InjectCodegenTypes.SERVICE_PROVIDER)) {
            // this may be the interface itself, and then it does not have a type argument
            if (!typeName.typeArguments().isEmpty()) {
                // provider must have a type argument (and the type argument is an automatic contract
                TypeName providedType = typeName.typeArguments().getFirst();
                if (!providedType.generic()) {
                    collectedContracts.add(providedType);
                }
            }

            // provider itself is a contract
            collectedContracts.add(typeName);
        }

        // add contracts from interfaces and types annotated as @Contract
        typeInfo.findAnnotation(InjectCodegenTypes.INJECTION_CONTRACT)
                .ifPresent(it -> collectedContracts.add(typeInfo.typeName()));

        // add contracts from @ExternalContracts
        typeInfo.findAnnotation(InjectCodegenTypes.INJECTION_EXTERNAL_CONTRACTS)
                .ifPresent(it -> collectedContracts.addAll(it.typeValues().orElseGet(List::of)));

        // go through hierarchy
        typeInfo.superTypeInfo().ifPresent(it -> contracts(collectedContracts, it, contractEligible));
        // interfaces are considered contracts by default
        typeInfo.interfaceTypeInfo().forEach(it -> contracts(collectedContracts, it, contractEligible));
    }

    private void singletonInstanceField(ClassModel.Builder classModel, TypeName descriptorType) {
        // singleton instance of the descriptor
        classModel.addField(instance -> instance.description("Global singleton instance for this descriptor.")
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .isFinal(true)
                .type(descriptorType)
                .name("INSTANCE")
                .defaultValueContent("new " + descriptorType.className() + "()"));
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
                .name("INFO_TYPE")
                .addContentCreate(descriptorType.genericTypeName()));
    }

    private void typeFields(ClassModel.Builder classModel, Map<String, GenericTypeDeclaration> genericTypes) {
        // constants for injection point parameter types (used by next section)
        genericTypes.forEach((typeName, generic) -> classModel.addField(field -> field.accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(TypeNames.TYPE_NAME)
                .name(generic.constantName())
                .update(it -> {
                    if (typeName.indexOf('.') < 0) {
                        // there is no package, we must use class (if this is a generic type, we have a problem)
                        it.addContent(TypeNames.TYPE_NAME)
                                .addContent(".create(")
                                .addContent(typeName)
                                .addContent(".class)");
                    } else {
                        it.addContentCreate(TypeName.create(typeName));
                    }
                })));
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
                    .type(InjectCodegenTypes.IP_ID)
                    .name(param.constantName())
                    .description(ipIdDescription(service, param))
                    .update(it -> {
                        it.addContent(InjectCodegenTypes.IP_ID)
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
                                                .addContent(param.kind.name())
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
                                .addContentLine(".descriptor(INFO_TYPE)")
                                .addContent(".field(\"")
                                .addContent(param.constantName())
                                .addContentLine("\")")
                                .addContent(".contract(")
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

    private void contractsField(ClassModel.Builder classModel, Set<TypeName> contracts) {
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

    private void qualifiersField(ClassModel.Builder classModel, Set<Annotation> qualifiers) {
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
    }

    private void codeGenQualifier(Field.Builder field, Annotation qualifier) {
        if (qualifier.value().isPresent()) {
            field.addContent(InjectCodegenTypes.QUALIFIER)
                    .addContent(".create(")
                    .addContentCreate(qualifier.typeName())
                    .addContent(", \"" + qualifier.value().get() + "\")");
            return;
        }

        field.addContent(InjectCodegenTypes.QUALIFIER)
                .addContent(".create(")
                .addContentCreate(qualifier.typeName())
                .addContent(")");
    }

    private void scopeField(ClassModel.Builder classModel, TypeName scope) {
        classModel.addField(scopesField -> scopesField
                .isStatic(true)
                .isFinal(true)
                .name("SCOPE")
                .type(TypeNames.TYPE_NAME)
                .addContentCreate(scope));
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

    private void annotationsField(ClassModel.Builder classModel, TypeInfo typeInfo) {
        classModel.addField(annotations -> annotations
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
                .name("infoType")
                .addContentLine("return INFO_TYPE;"));
    }

    private void contractsMethod(ClassModel.Builder classModel, Set<TypeName> contracts) {
        if (contracts.isEmpty()) {
            return;
        }
        // Set<Class<?>> contracts()
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .name("contracts")
                .returnType(SET_OF_TYPES)
                .addContentLine("return CONTRACTS;"));
    }

    private void dependenciesMethod(ClassModel.Builder classModel, List<ParamDefinition> params, SuperType superType) {
        // List<InjectionParameterId> dependencies()
        boolean hasSuperType = superType.hasSupertype();
        if (hasSuperType || !params.isEmpty()) {
            classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                    .returnType(LIST_OF_IP_IDS)
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
                                   boolean isAbstractClass,
                                   boolean constructorIntercepted,
                                   boolean interceptedMethods) {
        if (isAbstractClass) {
            return;
        }

        // T instantiate(InjectionContext ctx__helidonInject, InterceptionMetadata interceptMeta__helidonInject)
        TypeName toInstantiate = interceptedMethods
                ? TypeName.builder(serviceType).className(serviceType.className() + "__Intercepted").build()
                : serviceType;

        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .returnType(serviceType)
                .name("instantiate")
                .addParameter(ctxParam -> ctxParam.type(InjectCodegenTypes.INJECTION_CONTEXT)
                        .name("ctx__helidonInject"))
                .addParameter(interceptMeta -> interceptMeta.type(InjectCodegenTypes.INTERCEPTION_METADATA)
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
                    .addParameter(interceptMeta -> interceptMeta.type(InjectCodegenTypes.INTERCEPTION_METADATA)
                            .name("interceptMeta"))
                    .addParameter(ctrParams -> ctrParams.type(TypeName.create("Object..."))
                            .name("params"))
                    .update(it -> createDoInstantiateBody(toInstantiate, it, params, interceptedMethods)));
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
                .addContent(InjectCodegenTypes.INVOCATION_EXCEPTION)
                .addContentLine("(\"Failed to instantiate \" + SERVICE_TYPE.fqName(), e__helidonInject, false);")
                .addContentLine("}");
    }

    private List<ParamDefinition> declareCtrParamsAndGetThem(Method.Builder method, List<ParamDefinition> params) {
        /*
            var ipParam1_serviceProviders = ctx__helidonInject.param(IP_PARAM_1);
            var ipParam2_someOtherName = ctx__helidonInject.param(IP_PARAM_2);

            return new ConfigProducer(ipParam1_serviceProviders, someOtherName);
         */
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
                              Set<TypedElementInfo> maybeIntercepted) {

        // method for field and method injections
        List<ParamDefinition> fields = params.stream()
                .filter(it -> it.kind == ElementKind.FIELD)
                .toList();
        if (fields.isEmpty() && methods.isEmpty()) {
            // only generate this method if we do something
            return;
        }
        classModel.addMethod(method -> method.addAnnotation(Annotations.OVERRIDE)
                .name("inject")
                .addParameter(ctxParam -> ctxParam.type(InjectCodegenTypes.INJECTION_CONTEXT)
                        .name("ctx__helidonInject"))
                .addParameter(interceptMeta -> interceptMeta.type(InjectCodegenTypes.INTERCEPTION_METADATA)
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
                                  Set<TypedElementInfo> maybeIntercepted) {

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
            if (!method.isFinal) {
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

            if (!method.isFinal) {
                methodBuilder.addContentLine("}");
            }
            methodBuilder.addContentLine("");
        }
    }

    private void injectFieldBody(Method.Builder methodBuilder,
                                 ParamDefinition field,
                                 boolean canIntercept,
                                 Set<TypedElementInfo> maybeIntercepted) {
        if (canIntercept && maybeIntercepted.contains(field.elementInfo())) {
            methodBuilder.addContentLine(field.declaredType().resolvedName() + " "
                                                 + field.ipParamName()
                                                 + " = ctx__helidonInject.param(" + field.constantName() + ");");
            String interceptorsName = field.ipParamName() + "__interceptors";
            String constantName = fieldElementConstantName(field.ipParamName);
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

    private void postConstructMethod(TypeInfo typeInfo, ClassModel.Builder classModel, TypeName serviceType) {
        // postConstruct()
        lifecycleMethod(typeInfo, InjectCodegenTypes.INJECTION_POST_CONSTRUCT).ifPresent(method -> {
            classModel.addMethod(postConstruct -> postConstruct.name("postConstruct")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addParameter(instance -> instance.type(serviceType)
                            .name("instance"))
                    .addContentLine("instance." + method.elementName() + "();"));
        });
    }

    private void preDestroyMethod(TypeInfo typeInfo, ClassModel.Builder classModel, TypeName serviceType) {
        // preDestroy
        lifecycleMethod(typeInfo, InjectCodegenTypes.INJECTION_PRE_DESTROY).ifPresent(method -> {
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
        if (qualifiers.isEmpty() && !superType.hasSupertype()) {
            return;
        }
        // List<Qualifier> qualifiers()
        classModel.addMethod(qualifiersMethod -> qualifiersMethod.name("qualifiers")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(SET_OF_QUALIFIERS)
                .addContentLine("return QUALIFIERS;"));
    }

    private void scopesMethod(ClassModel.Builder classModel) {
        // TypeName scope()
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
        // int runLevel()
        Optional<Integer> runLevel = runLevel(typeInfo);

        if (!hasSuperType && runLevel.isEmpty()) {
            return;
        }
        int usedRunLevel = runLevel.orElse(100); // normal run level
        if (!hasSuperType && usedRunLevel == 100) {
            return;
        }

        classModel.addMethod(runLevelMethod -> runLevelMethod.name("runLevel")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.PRIMITIVE_INT)
                .addContentLine("return " + usedRunLevel + ";"));
    }

    private Optional<Integer> runLevel(TypeInfo typeInfo) {
        return typeInfo.findAnnotation(InjectCodegenTypes.RUN_LEVEL)
                .flatMap(Annotation::intValue);
    }

    private void notifyObservers(RoundContext roundContext, Collection<TypeInfo> descriptorsRequired) {
        // we have correct classloader set in current thread context
        if (!observers.isEmpty()) {
            Set<TypedElementInfo> elements = descriptorsRequired.stream()
                    .flatMap(it -> it.elementInfo().stream())
                    .collect(Collectors.toSet());
            observers.forEach(it -> it.onProcessingEvent(roundContext, elements));
        }
    }

    private InjectionCodegenContext.Assignment translateParameter(TypeName typeName, String constantName) {
        return ctx.assignment(typeName, "ctx__helidonInject.param(" + constantName + ")");
    }

    private record GenericTypeDeclaration(String constantName,
                                          TypeName typeName) {
    }

    private record MethodDefinition(TypeName declaringType, AccessModifier access,
                                    String methodId,
                                    String constantName,
                                    String methodName,
                                    boolean overrides,
                                    List<ParamDefinition> params,
                                    boolean isInjectionPoint,
                                    boolean isFinal) {
        public String invokeName() {
            return "invoke" + capitalize(methodId());
        }
    }

    /**
     * @param owningElement      if this is an argument, the constructor or method this belongs to
     * @param methodConstantName in case this param belongs to a method, the constant of the method, otherwise null
     * @param elementInfo        element info of field or argument
     * @param constantName       name of the constant that holds the IpId of this parameter
     * @param declaredType       type of the field as required by the injection point
     * @param translatedType     type used for injection into this param (e.g. using Supplier where #type uses Provider),
     *                           same instance as #type if not translated
     * @param assignmentHandler  to provide source for assigning the result from injection context
     * @param kind               kind of the owning element (field, method, constructor)
     * @param ipName             name of the field or method
     * @param ipParamName        name of the field or parameter
     * @param fieldId            unique identification of this param within the type (field name, methodid + param name)
     * @param isStatic           whether the field is static
     * @param annotations        annotations on this injection param
     * @param qualifiers         qualifiers of this injection param
     * @param contract           contract expected for this injection param (ignoring list, supplier, optional etc.)
     * @param access             access modifier of this param
     * @param methodId           id of the method (unique identification of method within the class)
     */
    private record ParamDefinition(TypedElementInfo owningElement,
                                   String methodConstantName,
                                   TypedElementInfo elementInfo,
                                   String constantName,
                                   TypeName declaredType,
                                   TypeName translatedType,
                                   Consumer<ContentBuilder<?>> assignmentHandler,
                                   ElementKind kind,
                                   String ipName,
                                   String ipParamName,
                                   String fieldId,
                                   boolean isStatic,
                                   List<Annotation> annotations,
                                   Set<Annotation> qualifiers,
                                   TypeName contract,
                                   AccessModifier access,
                                   String methodId) {

    }

    private record SuperType(boolean hasSupertype,
                             TypeName superDescriptorType,
                             TypeInfo superType) {
        static SuperType noSuperType() {
            return new SuperType(false, null, null);
        }
    }
}
