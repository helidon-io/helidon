/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
import io.helidon.pico.ElementInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InterceptedTrigger;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.spi.Resetable;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueFromMirror;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueListFromAnnotations;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createMethodElementInfo;
import static io.helidon.pico.tools.TypeTools.gatherAllAnnotationsUsedOnPublicNonStaticMethods;
import static io.helidon.pico.tools.TypeTools.toKind;
import static io.helidon.pico.tools.TypeTools.toObjectTypeName;
import static io.helidon.pico.types.DefaultAnnotationAndValue.create;

/**
 * The default interceptor creator strategy in use.
 *
 * @deprecated
 */
@Singleton
@SuppressWarnings("unchecked")
public class DefaultInterceptorCreator extends AbstractCreator implements InterceptorCreator, Resetable {
    private static final LazyValue<ScanResult> SCAN = LazyValue.create(ReflectionHandler.INSTANCE::scan);

    private static final String INNER_INTERCEPTOR_CLASS_NAME = "$$" + PicoServicesConfig.NAME + "Interceptor";
    private static final String COMPLEX_INTERCEPTOR_HBS = "complex-interceptor.hbs";
    private static final double INTERCEPTOR_PRIORITY_DELTA = 0.001;
    private static final String CTOR_ALIAS = "ctor";

    private Set<String> whiteListedAnnoTypeNames;
    private static List<String> manuallyWhiteListed;

    /**
     * Constructor.
     *
     * @deprecated
     */
    public DefaultInterceptorCreator() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    @Override
    public boolean reset(
            boolean deep) {
        whiteListedAnnoTypeNames = null;
        return true;
    }

    /**
     * Sets the white listed annotation types triggering interception creation for the default interceptor creator.
     *
     * @param whiteListedAnnotationTypes the whitelisted annotation types
     * @return this instance
     */
    public DefaultInterceptorCreator whiteListed(
            Set<String> whiteListedAnnotationTypes) {
        this.whiteListedAnnoTypeNames = whiteListedAnnotationTypes;
        return this;
    }

    @Override
    public Set<String> whiteListedAnnotationTypes() {
        return (whiteListedAnnoTypeNames != null) ? whiteListedAnnoTypeNames : Collections.emptySet();
    }

    @Override
    public Optional<InterceptionPlan> createInterceptorPlan(
            ServiceInfoBasics interceptedService,
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
        abstract Collection<AnnotationAndValue> resolve(
                String annoTypeName);
    }

    static class ProcessorResolver extends AnnotationTypeNameResolver {
        private final Elements elements;

        ProcessorResolver(
                Elements elements) {
            this.elements = elements;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(
                String annoTypeName) {
           TypeElement typeElement = elements.getTypeElement(annoTypeName);
           List<? extends AnnotationMirror> annotations = typeElement.getAnnotationMirrors();
           Set<AnnotationAndValue> result = annotations.stream()
                            .map(it -> createAnnotationAndValueFromMirror(it, elements).orElseThrow())
                            .collect(Collectors.toSet());
           return result;
        }
    }

    static class ReflectionResolver extends AnnotationTypeNameResolver {
        private final ScanResult scan;

        ReflectionResolver(
                ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(
                String annoTypeName) {
            ClassInfo classInfo = scan.getClassInfo(annoTypeName);
            if (Objects.isNull(classInfo)) {
                try {
                    Class<? extends Annotation> annotationType = (Class) Class.forName(annoTypeName);
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

        protected TriggerFilter(
                InterceptorCreator creator) {
            this.creator = Objects.requireNonNull(creator);
            this.resolver = null;
        }

        protected TriggerFilter(
                InterceptorCreator creator,
                AnnotationTypeNameResolver resolver) {
            this.creator = Objects.requireNonNull(creator);
            this.resolver = Objects.requireNonNull(resolver);
        }

        /**
         * Returns true if the annotation qualifies/triggers interceptor creation.
         *
         * @param annotationTypeName the annotation type name
         * @return true if the annotation qualifies/triggers interceptor creation.
         */
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
            return (creator != null) && creator.isWhiteListed(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#EXPLICIT}.
     */
    private static class ExplicitStrategy extends TriggerFilter {
        protected ExplicitStrategy(
                InterceptorCreator creator,
                AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
            return resolver.resolve(annotationTypeName).contains(TRIGGER)
                    || TRIGGER.typeName().name().equals(annotationTypeName);
        }
    }

    /**
     * Enforces {@link Strategy#ALL_RUNTIME}.
     */
    private static class AllRuntimeStrategy extends TriggerFilter {
        protected static final AnnotationAndValue RUNTIME = create(Retention.class, RetentionPolicy.RUNTIME.name());

        protected AllRuntimeStrategy(
                InterceptorCreator creator,
                AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
            return resolver.resolve(annotationTypeName).contains(RUNTIME)
                    || (manuallyWhiteListed != null && manuallyWhiteListed.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link Strategy#WHITE_LISTED}.
     */
    private static class WhiteListedStrategy extends TriggerFilter {
        private final Set<String> whiteListed;

        protected WhiteListedStrategy(
                InterceptorCreator creator) {
            super(creator);
            this.whiteListed = Objects.requireNonNull(creator.whiteListedAnnotationTypes());
        }

        @Override
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
            return whiteListed.contains(annotationTypeName)
                    || (manuallyWhiteListed != null && manuallyWhiteListed.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link Strategy#CUSTOM}.
     */
    private static class CustomStrategy extends TriggerFilter {
        protected CustomStrategy(
                InterceptorCreator creator) {
            super(creator);
        }

        @Override
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
            return creator.isWhiteListed(annotationTypeName)
                    || (manuallyWhiteListed != null && manuallyWhiteListed.contains(annotationTypeName));
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

        protected BlendedStrategy(
                InterceptorCreator creator,
                AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
            this.customStrategy = new CustomStrategy(creator);
        }

        @Override
        public boolean isQualifyingTrigger(
                String annotationTypeName) {
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
    private static TriggerFilter createTriggerFilter(
            InterceptorCreator creator,
            AnnotationTypeNameResolver resolver) {
        Strategy strategy = creator.strategy();
        if (Strategy.EXPLICIT == strategy) {
            return new ExplicitStrategy(creator, resolver);
        } else if (Strategy.ALL_RUNTIME == strategy) {
            return new AllRuntimeStrategy(creator, resolver);
        }  else if (Strategy.WHITE_LISTED == strategy) {
            return new WhiteListedStrategy(creator);
        } else if (Strategy.CUSTOM == strategy) {
            return new CustomStrategy(creator);
        } else if (Strategy.NONE == strategy) {
            return new NoneStrategy();
        } else if (Strategy.BLENDED == strategy || Objects.isNull(strategy)) {
            return new BlendedStrategy(creator, resolver);
        } else {
            throw new ToolsException("unknown strategy: " + strategy);
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

        protected AbstractInterceptorProcessor(
                ServiceInfoBasics interceptedService,
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
         * @return the annotation resolver in use.
         */
        AnnotationTypeNameResolver resolver() {
            return resolver;
        }

        /**
         * @return the trigger filter in use.
         */
        TriggerFilter triggerFilter() {
            return triggerFilter;
        }

        /**
         * The set of annotation types that are trigger interception.
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
                    .filter((anno) -> !TriggerFilter.TRIGGER.typeName().name().equals(anno))
                    .collect(Collectors.toSet());
            return annotationTypeTriggers;
        }

        @Override
        public Optional<InterceptionPlan> createInterceptorPlan(
                Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> interceptedElements = getInterceptedElements(interceptorAnnotationTriggers);
            if (interceptedElements == null || interceptedElements.isEmpty()) {
                return Optional.empty();
            }

            if (!hasNoArgConstructor()) {
                ToolsException te =  new ToolsException("there must be a no-arg constructor for: " + serviceTypeName());
                logger.log(System.Logger.Level.WARNING, "skipping interception for: " + serviceTypeName(), te);
                return Optional.empty();
            }

            Set<AnnotationAndValue> serviceLevelAnnotations = getServiceLevelAnnotations();
            InterceptionPlan plan = DefaultInterceptionPlan.builder()
                    .interceptedService(interceptedService)
                    .serviceLevelAnnotations(serviceLevelAnnotations)
                    .annotationTriggerTypeNames(interceptorAnnotationTriggers)
                    .interceptedElements(interceptedElements)
                    .build();
            return Optional.of(plan);
        }

        /**
         * @return the cumulative annotations referenced by this type.
         */
        abstract Set<String> getAllAnnotations();

        /**
         * @return only the service level annotations referenced by this type.
         */
        abstract Set<AnnotationAndValue> getServiceLevelAnnotations();

        /**
         * Intercepted classes must have a no-arg constructor (current restriction).
         * @return true if there is a no-arg constructor present.
         */
        abstract boolean hasNoArgConstructor();

        abstract List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers);

        boolean containsAny(Set<AnnotationAndValue> annotations, Set<String> annotationTypeNames) {
            assert (!annotationTypeNames.isEmpty());
            for (AnnotationAndValue annotation : annotations) {
                if (annotationTypeNames.contains(annotation.typeName().name())) {
                    return true;
                }
            }
            return false;
        }

        boolean isProcessed(InjectionPointInfo.ElementKind kind,
                            int methodArgCount,
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

            if (ElementInfo.ElementKind.CONSTRUCTOR == kind && methodArgCount != 0) {
                return false;
            }

            return true;
        }
    }

    private static class ProcessorBased extends AbstractInterceptorProcessor {
        private final TypeElement serviceTypeElement;
        private final ProcessingEnvironment processEnv;

        ProcessorBased(
                ServiceInfoBasics interceptedService,
                InterceptorCreator realCreator,
                ProcessingEnvironment processEnv,
                System.Logger logger) {
            super(interceptedService, realCreator, createResolverFromProcessor(processEnv), logger);
            this.serviceTypeElement = Objects
                    .requireNonNull(processEnv.getElementUtils().getTypeElement(serviceTypeName()));
            this.processEnv = processEnv;
        }

        @Override
        public Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(serviceTypeElement, processEnv);
            return set.stream().map((a) -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return createAnnotationAndValueSet(serviceTypeElement);
        }

        @Override
        public boolean hasNoArgConstructor() {
            return serviceTypeElement.getEnclosedElements().stream()
                    .filter((it) -> it.getKind().equals(ElementKind.CONSTRUCTOR))
                    .map(ExecutableElement.class::cast)
                    .anyMatch((it) -> it.getParameters().isEmpty());
        }

        @Override
        protected List<InterceptedElement> getInterceptedElements(
                Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new LinkedList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            serviceTypeElement.getEnclosedElements().stream()
                    .filter((e) -> e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .filter((e) -> isProcessed(toKind(e), e.getParameters().size(), e.getModifiers(), null, null))
                    .forEach((ee) -> result.add(create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        private InterceptedElement create(
                ExecutableElement ee,
                Set<AnnotationAndValue> serviceLevelAnnos,
                Set<String> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(serviceTypeElement, ee, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map((a) -> a.typeName().name()).collect(Collectors.toSet()));
            return DefaultInterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
        }
    }

    private static class ReflectionBased extends AbstractInterceptorProcessor {
        private final ClassInfo classInfo;

        ReflectionBased(
                ServiceInfoBasics interceptedService,
                InterceptorCreator realCreator,
                ClassInfo classInfo,
                System.Logger logger) {
            super(/*serviceTypeName,*/ interceptedService, realCreator, createResolverFromReflection(), logger);
            this.classInfo = classInfo;
        }

        @Override
        public Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(classInfo);
            return set.stream().map((a) -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return createAnnotationAndValueSet(classInfo);
        }

        @Override
        public boolean hasNoArgConstructor() {
            return classInfo.getConstructorInfo().stream()
                    .filter((mi) -> !mi.isPrivate())
                    .anyMatch((mi) -> mi.getParameterInfo().length == 0);
        }

        @Override
        protected List<InterceptedElement> getInterceptedElements(
                Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new LinkedList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter((m) -> isProcessed(toKind(m),
                                               m.getParameterInfo().length, null, m.isPrivate(), m.isStatic()))
                    .filter((m) -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                                    || containsAny(createAnnotationAndValueSet(
                                            m.getAnnotationInfo()), interceptorAnnotationTriggers))
                    .forEach((mi) -> result.add(create(mi, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        private InterceptedElement create(
                MethodInfo mi,
                Set<AnnotationAndValue> serviceLevelAnnos,
                Set<String> interceptorAnnotationTriggers) {
            MethodElementInfo elementInfo = createMethodElementInfo(mi, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map((a) -> a.typeName().name()).collect(Collectors.toSet()));
            return DefaultInterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
        }
    }

    /**
     * Create an annotation resolver based on annotation processing.
     *
     * @param processEnv the processing env
     * @return the {@link io.helidon.pico.tools.DefaultInterceptorCreator.AnnotationTypeNameResolver} to use.
     */
    static AnnotationTypeNameResolver createResolverFromProcessor(
            ProcessingEnvironment processEnv) {
        return new ProcessorResolver(processEnv.getElementUtils());
    }

    /**
     * Create an annotation resolver based on reflective processing.
     *
     * @return the {@link io.helidon.pico.tools.DefaultInterceptorCreator.AnnotationTypeNameResolver} to use.
     */
    static AnnotationTypeNameResolver createResolverFromReflection() {
        return new ReflectionResolver(SCAN.get());
    }

    @Override
    public AbstractInterceptorProcessor createInterceptorProcessor(
            ServiceInfoBasics interceptedService,
            InterceptorCreator delegateCreator,
            Optional<ProcessingEnvironment> processEnv) {
        if (processEnv.isPresent()) {
            return createInterceptorProcessorFromProcessor(interceptedService, delegateCreator, processEnv.get());
        }
        return createInterceptorProcessorFromReflection(interceptedService, delegateCreator);
    }


    /**
     * Create an interceptor processor based on annotation processing.
     *
     * @param interceptedService the service being processed
     * @param realCreator     the real/delegate creator
     * @param processEnv the processing env
     * @return the {@link io.helidon.pico.tools.DefaultInterceptorCreator.AbstractInterceptorProcessor} to use.
     */
    AbstractInterceptorProcessor createInterceptorProcessorFromProcessor(
            ServiceInfoBasics interceptedService,
            InterceptorCreator realCreator,
            ProcessingEnvironment processEnv) {
        Options.init(processEnv);
        if (manuallyWhiteListed == null) {
            manuallyWhiteListed = Options.getOptionStringList(Options.TAG_WHITE_LISTED_INTERCEPTOR_ANNOTATIONS);
        }
        return new ProcessorBased(Objects.requireNonNull(interceptedService),
                                  Objects.requireNonNull(realCreator),
                                  Objects.requireNonNull(processEnv),
                                  logger());
    }

    /**
     * Create an interceptor processor based on reflection processing.
     *
     * @param interceptedService the service being processed
     * @param realCreator     the real/delegate creator
     * @return the {@link io.helidon.pico.tools.DefaultInterceptorCreator.AbstractInterceptorProcessor} to use.
     */
    AbstractInterceptorProcessor createInterceptorProcessorFromReflection(
            ServiceInfoBasics interceptedService,
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
    static TypeName createInterceptorSourceTypeName(
            InterceptionPlan plan) {
        String parent = plan.interceptedService().serviceTypeName();
        return toInterceptorTypeName(parent);
    }

    /**
     * Creates the source code associated with an interception plan.
     *
     * @param plan the plan
     * @return the java source code body
     */
    String createInterceptorSourceBody(
            InterceptionPlan plan) {
        String parent = plan.interceptedService().serviceTypeName();
        TypeName interceptorTypeName = toInterceptorTypeName(parent);
        Map<String, Object> subst = new HashMap<>();
        subst.put("packageName", interceptorTypeName.packageName());
        subst.put("className", interceptorTypeName.className());
        subst.put("parent", parent);
        subst.put("generatedanno", toGeneratedSticker(null));
        subst.put("weight", interceptorWeight(plan.interceptedService().declaredWeight()));
        subst.put("interceptedmethoddecls", toInterceptedMethodDecls(plan));
        subst.put("interceptedmethods", IdAndToString
                .toList(plan.interceptedElements(), DefaultInterceptorCreator::toBody).stream()
                .filter((it) -> !it.getId().equals(CTOR_ALIAS))
                .collect(Collectors.toList()));
        subst.put("annotationtriggertypenames", IdAndToString.toList(plan.annotationTriggerTypeNames(),
                                             (str) -> new IdAndToString(str.replace(".", "_"), str)));
        subst.put("servicelevelannotations", IdAndToString
                .toList(plan.serviceLevelAnnotations(), DefaultInterceptorCreator::toDecl));
        String template = templateHelper.safeLoadTemplate(COMPLEX_INTERCEPTOR_HBS);
        return templateHelper.applySubstitutions(template, subst, true).trim();
    }

    private static List<IdAndToString> toInterceptedMethodDecls(
            InterceptionPlan plan) {
        List<IdAndToString> result = new LinkedList<>();
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

    private static IdAndToString toDecl(
            InterceptedElement method) {
        MethodElementInfo mi = method.elementInfo();
        String name = (mi.elementKind() == ElementInfo.ElementKind.CONSTRUCTOR) ? CTOR_ALIAS : mi.elementName();
        String builder = typeNameElementNameAnnotations(mi);
        return new IdAndToString(name, builder);
    }

    private static String typeNameElementNameAnnotations(
            ElementInfo ei) {
        StringBuilder builder = new StringBuilder(".typeName(create(" + ei.elementTypeName() + ".class))");
        builder.append("\n\t\t\t.elementName(\"").append(ei.elementName()).append("\")");
        for (AnnotationAndValue anno : ei.annotations()) {
            builder.append("\n\t\t\t.annotation(").append(toDecl(anno)).append(")");
        }
        return builder.toString();
    }

    private static IdAndToString toDecl(
            ElementInfo elementInfo) {
        String name = elementInfo.elementName();
        return new IdAndToString(name, elementInfo.elementTypeName() + " " + name);
    }

    private static IdAndToString toDecl(
            AnnotationAndValue anno) {
        StringBuilder builder = new StringBuilder("DefaultAnnotationAndValue.create(" + anno.typeName() + ".class");
        Map<String, String> map = anno.values();
        String val = anno.value().orElse(null);
        if (map != null && !map.isEmpty()) {
            builder.append(", Map.of(");
            int count = 0;
            for (Map.Entry<String, String> e : map.entrySet()) {
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
    private static IdAndToString toBody(
            InterceptedElement method) {
        MethodElementInfo mi = method.elementInfo();
        String name = (mi.elementKind() == ElementInfo.ElementKind.CONSTRUCTOR) ? CTOR_ALIAS : mi.elementName();
        StringBuilder builder = new StringBuilder();
        builder.append("public ").append(mi.elementTypeName()).append(" ").append(mi.elementName()).append("(");
        String args = CommonUtils.toString(mi.parameterInfo().stream().map(ElementInfo::elementName)
                                     .collect(Collectors.toList()));
        String argDecls = "";
        String objArrayArgs = "";
        String typedElementArgs = "";
        boolean hasArgs = (args.length() > 0);
        if (hasArgs) {
            argDecls = CommonUtils.toString(IdAndToString.toList(mi.parameterInfo(), DefaultInterceptorCreator::toDecl));
            AtomicInteger count = new AtomicInteger();
            objArrayArgs = CommonUtils.toString(mi.parameterInfo().stream()
                                       .map((ei) -> ("(" + toObjectTypeName(ei.elementTypeName())) + ") "
                                               + "args[" + count.getAndIncrement() + "]")
                                       .collect(Collectors.toList()));
            typedElementArgs = CommonUtils.toString(mi.parameterInfo().stream()
                                       .map((ei) -> "__" + mi.elementName() + "__" + ei.elementName())
                                       .collect(Collectors.toList()), null, ",\n\t\t\t");
        }
        boolean hasReturn = !mi.elementTypeName().equals(void.class.getName());
        builder.append(argDecls);
        builder.append(")");
        if (!mi.throwableTypeNames().isEmpty()) {
            builder.append(" throws ").append(CommonUtils.toString(mi.throwableTypeNames()));
        }
        // note to self: turn these into mustaches
        builder.append(" {\n");
        if (hasArgs) {
            builder.append("\t\tObject[] args = new Object[] {" + args + "};\n");
        }
        if (hasReturn) {
            TypeName supplierType = toObjectTypeName(mi.elementTypeName());
            builder.append("\t\tSupplier<" + supplierType + "> call = () -> {\n" +
                                   "\t\t\ttry {\n" +
                                   "\t\t\t\treturn __impl." + mi.elementName() + "(" + objArrayArgs + ");\n" +
                                   "\t\t\t} catch (RuntimeException e) {\n" +
                                   "\t\t\t\tthrow e;\n" +
                                   "\t\t\t} catch (Throwable t) {\n" +
                                   "\t\t\t\tthrow new InvocationException(t.getMessage(), t, __sp);\n" +
                                   "\t\t\t}\n" +
                                   "\t\t};\n");
            builder.append("\t\t" + supplierType + " result = createInvokeAndSupply(call,\n");
        } else {
            builder.append("\t\tRunnable call = () -> {\n" +
                                   "\t\t\ttry {\n" +
                                   "\t\t\t\t__impl." + mi.elementName() + "(" + objArrayArgs + ");\n" +
                                   "\t\t\t} catch (RuntimeException e) {\n" +
                                   "\t\t\t\tthrow e;\n" +
                                   "\t\t\t} catch (Throwable t) {\n" +
                                   "\t\t\t\tthrow new InvocationException(t.getMessage(), t, __sp);\n" +
                                   "\t\t\t}\n" +
                                   "\t\t};\n");
            builder.append("\t\tcreateAndInvoke(call,\n");
        }
        builder.append("\t\t\t__" + mi.elementName() + "__interceptors,\n" +
                                    "\t\t\t__sp,\n" +
                                    "\t\t\t__serviceTypeName,\n" +
                                    "\t\t\t__serviceLevelAnnotations,\n" +
                                    "\t\t\t__" + mi.elementName());
        builder.append(",\n");
        builder.append("\t\t\t" + (hasArgs ? "args" : null));
        if (hasArgs) {
            builder.append(",\n");
            builder.append("\t\t\t" + typedElementArgs);
        }
        builder.append(");\n");
        if (hasReturn) {
            builder.append("\t\treturn result;\n");
        }
        builder.append("\t}\n");
        return new InterceptedMethodCodeGen(name, method.interceptedTriggerTypeNames(), builder);
    }

    private static TypeName toInterceptorTypeName(
            String serviceTypeName) {
        TypeName typeName = DefaultTypeName.createFromTypeName(serviceTypeName);
        return DefaultTypeName.create(typeName.packageName(), typeName.className()
                                                                      + INNER_INTERCEPTOR_CLASS_NAME);
    }

    /**
     * Assign a weight slightly higher than the service weight passed in.
     *
     * @param serviceWeight the service weight, where null defaults to {@link io.helidon.common.Weighted#DEFAULT_WEIGHT}.
     * @return the higher weighted value appropriate for interceptors
     */
    static double interceptorWeight(
            Optional<Double> serviceWeight) {
        double val = serviceWeight.orElse(Weighted.DEFAULT_WEIGHT);
        return val + INTERCEPTOR_PRIORITY_DELTA;
    }


    static class InterceptedMethodCodeGen extends IdAndToString {
        private final String interceptedTriggerTypeNames;

        InterceptedMethodCodeGen(
                String id,
                Collection<String> interceptedTriggerTypeNames,
                Object toString) {
            super(id, toString);
            this.interceptedTriggerTypeNames = CommonUtils.toString(interceptedTriggerTypeNames,
                                                                (str) -> str.replace(".", "_"), null);
        }

        String interceptedTriggerTypeNames() {
            return interceptedTriggerTypeNames;
        }
    }

}
