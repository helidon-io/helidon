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

package io.helidon.pico.tools;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.builder.processor.tools.BuilderTypeTools;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InterceptedTrigger;
import io.helidon.pico.api.Resettable;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.tools.spi.InterceptorCreator;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodTypeSignature;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.common.types.AnnotationAndValueDefault.create;
import static io.helidon.pico.api.ServiceInfoBasics.DEFAULT_PICO_WEIGHT;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueFromMirror;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueListFromAnnotations;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createMethodElementInfo;
import static io.helidon.pico.tools.TypeTools.gatherAllAnnotationsUsedOnPublicNonStaticMethods;
import static io.helidon.pico.tools.TypeTools.toKind;
import static io.helidon.pico.tools.TypeTools.toObjectTypeName;

/**
 * The default {@link io.helidon.pico.tools.spi.InterceptorCreator} provider in use.
 */
@Singleton
@Weight(DEFAULT_PICO_WEIGHT)
@SuppressWarnings("unchecked")
public class InterceptorCreatorDefault extends AbstractCreator implements InterceptorCreator, Resettable {
    private static final LazyValue<ScanResult> SCAN = LazyValue.create(ReflectionHandler.INSTANCE::scan);

    private static final String INTERCEPTOR_NAME_SUFFIX = "Interceptor";
    private static final String INNER_INTERCEPTOR_CLASS_NAME = "$$" + NAME_PREFIX + INTERCEPTOR_NAME_SUFFIX;
    private static final String NO_ARG_INTERCEPTOR_HBS = "no-arg-based-interceptor.hbs";
    private static final String INTERFACES_INTERCEPTOR_HBS = "interface-based-interceptor.hbs";
    private static final double INTERCEPTOR_PRIORITY_DELTA = 0.001;
    private static final String CTOR_ALIAS = "ctor";
    private static final Set<String> ALLOW_LIST = new LinkedHashSet<>();

    private Set<String> allowListedAnnoTypeNames;

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
        allowListedAnnoTypeNames = null;
        return true;
    }

    /**
     * Sets the allow-listed annotation types triggering interception creation for the default interceptor creator.
     *
     * @param allowListedAnnotationTypes the allow-listed annotation types
     * @return this instance
     */
    public InterceptorCreatorDefault allowListedAnnotationTypes(Set<String> allowListedAnnotationTypes) {
        this.allowListedAnnoTypeNames = allowListedAnnotationTypes;
        return this;
    }

    @Override
    public Set<String> allowListedAnnotationTypes() {
        return (allowListedAnnoTypeNames != null) ? allowListedAnnoTypeNames : Collections.emptySet();
    }

    @Override
    public Optional<InterceptionPlan> createInterceptorPlan(ServiceInfoBasics interceptedService,
                                                            ProcessingEnvironment processEnv,
                                                            Set<String> annotationTypeTriggers) {
        return createInterceptorProcessor(interceptedService, this, Optional.of(processEnv))
                .createInterceptorPlan(annotationTypeTriggers);
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
        abstract Collection<AnnotationAndValue> resolve(String annoTypeName);
    }

    static class ProcessorResolver extends AnnotationTypeNameResolver {
        private final Elements elements;

        ProcessorResolver(Elements elements) {
            this.elements = elements;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(String annoTypeName) {
           TypeElement typeElement = elements.getTypeElement(annoTypeName);
           if (typeElement == null) {
               throw new ToolsException("Unable to resolve: " + annoTypeName);
           }

           List<? extends AnnotationMirror> annotations = typeElement.getAnnotationMirrors();
           Set<AnnotationAndValue> result = annotations.stream()
                            .map(it -> createAnnotationAndValueFromMirror(it, elements).orElseThrow())
                            .collect(Collectors.toSet());
           return result;
        }
    }

    static class ReflectionResolver extends AnnotationTypeNameResolver {
        private final ScanResult scan;

        ReflectionResolver(ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(String annoTypeName) {
            ClassInfo classInfo = scan.getClassInfo(annoTypeName);
            if (classInfo == null) {
                try {
                    Class<? extends Annotation> annotationType = (Class<? extends Annotation>) Class.forName(annoTypeName);
                    return createAnnotationAndValueListFromAnnotations(annotationType.getAnnotations());
                } catch (ClassNotFoundException e) {
                    throw new ToolsException(e.getMessage(), e);
                }
            }
            return createAnnotationAndValueSet(classInfo);
        }
    }

    /**
     * Filter will apply the appropriate strategy determine which annotation types qualify as triggers for interception.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    abstract static class TriggerFilter {
        /**
         * The creator.
         */
        protected final InterceptorCreator creator;

        /**
         * The way to convert a string to the annotation type.
         */
        protected final AnnotationTypeNameResolver resolver;

        /**
         * the interceptor meta-annotation.
         */
        public static final AnnotationAndValue TRIGGER = create(InterceptedTrigger.class);

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

        /**
         * Returns true if the annotation qualifies/triggers interceptor creation.
         *
         * @param annotationTypeName the annotation type name
         * @return true if the annotation qualifies/triggers interceptor creation
         */
        public boolean isQualifyingTrigger(String annotationTypeName) {
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
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return resolver.resolve(annotationTypeName).contains(TRIGGER)
                    || TRIGGER.typeName().name().equals(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#ALL_RUNTIME}.
     */
    private static class AllRuntimeStrategy extends TriggerFilter {
        protected static final AnnotationAndValue RUNTIME = create(Retention.class, RetentionPolicy.RUNTIME.name());
        protected static final AnnotationAndValue CLASS = create(Retention.class, RetentionPolicy.CLASS.name());

        protected AllRuntimeStrategy(InterceptorCreator creator,
                                     AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
            Objects.requireNonNull(resolver);
            Objects.requireNonNull(annotationTypeName);
            return (resolver.resolve(annotationTypeName).contains(RUNTIME)
                            || resolver.resolve(annotationTypeName).contains(CLASS)
                            || ALLOW_LIST.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link Strategy#ALLOW_LISTED}.
     */
    private static class AllowListedStrategy extends TriggerFilter {
        private final Set<String> allowListed;

        protected AllowListedStrategy(InterceptorCreator creator) {
            super(creator);
            this.allowListed = Objects.requireNonNull(creator.allowListedAnnotationTypes());
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
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
        public boolean isQualifyingTrigger(String annotationTypeName) {
            Objects.requireNonNull(creator);
            Objects.requireNonNull(annotationTypeName);
            return (creator.isAllowListed(annotationTypeName) || ALLOW_LIST.contains(annotationTypeName));
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
        public boolean isQualifyingTrigger(String annotationTypeName) {
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

        String serviceTypeName() {
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
        public Set<String> allAnnotationTypeTriggers() {
            Set<String> allAnnotations = getAllAnnotations();
            if (allAnnotations.isEmpty()) {
                return Set.of();
            }

            TriggerFilter triggerFilter = triggerFilter();
            Set<String> annotationTypeTriggers = allAnnotations.stream()
                    .filter(triggerFilter::isQualifyingTrigger)
                    .filter(anno -> !TriggerFilter.TRIGGER.typeName().name().equals(anno))
                    .collect(Collectors.toSet());
            return annotationTypeTriggers;
        }

        @Override
        public Optional<InterceptionPlan> createInterceptorPlan(Set<String> interceptorAnnotationTriggers) {
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

            Set<AnnotationAndValue> serviceLevelAnnotations = getServiceLevelAnnotations();
            InterceptionPlan plan = InterceptionPlanDefault.builder()
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
            return serviceTypeName();
        }

        /**
         * @return the cumulative annotations referenced by this type
         */
        abstract Set<String> getAllAnnotations();

        /**
         * @return only the service level annotations referenced by this type
         */
        abstract Set<AnnotationAndValue> getServiceLevelAnnotations();

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
        abstract List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers);

        /**
         * @return all public methods for only the given interfaces
         */
        abstract List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers,
                                                                 Set<TypeName> interfaces);

        boolean containsAny(Set<AnnotationAndValue> annotations,
                            Set<String> annotationTypeNames) {
            for (AnnotationAndValue annotation : annotations) {
                if (annotationTypeNames.contains(annotation.typeName().name())) {
                    return true;
                }
            }
            return false;
        }

        boolean isProcessed(InjectionPointInfo.ElementKind kind,
                            Set<Modifier> modifiers,
                            Boolean isPrivate,
                            Boolean isStatic) {
            assert (ElementInfo.ElementKind.CONSTRUCTOR == kind || InjectionPointInfo.ElementKind.METHOD == kind)
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
            this.serviceTypeElement = Objects
                    .requireNonNull(processEnv.getElementUtils().getTypeElement(serviceTypeName()));
            this.processEnv = processEnv;
        }

        @Override
        Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(serviceTypeElement, processEnv);
            return set.stream().map(a -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return createAnnotationAndValueSet(serviceTypeElement);
        }

        @Override
        boolean hasNoArgConstructor() {
            return serviceTypeElement.getEnclosedElements().stream()
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
                result.add(TypeTools.createTypeNameFromMirror(tm).orElseThrow());
                gatherInterfaces(result, TypeTools.toTypeElement(tm).orElse(null));
            });

            TypeElement te = (TypeElement) processEnv.getTypeUtils().asElement(typeElement.getSuperclass());
            return gatherInterfaces(result, te);
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();

            // find the injectable constructor, falling back to the no-arg constructor
            gatherInjectableConstructor(result, serviceLevelAnnos, interceptorAnnotationTriggers);

            // gather all of the public methods as well as the no-arg constructor
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(e -> isProcessed(toKind(e), /*e.getParameters().size(),*/ e.getModifiers(), null, null))
                    .forEach(ee -> result.add(
                            create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers,
                                                        Set<TypeName> interfaces) {
            assert (!interfaces.isEmpty());
            List<InterceptedElement> result = new ArrayList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();

            // find the injectable constructor, falling back to the no-arg constructor
            gatherInjectableConstructor(result, serviceLevelAnnos, interceptorAnnotationTriggers);

            // gather all of the methods that map to one of our interfaces
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(e -> isProcessed(toKind(e), e.getModifiers(), null, null))
                    .filter(e -> mapsToAnInterface(e, interfaces))
                    .forEach(ee -> result.add(
                            create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        void gatherInjectableConstructor(List<InterceptedElement> result,
                                         Set<AnnotationAndValue> serviceLevelAnnos,
                                         Set<String> interceptorAnnotationTriggers) {
            serviceTypeElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .filter(ee -> !ee.getModifiers().contains(Modifier.PRIVATE))
                    .filter(ee -> {
                        boolean hasInject = ee.getAnnotationMirrors().stream()
                                .map(TypeTools::createAnnotationAndValue)
                                .map(AnnotationAndValue::typeName)
                                .map(TypeName::name)
                                .anyMatch(anno -> TypeNames.JAKARTA_INJECT.equals(anno) || TypeNames.JAVAX_INJECT.equals(anno));
                        return hasInject;
                    })
                    .forEach(ee -> result.add(
                            create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));

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
                            create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
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

        private InterceptedElement create(ExecutableElement ee,
                                          Set<AnnotationAndValue> serviceLevelAnnos,
                                          Set<String> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(serviceTypeElement, ee, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map(a -> a.typeName().name()).collect(Collectors.toSet()));
            return InterceptedElementDefault.builder()
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
        Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(classInfo);
            return set.stream().map(a -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return createAnnotationAndValueSet(classInfo);
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
        List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter(m -> isProcessed(toKind(m), /*m.getParameterInfo().length,*/ null, m.isPrivate(), m.isStatic()))
                    .filter(m -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                            || containsAny(createAnnotationAndValueSet(m.getAnnotationInfo()), interceptorAnnotationTriggers))
                    .forEach(mi -> result.add(
                            create(mi, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        @Override
        List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers,
                                                        Set<TypeName> interfaces) {
            List<InterceptedElement> result = new ArrayList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter(m -> isProcessed(toKind(m), null, m.isPrivate(), m.isStatic()))
                    .filter(m -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                            || containsAny(createAnnotationAndValueSet(m.getAnnotationInfo()), interceptorAnnotationTriggers))
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
                                          Set<AnnotationAndValue> serviceLevelAnnos,
                                          Set<String> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(mi, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map(a -> a.typeName().name()).collect(Collectors.toSet()));
            return InterceptedElementDefault.builder()
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
                                                                   Optional<ProcessingEnvironment> processEnv) {
        Objects.requireNonNull(interceptedService);
        Objects.requireNonNull(delegateCreator);
        if (processEnv.isPresent()) {
            return createInterceptorProcessorFromProcessor(interceptedService, delegateCreator, processEnv);
        }
        return createInterceptorProcessorFromReflection(interceptedService, delegateCreator);
    }


    /**
     * Create an interceptor processor based on annotation processing.
     *
     * @param interceptedService    the service being processed
     * @param delegateCreator       the real/delegate creator
     * @param processEnv            the processing env, if available
     * @return the {@link InterceptorCreatorDefault.AbstractInterceptorProcessor} to use
     */
    AbstractInterceptorProcessor createInterceptorProcessorFromProcessor(ServiceInfoBasics interceptedService,
                                                                         InterceptorCreator delegateCreator,
                                                                         Optional<ProcessingEnvironment> processEnv) {
        processEnv.ifPresent(Options::init);
        ALLOW_LIST.addAll(Options.getOptionStringList(Options.TAG_ALLOW_LISTED_INTERCEPTOR_ANNOTATIONS));
        if (processEnv.isPresent()) {
            return new ProcessorBased(interceptedService,
                                      delegateCreator,
                                      processEnv.get(),
                                      logger());
        }
        return createInterceptorProcessorFromReflection(interceptedService, delegateCreator);
    }

    /**
     * Create an interceptor processor based on reflection processing.
     *
     * @param interceptedService the service being processed
     * @param realCreator        the real/delegate creator
     * @return the {@link InterceptorCreatorDefault.AbstractInterceptorProcessor} to use
     */
    AbstractInterceptorProcessor createInterceptorProcessorFromReflection(ServiceInfoBasics interceptedService,
                                                                          InterceptorCreator realCreator) {
        return new ReflectionBased(Objects.requireNonNull(interceptedService),
                                   Objects.requireNonNull(realCreator),
                                   Objects.requireNonNull(SCAN.get().getClassInfo(interceptedService.serviceTypeName())),
                                   logger());
    }

    /**
     * Creates the interceptor source code type name given its plan.
     *
     * @param plan the plan
     * @return the interceptor type name
     */
    static TypeName createInterceptorSourceTypeName(InterceptionPlan plan) {
        String parent = plan.interceptedService().serviceTypeName();
        return toInterceptorTypeName(parent);
    }

    /**
     * Creates the source code associated with an interception plan.
     *
     * @param plan the plan
     * @return the java source code body
     */
    String createInterceptorSourceBody(InterceptionPlan plan) {
        String parent = plan.interceptedService().serviceTypeName();
        TypeName interceptorTypeName = toInterceptorTypeName(parent);
        Map<String, Object> subst = new HashMap<>();
        subst.put("packageName", interceptorTypeName.packageName());
        subst.put("className", interceptorTypeName.className());
        subst.put("parent", parent);
        subst.put("header", BuilderTypeTools.copyrightHeaderFor(getClass().getName()));
        subst.put("generatedanno", toGeneratedSticker(null));
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
                        str -> new IdAndToString(str.replace(".", "_"), str)));
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

            if (element.elementInfo().elementKind() == ElementInfo.ElementKind.CONSTRUCTOR) {
                continue;
            }

            for (ElementInfo param : element.elementInfo().parameterInfo()) {
                IdAndToString paramTypedElement = new IdAndToString(element.elementInfo().elementName()
                                                                            + "__" + param.elementName(),
                                                                    typeNameElementNameAnnotations(param));
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
        String name = (mi.elementKind() == ElementInfo.ElementKind.CONSTRUCTOR) ? CTOR_ALIAS : mi.elementName();
        String builder = typeNameElementNameAnnotations(mi);
        return new IdAndToString(name, builder);
    }

    private static String typeNameElementNameAnnotations(ElementInfo ei) {
        StringBuilder builder = new StringBuilder(".typeName(create(" + ei.elementTypeName() + ".class))");
        builder.append("\n\t\t\t.elementName(").append(CodeGenUtils.elementNameRef(ei.elementName())).append(")");
        TreeSet<AnnotationAndValue> sortedAnnotations = new TreeSet<>(ei.annotations());
        for (AnnotationAndValue anno : sortedAnnotations) {
            builder.append("\n\t\t\t.addAnnotation(").append(toDecl(anno)).append(")");
        }
        return builder.toString();
    }

    private static IdAndToString toDecl(ElementInfo elementInfo) {
        String name = elementInfo.elementName();
        return new IdAndToString(name, elementInfo.elementTypeName() + " " + name);
    }

    private static IdAndToString toDecl(AnnotationAndValue anno) {
        StringBuilder builder = new StringBuilder("AnnotationAndValueDefault.create(" + anno.typeName() + ".class");
        Map<String, String> map = anno.values();
        String val = anno.value().orElse(null);
        if (map != null && !map.isEmpty()) {
            builder.append(", Map.of(");
            int count = 0;
            TreeMap<String, String> sortedMap = new TreeMap<>(map);
            for (Map.Entry<String, String> e : sortedMap.entrySet()) {
                if (count++ > 0) {
                    builder.append(", ");
                }
                builder.append("\"")
                        .append(e.getKey())
                        .append("\", \"")
                        .append(e.getValue())
                        .append("\"");
            }
            builder.append(")");
        } else if (val != null) {
            builder.append(", \"")
                    .append(val)
                    .append("\"");
        }
        builder.append(")");
        return new IdAndToString(anno.typeName().name(), builder);
    }

    @SuppressWarnings("checkstyle:OperatorWrap")
    private static InterceptedMethodCodeGen toBody(InterceptedElement method) {
        MethodElementInfo mi = method.elementInfo();
        String name = (mi.elementKind() == ElementInfo.ElementKind.CONSTRUCTOR) ? CTOR_ALIAS : mi.elementName();
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
                    .map(TypeTools::toObjectTypeName)
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

        boolean hasReturn = !mi.elementTypeName().equals(void.class.getName());
        builder.append(argDecls);
        builder.append(")");
        if (!mi.throwableTypeNames().isEmpty()) {
            builder.append(" throws ").append(CommonUtils.toString(mi.throwableTypeNames()));
        }
        String methodDecl = builder.toString();
        builder.append(" {\n");
        TypeName supplierType = (hasReturn) ? toObjectTypeName(mi.elementTypeName()) : TypeNameDefault.create(Void.class);

        String elementArgInfo = "";
        if (hasArgs) {
            elementArgInfo = ",\n\t\t\t\tnew TypedElementName[] {" + typedElementArgs + "}";
        }
        return new InterceptedMethodCodeGen(name, methodDecl, true, hasReturn, supplierType, elementArgInfo, args,
                                            objArrayArgs, untypedElementArgs,
                                            method.interceptedTriggerTypeNames(), builder);
    }

    private static TypeName toInterceptorTypeName(String serviceTypeName) {
        TypeName typeName = TypeNameDefault.createFromTypeName(serviceTypeName);
        return TypeNameDefault.create(typeName.packageName(), typeName.className()
                                                                      + INNER_INTERCEPTOR_CLASS_NAME);
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
                                 Collection<String> interceptedTriggerTypeNames,
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
                                                                (str) -> str.replace(".", "_"), null);
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
