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

package io.helidon.inject.tools;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.processor.AnnotationFactory;
import io.helidon.common.processor.CopyrightHandler;
import io.helidon.common.processor.TypeFactory;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.ElementInfo;
import io.helidon.inject.api.InterceptedTrigger;
import io.helidon.inject.api.Resettable;
import io.helidon.inject.api.ServiceInfoBasics;
import io.helidon.inject.tools.spi.InterceptorCreator;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodTypeSignature;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.inject.api.ServiceInfoBasics.DEFAULT_INJECT_WEIGHT;
import static io.helidon.inject.tools.TypeTools.createAnnotationListFromAnnotations;
import static io.helidon.inject.tools.TypeTools.createAnnotationSet;
import static io.helidon.inject.tools.TypeTools.createMethodElementInfo;
import static io.helidon.inject.tools.TypeTools.gatherAllAnnotationsUsedOnPublicNonStaticMethods;
import static io.helidon.inject.tools.TypeTools.toKind;

/**
 * The default {@link InterceptorCreator} provider in use.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Singleton
@Weight(DEFAULT_INJECT_WEIGHT)
@SuppressWarnings("unchecked")
public class InterceptorCreatorDefault extends AbstractCreator implements InterceptorCreator, Resettable {
    private static final LazyValue<ScanResult> SCAN = LazyValue.create(ReflectionHandler.INSTANCE::scan);

    private static final String INTERCEPTOR_NAME_SUFFIX = "Interceptor";
    private static final String INNER_INTERCEPTOR_CLASS_NAME = "$$" + NAME_PREFIX + INTERCEPTOR_NAME_SUFFIX;
    private static final String NO_ARG_INTERCEPTOR_HBS = "no-arg-based-interceptor.hbs";
    private static final String INTERFACES_INTERCEPTOR_HBS = "interface-based-interceptor.hbs";
    private static final double INTERCEPTOR_PRIORITY_DELTA = 0.001;
    private static final String CTOR_ALIAS = "ctor";
    /**
     * the interceptor meta-annotation.
     */
    private static final Annotation TRIGGER = Annotation.create(InterceptedTrigger.class);
    private static final TypeName TRIGGER_TYPE = TRIGGER.typeName();
    private static final Set<TypeName> ALLOW_LIST = new LinkedHashSet<>();

    private final Set<TypeName> allowListedAnnoTypeNames = new LinkedHashSet<>();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public InterceptorCreatorDefault() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    @Override
    public boolean reset(boolean deep) {
        allowListedAnnoTypeNames.clear();
        return true;
    }

    /**
     * Sets the allow-listed annotation types triggering interception creation for the default interceptor creator.
     *
     * @param allowListedAnnotationTypes the allow-listed annotation types
     * @return this instance
     */
    public InterceptorCreatorDefault allowListedAnnotationTypes(Set<TypeName> allowListedAnnotationTypes) {
        this.allowListedAnnoTypeNames.addAll(allowListedAnnotationTypes);
        return this;
    }

    @Override
    public Set<TypeName> allowListedAnnotationTypes() {
        return allowListedAnnoTypeNames;
    }

    /**
     * Abstract base for handling the resolution of annotation types by name.
     */
    abstract static class AnnotationTypeNameResolver {
        /**
         * Determine the all the annotations belonging to a particular annotation type name.
         *
         * @param annoTypeName the annotation type name
         * @return the list of (meta) annotations for the given annotation
         */
        abstract Collection<Annotation> resolve(TypeName annoTypeName);
    }

    static class ProcessorResolver extends AnnotationTypeNameResolver {
        private final Elements elements;

        ProcessorResolver(Elements elements) {
            this.elements = elements;
        }

        @Override
        public Collection<Annotation> resolve(TypeName annoTypeName) {
           TypeElement typeElement = elements.getTypeElement(annoTypeName.fqName());

           if (typeElement == null) {
               throw new ToolsException("Unable to resolve: " + annoTypeName);
           }

           List<? extends AnnotationMirror> annotations = typeElement.getAnnotationMirrors();
           return annotations.stream()
                            .map(it -> AnnotationFactory.createAnnotation(it, elements))
                            .collect(Collectors.toSet());
        }
    }

    static class ReflectionResolver extends AnnotationTypeNameResolver {
        private final ScanResult scan;

        ReflectionResolver(ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public Collection<Annotation> resolve(TypeName annoTypeName) {
            ClassInfo classInfo = scan.getClassInfo(annoTypeName.fqName());
            if (classInfo == null) {
                try {
                    Class<? extends Annotation> annotationType =
                            (Class<? extends Annotation>) Class.forName(annoTypeName.fqName());
                    return createAnnotationListFromAnnotations(annotationType.getAnnotations());
                } catch (ClassNotFoundException e) {
                    throw new ToolsException(e.getMessage(), e);
                }
            }
            return createAnnotationSet(classInfo);
        }
    }

    /**
     * Filter will apply the appropriate strategy determine which annotation types qualify as triggers for interception.
     */
    abstract static class TriggerFilter {
        /**
         * The creator.
         */
        private final InterceptorCreator creator;

        /**
         * The way to convert a string to the annotation type.
         */
        private final AnnotationTypeNameResolver resolver;

        protected TriggerFilter() {
            this.creator = null;
            this.resolver = null;
        }

        protected TriggerFilter(InterceptorCreator creator) {
            this.creator = Objects.requireNonNull(creator);
            this.resolver = null;
        }

        protected TriggerFilter(InterceptorCreator creator,
                                AnnotationTypeNameResolver resolver) {
            this.creator = Objects.requireNonNull(creator);
            this.resolver = Objects.requireNonNull(resolver);
        }

        Optional<InterceptorCreator> creator() {
            return Optional.ofNullable(creator);
        }

        Optional<AnnotationTypeNameResolver> resolver() {
            return Optional.ofNullable(resolver);
        }

        /**
         * Returns true if the annotation qualifies/triggers interceptor creation.
         *
         * @param annotationTypeName the annotation type name
         * @return true if the annotation qualifies/triggers interceptor creation
         */
        boolean isQualifyingTrigger(TypeName annotationTypeName) {
            return (creator != null) && creator.isAllowListed(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#EXPLICIT}.
     */
    private static class ExplicitStrategy extends TriggerFilter {
        protected ExplicitStrategy(InterceptorCreator creator,
                                   AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(TypeName annotationTypeName) {
            return resolver().map(it -> it.resolve(annotationTypeName).contains(TRIGGER))
                    .orElse(false)
                    || TRIGGER_TYPE.equals(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#ALL_RUNTIME}.
     */
    private static class AllRuntimeStrategy extends TriggerFilter {
        protected static final Annotation RUNTIME = Annotation.create(Retention.class, RetentionPolicy.RUNTIME.name());
        protected static final Annotation CLASS = Annotation.create(Retention.class, RetentionPolicy.CLASS.name());

        protected AllRuntimeStrategy(InterceptorCreator creator,
                                     AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(TypeName annotationTypeName) {
            Objects.requireNonNull(annotationTypeName);
            if (ALLOW_LIST.contains(annotationTypeName)) {
                return true;
            }
            return resolver().map(resolver -> resolver.resolve(annotationTypeName).contains(RUNTIME)
                            || resolver.resolve(annotationTypeName).contains(CLASS))
                    .orElse(false);
        }
    }

    /**
     * Enforces {@link Strategy#ALLOW_LISTED}.
     */
    private static class AllowListedStrategy extends TriggerFilter {
        private final Set<TypeName> allowListed;

        protected AllowListedStrategy(InterceptorCreator creator) {
            super(creator);
            this.allowListed = Objects.requireNonNull(creator.allowListedAnnotationTypes());
        }

        @Override
        public boolean isQualifyingTrigger(TypeName annotationTypeName) {
            Objects.requireNonNull(annotationTypeName);
            return allowListed.contains(annotationTypeName) || ALLOW_LIST.contains(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#CUSTOM}.
     */
    private static class CustomStrategy extends TriggerFilter {
        protected CustomStrategy(InterceptorCreator creator) {
            super(creator);
        }

        @Override
        public boolean isQualifyingTrigger(TypeName annotationTypeName) {
            Objects.requireNonNull(annotationTypeName);
            if (ALLOW_LIST.contains(annotationTypeName)) {
                return true;
            }

            return creator().map(it -> it.isAllowListed(annotationTypeName))
                    .orElse(false);
        }
    }

    /**
     * Enforces {@link Strategy#NONE}.
     */
    private static class NoneStrategy extends TriggerFilter {
    }

    /**
     * Enforces {@link Strategy#BLENDED}.
     */
    private static class BlendedStrategy extends ExplicitStrategy {
        private final CustomStrategy customStrategy;

        protected BlendedStrategy(InterceptorCreator creator,
                                  AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
            this.customStrategy = new CustomStrategy(creator);
        }

        @Override
        public boolean isQualifyingTrigger(TypeName annotationTypeName) {
            if (super.isQualifyingTrigger(annotationTypeName)) {
                return true;
            }
            return customStrategy.isQualifyingTrigger(annotationTypeName);
        }
    }

    /**
     * Returns the {@link TriggerFilter} appropriate for the given {@link InterceptorCreator}.
     *
     * @param creator   the interceptor creator
     * @param resolver  the resolver, used in cases where the implementation needs to research more about a given annotation type
     * @return the trigger filter instance
     */
    private static TriggerFilter createTriggerFilter(InterceptorCreator creator,
                                                     AnnotationTypeNameResolver resolver) {
        Strategy strategy = creator.strategy();
        if (Strategy.EXPLICIT == strategy) {
            return new ExplicitStrategy(creator, resolver);
        } else if (Strategy.ALL_RUNTIME == strategy) {
            return new AllRuntimeStrategy(creator, resolver);
        }  else if (Strategy.ALLOW_LISTED == strategy) {
            return new AllowListedStrategy(creator);
        } else if (Strategy.CUSTOM == strategy) {
            return new CustomStrategy(creator);
        } else if (Strategy.NONE == strategy) {
            return new NoneStrategy();
        } else if (Strategy.BLENDED == strategy || strategy == null) {
            return new BlendedStrategy(creator, resolver);
        } else {
            throw new ToolsException("Unknown strategy: " + strategy);
        }
    }

    /**
     * Able to abstractly handle processing in annotation processing mode, or in reflection mode.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    abstract static class AbstractInterceptorProcessor implements InterceptorProcessor {
        /**
         * The service being intercepted/processed.
         */
        final ServiceInfoBasics interceptedService;

        /**
         * The "real" / delegate creator.
         */
        final InterceptorCreator creator;

        private final AnnotationTypeNameResolver resolver;
        private final TriggerFilter triggerFilter;
        private final System.Logger logger;

        protected AbstractInterceptorProcessor(ServiceInfoBasics interceptedService,
                                               InterceptorCreator realCreator,
                                               AnnotationTypeNameResolver resolver,
                                               System.Logger logger) {
            this.creator = realCreator;
            this.interceptedService = interceptedService;
            this.resolver = resolver;
            this.triggerFilter = createTriggerFilter(realCreator, resolver);
            this.logger = logger;
        }

        TypeName serviceTypeName() {
            return interceptedService.serviceTypeName();
        }

        /**
         * @return the trigger filter in use
         */
        TriggerFilter triggerFilter() {
            return triggerFilter;
        }

        /**
         * The set of annotation types that trigger interception.
         *
         * @return the set of annotation types that are trigger interception
         */
        @Override
        public Set<TypeName> allAnnotationTypeTriggers() {
            Set<TypeName> allAnnotations = getAllAnnotations();
            if (allAnnotations.isEmpty()) {
                return Set.of();
            }

            TriggerFilter triggerFilter = triggerFilter();
            // the below section uses a linked has set to make sure the order is preserved
            // so we can run tests that depend on that order (such as actual generated code)
            return allAnnotations.stream()
                    .filter(triggerFilter::isQualifyingTrigger)
                    .filter(Predicate.not(TRIGGER_TYPE::equals))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Optional<InterceptionPlan> createInterceptorPlan(Set<TypeName> interceptorAnnotationTriggers) {
            boolean hasNoArgConstructor = hasNoArgConstructor();
            Set<TypeName> interfaces = interfaces();

            // the code generation will extend the class when there is a zero/no-arg constructor, but if not then we will use a
            // different generated source altogether that will only allow the interception of the interfaces.
            // note also that the service type, or the method has to have the interceptor trigger annotation to qualify.
            if (!hasNoArgConstructor && interfaces.isEmpty()) {
                String msg = "There must either be a no-arg constructor, or otherwise the target service must implement at least "
                        + "one interface type. Note that when a no-arg constructor is available then your entire type, including "
                        + "all of its public methods are interceptable. If, however, there is no applicable no-arg constructor "
                        + "available then only the interface-based methods of the target service type are interceptable for: "
                        + serviceTypeName();
                ToolsException te = new ToolsException(msg);
                logger.log(System.Logger.Level.ERROR, "Unable to create an interceptor plan for: " + serviceTypeName(), te);
                throw te;
            }

            List<InterceptedElement> interceptedElements = (hasNoArgConstructor)
                    ? getInterceptedElements(interceptorAnnotationTriggers)
                    : getInterceptedElements(interceptorAnnotationTriggers, interfaces);
            if (interceptedElements.isEmpty()) {
                ToolsException te = new ToolsException("No methods available to intercept for: " + serviceTypeName());
                logger.log(System.Logger.Level.ERROR, "Unable to create an interceptor plan for: " + serviceTypeName(), te);
                throw te;
            }

            Set<Annotation> serviceLevelAnnotations = getServiceLevelAnnotations();
            InterceptionPlan plan = InterceptionPlan.builder()
                    .interceptedService(interceptedService)
                    .serviceLevelAnnotations(serviceLevelAnnotations)
                    .annotationTriggerTypeNames(interceptorAnnotationTriggers)
                    .interceptedElements(interceptedElements)
                    .hasNoArgConstructor(hasNoArgConstructor)
                    .interfaces(interfaces)
                    .build();
            return Optional.of(plan);
        }

        @Override
        public String toString() {
            return serviceTypeName().resolvedName();
        }

        /**
         * @return the cumulative annotations referenced by this type
         */
        abstract Set<TypeName> getAllAnnotations();

        /**
         * @return only the service level annotations referenced by this type
         */
        abstract Set<Annotation> getServiceLevelAnnotations();

        /**
         * @return true if there is a no-arg constructor present
         */
        abstract boolean hasNoArgConstructor();

        /**
         * @return the set of interfaces implemented
         */
        abstract Set<TypeName> interfaces();

        /**
         * @return all public methods
         */
        abstract List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers);

        /**
         * @return all public methods for only the given interfaces
         */
        abstract List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers,
                                                                 Set<TypeName> interfaces);

        boolean containsAny(Set<Annotation> annotations,
                            Set<TypeName> annotationTypeNames) {
            for (Annotation annotation : annotations) {
                if (annotationTypeNames.contains(annotation.typeName())) {
                    return true;
                }
            }
            return false;
        }

        boolean isProcessed(ElementKind kind,
                            Set<Modifier> modifiers,
                            Boolean isPrivate,
                            Boolean isStatic) {
            assert (ElementKind.CONSTRUCTOR == kind || ElementKind.METHOD == kind)
                    : kind + " in:" + serviceTypeName();

            if (modifiers != null) {
                if (modifiers.contains(Modifier.STATIC)) {
                    return false;
                }

                if (modifiers.contains(Modifier.PRIVATE)) {
                    return false;
                }
            } else {
                if (isPrivate) {
                    return false;
                }

                if (isStatic) {
                    return false;
                }
            }

            return true;
        }
    }


    private static class ProcessorBased extends AbstractInterceptorProcessor {
        private final TypeElement serviceTypeElement;
        private final ProcessingEnvironment processEnv;

        ProcessorBased(ServiceInfoBasics interceptedService,
                       InterceptorCreator realCreator,
                       ProcessingEnvironment processEnv,
                       System.Logger logger) {
            super(interceptedService, realCreator, createResolverFromProcessor(processEnv), logger);

            TypeElement typeElement = processEnv.getElementUtils().getTypeElement(serviceTypeName().resolvedName());
            if (typeElement == null) {
                throw new ToolsException("Failed to get type element for " + serviceTypeName());
            }

            this.serviceTypeElement = typeElement;
            this.processEnv = processEnv;
        }

        @Override
        Set<TypeName> getAllAnnotations() {
            Set<Annotation> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(serviceTypeElement, processEnv);
            return set.stream()
                    .map(a -> a.typeName())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        Set<Annotation> getServiceLevelAnnotations() {
            return AnnotationFactory.createAnnotations(serviceTypeElement, processEnv.getElementUtils());
        }

        @Override
        boolean hasNoArgConstructor() {
            return serviceTypeElement.getEnclosedElements()
                    .stream()
                    .filter(it -> it.getKind().equals(ElementKind.CONSTRUCTOR))
                    .map(ExecutableElement.class::cast)
                    .anyMatch(it -> it.getParameters().isEmpty());
        }

        @Override
        Set<TypeName> interfaces() {
            return gatherInterfaces(new LinkedHashSet<>(), serviceTypeElement);
        }

        Set<TypeName> gatherInterfaces(Set<TypeName> result,
                                       TypeElement typeElement) {
            if (typeElement == null) {
                return result;
            }

            typeElement.getInterfaces().forEach(tm -> {
                result.add(TypeFactory.createTypeName(tm).orElseThrow());
                gatherInterfaces(result, TypeTools.toTypeElement(tm).orElse(null));
            });

            TypeElement te = (TypeElement) processEnv.getTypeUtils().asElement(typeElement.getSuperclass());
            return gatherInterfaces(result, te);
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<Annotation> serviceLevelAnnos = getServiceLevelAnnotations();

            // find the injectable constructor, falling back to the no-arg constructor
            gatherInjectableConstructor(result, serviceLevelAnnos, interceptorAnnotationTriggers);

            // gather all of the public methods as well as the no-arg constructor
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(e -> isProcessed(toKind(e), /*e.getParameters().size(),*/ e.getModifiers(), null, null))
                    .forEach(ee -> result.add(
                            create(processEnv, ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers,
                                                        Set<TypeName> interfaces) {
            assert (!interfaces.isEmpty());
            List<InterceptedElement> result = new ArrayList<>();
            Set<Annotation> serviceLevelAnnos = getServiceLevelAnnotations();

            // find the injectable constructor, falling back to the no-arg constructor
            gatherInjectableConstructor(result, serviceLevelAnnos, interceptorAnnotationTriggers);

            // gather all of the methods that map to one of our interfaces
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(e -> isProcessed(toKind(e), e.getModifiers(), null, null))
                    .filter(e -> mapsToAnInterface(e, interfaces))
                    .forEach(ee -> result.add(
                            create(processEnv, ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        void gatherInjectableConstructor(List<InterceptedElement> result,
                                         Set<Annotation> serviceLevelAnnos,
                                         Set<TypeName> interceptorAnnotationTriggers) {
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .filter(ee -> !ee.getModifiers().contains(Modifier.PRIVATE))
                    .filter(ee -> {
                        boolean hasInject = ee.getAnnotationMirrors().stream()
                                .map(it -> AnnotationFactory.createAnnotation(it, processEnv.getElementUtils()))
                                .map(Annotation::typeName)
                                .map(TypeName::name)
                                .anyMatch(anno -> TypeNames.JAKARTA_INJECT.equals(anno) || TypeNames.JAVAX_INJECT.equals(anno));
                        return hasInject;
                    })
                    .forEach(ee -> result.add(
                            create(processEnv, ee, serviceLevelAnnos, interceptorAnnotationTriggers)));

            if (result.size() > 1) {
                throw new ToolsException("There can be at most one injectable constructor for: " + serviceTypeName());
            }

            if (result.size() == 1) {
                return;
            }

            // find the no-arg constructor as the fallback
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .filter(ee -> !ee.getModifiers().contains(Modifier.PRIVATE))
                    .filter(ee -> ee.getParameters().isEmpty())
                    .forEach(ee -> result.add(
                            create(processEnv, ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            if (result.isEmpty()) {
                throw new ToolsException("There should either be a no-arg or injectable constructor for: " + serviceTypeName());
            }
        }

        /**
         * @return returns true if the given method is implemented in one of the provided interface type names
         */
        boolean mapsToAnInterface(ExecutableElement ee,
                                  Set<TypeName> interfaces) {
            for (TypeName typeName : interfaces) {
                TypeElement te = processEnv.getElementUtils().getTypeElement(typeName.name());
                Objects.requireNonNull(te, typeName.toString());
                boolean hasIt = te.getEnclosedElements().stream()
                        // _note to self_: there needs to be a better way than this!
                        .anyMatch(e -> e.toString().equals(ee.toString()));
                if (hasIt) {
                    return true;
                }
            }
            return false;
        }

        private InterceptedElement create(ProcessingEnvironment processingEnv,
                                          ExecutableElement ee,
                                          Set<Annotation> serviceLevelAnnos,
                                          Set<TypeName> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(processingEnv, serviceTypeElement, ee, serviceLevelAnnos);
            Set<TypeName> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations()
                                                 .stream()
                                                 .map(a -> a.typeName())
                                                 .collect(Collectors.toSet()));
            return InterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
        }
    }


    private static class ReflectionBased extends AbstractInterceptorProcessor {
        private final ClassInfo classInfo;

        ReflectionBased(ServiceInfoBasics interceptedService,
                        InterceptorCreator realCreator,
                        ClassInfo classInfo,
                        System.Logger logger) {
            super(/*serviceTypeName,*/ interceptedService, realCreator, createResolverFromReflection(), logger);
            this.classInfo = classInfo;
        }

        @Override
        Set<TypeName> getAllAnnotations() {
            Set<Annotation> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(classInfo);
            return set.stream()
                    .map(a -> a.typeName())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        Set<Annotation> getServiceLevelAnnotations() {
            return createAnnotationSet(classInfo);
        }

        @Override
        boolean hasNoArgConstructor() {
            return classInfo.getConstructorInfo().stream()
                    .filter(mi -> !mi.isPrivate())
                    .anyMatch(mi -> mi.getParameterInfo().length == 0);
        }

        @Override
        Set<TypeName> interfaces() {
            return gatherInterfaces(new LinkedHashSet<>(), classInfo);
        }

        Set<TypeName> gatherInterfaces(Set<TypeName> result,
                                       ClassInfo classInfo) {
            if (classInfo == null) {
                return result;
            }

            classInfo.getInterfaces().forEach(tm -> {
                result.add(TypeTools.createTypeNameFromClassInfo(tm));
                gatherInterfaces(result, tm);
            });

            return gatherInterfaces(result, classInfo.getSuperclass());
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<Annotation> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter(m -> isProcessed(toKind(m), /*m.getParameterInfo().length,*/ null, m.isPrivate(), m.isStatic()))
                    .filter(m -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                            || containsAny(createAnnotationSet(m.getAnnotationInfo()), interceptorAnnotationTriggers))
                    .forEach(mi -> result.add(
                            create(mi, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<TypeName> interceptorAnnotationTriggers,
                                                        Set<TypeName> interfaces) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<Annotation> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter(m -> isProcessed(toKind(m), null, m.isPrivate(), m.isStatic()))
                    .filter(m -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                            || containsAny(createAnnotationSet(m.getAnnotationInfo()), interceptorAnnotationTriggers))
                    .filter(m -> mapsToAnInterface(m, interfaces))
                    .forEach(mi -> result.add(
                            create(mi, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        boolean mapsToAnInterface(MethodInfo targetMethodInfo,
                                  Set<TypeName> interfaces) {
            MethodTypeSignature sig = targetMethodInfo.getTypeSignatureOrTypeDescriptor();
            for (TypeName typeName : interfaces) {
                ClassInfo ci = toClassInfo(typeName, classInfo);
                Objects.requireNonNull(ci, typeName.toString());
                for (MethodInfo mi : ci.getDeclaredMethodInfo(targetMethodInfo.getName())) {
                    if (mi.equals(targetMethodInfo) || sig.equals(mi.getTypeSignatureOrTypeDescriptor())) {
                        return true;
                    }
                }
            }
            return false;
        }

        ClassInfo toClassInfo(TypeName typeName,
                              ClassInfo child) {
            for (ClassInfo ci : child.getInterfaces()) {
                if (TypeTools.createTypeNameFromClassInfo(ci).equals(typeName)) {
                    return ci;
                }
            }

            for (ClassInfo ci : child.getSuperclasses()) {
                ClassInfo foundIt = toClassInfo(typeName, ci);
                if (foundIt != null) {
                    return foundIt;
                }
            }

            return null;
        }

        private InterceptedElement create(MethodInfo mi,
                                          Set<Annotation> serviceLevelAnnos,
                                          Set<TypeName> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(mi, serviceLevelAnnos);
            Set<TypeName> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations()
                                                 .stream()
                                                 .map(a -> a.typeName())
                                                 .collect(Collectors.toSet()));
            return InterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
        }
    }

    /**
     * Create an annotation resolver based on annotation processing.
     *
     * @param processEnv the processing env
     * @return the {@link InterceptorCreatorDefault.AnnotationTypeNameResolver} to use
     */
    static AnnotationTypeNameResolver createResolverFromProcessor(ProcessingEnvironment processEnv) {
        return new ProcessorResolver(processEnv.getElementUtils());
    }

    /**
     * Create an annotation resolver based on reflective processing.
     *
     * @return the {@link InterceptorCreatorDefault.AnnotationTypeNameResolver} to use
     */
    static AnnotationTypeNameResolver createResolverFromReflection() {
        return new ReflectionResolver(SCAN.get());
    }

    @Override
    public AbstractInterceptorProcessor createInterceptorProcessor(ServiceInfoBasics interceptedService,
                                                                   InterceptorCreator delegateCreator,
                                                                   ProcessingEnvironment processEnv) {
        Objects.requireNonNull(interceptedService);
        Objects.requireNonNull(delegateCreator);
        Objects.requireNonNull(processEnv);

        Options.init(processEnv);
        ALLOW_LIST.addAll(Options.getOptionStringList(Options.TAG_ALLOW_LISTED_INTERCEPTOR_ANNOTATIONS)
                                  .stream()
                                  .map(TypeName::create)
                                  .toList());

        return new ProcessorBased(interceptedService,
                                  delegateCreator,
                                  processEnv,
                                  logger());
    }


    @Override
    public InterceptorProcessor createInterceptorProcessor(ServiceInfoBasics interceptedService,
                                                           InterceptorCreator delegateCreator) {
        Objects.requireNonNull(interceptedService);
        Objects.requireNonNull(delegateCreator);

        String resolvedType = interceptedService.serviceTypeName().resolvedName();
        return new ReflectionBased(Objects.requireNonNull(interceptedService),
                                   Objects.requireNonNull(delegateCreator),
                                   Objects.requireNonNull(SCAN.get().getClassInfo(resolvedType)),
                                   logger());
    }

    /**
     * Creates the interceptor source code type name given its plan.
     *
     * @param plan the plan
     * @return the interceptor type name
     */
    static TypeName createInterceptorSourceTypeName(InterceptionPlan plan) {
        String parent = plan.interceptedService().serviceTypeName().resolvedName();
        return toInterceptorTypeName(parent);
    }

    /**
     * Creates the source code associated with an interception plan.
     *
     * @param plan                the plan
     * @return the java source code body
     */
    String createInterceptorSourceBody(InterceptionPlan plan) {
        TypeName generatorType = TypeName.create(getClass());
        TypeName triggerType = TypeName.create(plan.interceptedService().serviceTypeName().resolvedName());

        String parent = plan.interceptedService().serviceTypeName().resolvedName();
        TypeName interceptorTypeName = toInterceptorTypeName(parent);
        Map<String, Object> subst = new HashMap<>();
        subst.put("packageName", interceptorTypeName.packageName());
        subst.put("className", interceptorTypeName.className());
        subst.put("parent", parent);
        subst.put("header", CopyrightHandler.copyright(generatorType,
                                                       triggerType,
                                                       interceptorTypeName));
        subst.put("generatedanno", templateHelper().generatedStickerFor(generatorType,
                                                                        triggerType,
                                                                        interceptorTypeName));
        subst.put("weight", interceptorWeight(plan.interceptedService().declaredWeight()));
        subst.put("interceptedmethoddecls", toInterceptedMethodDecls(plan));
        subst.put("interfaces", toInterfacesDecl(plan));
        subst.put("interceptedelements", IdAndToString
                .toList(plan.interceptedElements(), InterceptorCreatorDefault::toBody).stream()
                .filter(it -> !it.getId().equals(CTOR_ALIAS))
                .collect(Collectors.toList()));
        subst.put("ctorinterceptedelements", IdAndToString
                .toList(plan.interceptedElements(), InterceptorCreatorDefault::toBody).stream()
                .filter(it -> it.getId().equalsIgnoreCase(CTOR_ALIAS))
                .toList());
        subst.put("annotationtriggertypenames", IdAndToString
                .toList(plan.annotationTriggerTypeNames(),
                        typeName -> new IdAndToString(typeName.fqName().replace(".", "_"),
                                                      typeName.fqName().replace('$', '.'))));
        subst.put("servicelevelannotations", IdAndToString
                .toList(plan.serviceLevelAnnotations(), InterceptorCreatorDefault::toDecl));
        String template = templateHelper().safeLoadTemplate(
                plan.hasNoArgConstructor() ? NO_ARG_INTERCEPTOR_HBS : INTERFACES_INTERCEPTOR_HBS);
        return templateHelper().applySubstitutions(template, subst, true).trim();
    }

    private static List<IdAndToString> toInterceptedMethodDecls(InterceptionPlan plan) {
        List<IdAndToString> result = new ArrayList<>();
        for (InterceptedElement element : plan.interceptedElements()) {
            IdAndToString methodTypedElement = toDecl(element);
            result.add(methodTypedElement);

            if (element.elementInfo().elementKind() == io.helidon.inject.api.ElementKind.CONSTRUCTOR) {
                continue;
            }

            for (ElementInfo param : element.elementInfo().parameterInfo()) {
                IdAndToString paramTypedElement = new IdAndToString(element.elementInfo().elementName()
                                                                            + "__" + param.elementName(),
                                                                    typeNameElementNameAnnotations(param, false));
                result.add(paramTypedElement);
            }
        }
        return result;
    }

    private static String toInterfacesDecl(InterceptionPlan plan) {
        return plan.interfaces().stream()
                .map(TypeName::name)
                .collect(Collectors.joining(", "));
    }

    private static IdAndToString toDecl(InterceptedElement method) {
        MethodElementInfo mi = method.elementInfo();
        boolean constructor = mi.elementKind() == io.helidon.inject.api.ElementKind.CONSTRUCTOR;
        String name = constructor ? CTOR_ALIAS : mi.elementName();
        String builder = typeNameElementNameAnnotations(mi, constructor);
        return new IdAndToString(name, builder);
    }

    private static String typeNameElementNameAnnotations(ElementInfo ei, boolean isConstructor) {
        StringBuilder builder = new StringBuilder(".typeName(create(" + ei.elementTypeName() + ".class))")
                .append("\n\t\t\t.elementTypeKind(TypeValues.");
        if (isConstructor) {
            builder.append("KIND_CONSTRUCTOR");
        } else {
            builder.append("KIND_METHOD");
        }
        builder.append(")\n\t\t\t.elementName(").append(CodeGenUtils.elementNameRef(ei.elementName())).append(")");
        TreeSet<Annotation> sortedAnnotations = new TreeSet<>(ei.annotations());
        for (Annotation anno : sortedAnnotations) {
            builder.append("\n\t\t\t.addAnnotation(").append(toDecl(anno)).append(")");
        }
        return builder.toString();
    }

    private static IdAndToString toDecl(ElementInfo elementInfo) {
        String name = elementInfo.elementName();
        return new IdAndToString(name, elementInfo.elementTypeName() + " " + name);
    }

    private static IdAndToString toDecl(Annotation anno) {
        StringBuilder builder = new StringBuilder("Annotation.create(" + anno.typeName() + ".class");
        Map<String, Object> map = anno.values();
        Object val = anno.objectValue().orElse(null);
        if (map != null && !map.isEmpty()) {
            builder.append(", Map.of(");
            int count = 0;
            TreeMap<String, Object> sortedMap = new TreeMap<>(map);
            for (Map.Entry<String, Object> e : sortedMap.entrySet()) {
                if (count++ > 0) {
                    builder.append(", ");
                }
                builder.append("\"")
                        .append(e.getKey())
                        .append("\", ")
                        .append(mapValueInSources(e.getValue()));
            }
            builder.append(")");
        } else if (val != null) {
            builder.append(", \"")
                    .append(mapValueInSources(val))
                    .append("\"");
        }
        builder.append(")");
        return new IdAndToString(anno.typeName().name(), builder);
    }

    private static String mapValueInSources(Object value) {
        if (value instanceof String str) {
            return "\"" + str + "\"";
        }
        if (value instanceof Annotation ann) {
            throw new IllegalArgumentException("Cannot process nested annotation in a sample map: " + ann);
        }
        if (value instanceof List<?> list) {
            return "java.util.List.of(" + list.stream()
                    .map(InterceptorCreatorDefault::mapValueInSources)
                    .collect(Collectors.joining(", "))
                    + ")";
        }
        // for primitive types, just use them
        return String.valueOf(value);
    }

    @SuppressWarnings("checkstyle:OperatorWrap")
    private static InterceptedMethodCodeGen toBody(InterceptedElement method) {
        MethodElementInfo mi = method.elementInfo();
        String name = (mi.elementKind() == io.helidon.inject.api.ElementKind.CONSTRUCTOR) ? CTOR_ALIAS : mi.elementName();
        StringBuilder builder = new StringBuilder();
        builder.append("public ").append(mi.elementTypeName()).append(" ").append(mi.elementName()).append("(");
        String args = mi.parameterInfo().stream()
                .map(ElementInfo::elementName)
                .collect(Collectors.joining(", "));
        String argDecls = "";
        String objArrayArgs = "";
        String typedElementArgs = "";
        String untypedElementArgs = "";
        boolean hasArgs = (args.length() > 0);
        if (hasArgs) {
            argDecls = mi.parameterInfo().stream()
                    .map(InterceptorCreatorDefault::toDecl)
                    .map(IdAndToString::toString)
                    .collect(Collectors.joining(", "));
            AtomicInteger count = new AtomicInteger();
            objArrayArgs = mi.parameterInfo().stream()
                    .map(ElementInfo::elementTypeName)
                    .map(TypeName::boxed)
                    .map(typeName -> "(" + typeName + ") " + "args[" + count.getAndIncrement() + "]")
                    .collect(Collectors.joining(", "));

            typedElementArgs = mi.parameterInfo().stream()
                    .map(ei -> "__" + mi.elementName() + "__" + ei.elementName())
                    .collect(Collectors.joining(", "));

            count.set(0);
            untypedElementArgs = mi.parameterInfo().stream()
                    .map(ei -> "args[" + count.getAndIncrement() + "]")
                    .collect(Collectors.joining(", "));
        }

        boolean hasReturn = !mi.elementTypeName().equals(TypeName.create(void.class));
        builder.append(argDecls);
        builder.append(")");
        if (!mi.throwableTypeNames().isEmpty()) {
            builder.append(" throws ").append(CommonUtils.toString(mi.throwableTypeNames()));
        }
        String methodDecl = builder.toString();
        builder.append(" {\n");
        TypeName supplierType = (hasReturn) ? mi.elementTypeName().boxed() : TypeName.create(Void.class);

        String elementArgInfo = "";
        if (hasArgs) {
            elementArgInfo = ",\n\t\t\t\tList.of(" + typedElementArgs + ")";
        }
        return new InterceptedMethodCodeGen(name,
                                            methodDecl,
                                            true,
                                            hasReturn,
                                            supplierType,
                                            elementArgInfo,
                                            args,
                                            objArrayArgs,
                                            untypedElementArgs,
                                            method.interceptedTriggerTypeNames(),
                                            builder);
    }

    private static TypeName toInterceptorTypeName(String serviceTypeName) {
        TypeName typeName = TypeName.create(serviceTypeName);
        return TypeName.builder()
                .packageName(typeName.packageName())
                .className(typeName.className() + INNER_INTERCEPTOR_CLASS_NAME)
                .build();
    }

    /**
     * Assign a weight slightly higher than the service weight passed in.
     *
     * @param serviceWeight the service weight, where null defaults to {@link io.helidon.common.Weighted#DEFAULT_WEIGHT}.
     * @return the higher weighted value appropriate for interceptors
     */
    static double interceptorWeight(Optional<Double> serviceWeight) {
        double val = serviceWeight.orElse(Weighted.DEFAULT_WEIGHT);
        return val + INTERCEPTOR_PRIORITY_DELTA;
    }


    static class InterceptedMethodCodeGen extends IdAndToString {
        private final String methodDecl;
        private final boolean isOverride;
        private final boolean hasReturn;
        private final TypeName elementTypeName;
        private final String elementArgInfo;
        private final String args;
        private final String objArrayArgs;
        private final String untypedElementArgs;
        private final String interceptedTriggerTypeNames;

        InterceptedMethodCodeGen(String id,
                                 String methodDecl,
                                 boolean isOverride,
                                 boolean hasReturn,
                                 TypeName elementTypeName,
                                 String elementArgInfo,
                                 String args,
                                 String objArrayArgs,
                                 String untypedElementArgs,
                                 Collection<TypeName> interceptedTriggerTypeNames,
                                 Object toString) {
            super(id, toString);
            this.methodDecl = methodDecl;
            this.isOverride = isOverride;
            this.hasReturn = hasReturn;
            this.elementTypeName = elementTypeName;
            this.elementArgInfo = elementArgInfo;
            this.args = args;
            this.objArrayArgs = objArrayArgs;
            this.untypedElementArgs = untypedElementArgs;
            this.interceptedTriggerTypeNames = CommonUtils.toString(interceptedTriggerTypeNames,
                                                                typeName -> typeName.fqName().replace(".", "_"),
                                                                    null);
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getMethodDecl() {
            return methodDecl;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public boolean isOverride() {
            return isOverride;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public TypeName getElementTypeName() {
            return elementTypeName;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getElementArgInfo() {
            return elementArgInfo;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getArgs() {
            return args;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getObjArrayArgs() {
            return objArrayArgs;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getUntypedElementArgs() {
            return untypedElementArgs;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public boolean getHasReturn() {
            return hasReturn;
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public boolean getHasArgs() {
            return !args.isEmpty();
        }

        // note: this needs to stay as a public getXXX() method to support Mustache
        public String getInterceptedTriggerTypeNames() {
            return interceptedTriggerTypeNames;
        }
    }

}
