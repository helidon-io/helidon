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

package io.helidon.pico.tools.creator.impl;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.ElementInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InterceptedTrigger;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.InterceptedElement;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.InterceptorCreator;
import io.helidon.pico.tools.processor.Options;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.TemplateHelper;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.pico.builder.processor.tools.BuilderTypeTools.createAnnotationAndValueFromMirror;
import static io.helidon.pico.types.DefaultAnnotationAndValue.create;
import static io.helidon.pico.tools.processor.TypeTools.createAnnotationAndValueListFromAnnotations;
import static io.helidon.pico.tools.processor.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.processor.TypeTools.createMethodInfo;
import static io.helidon.pico.tools.processor.TypeTools.gatherAllAnnotationsUsedOnPublicNonStaticMethods;
import static io.helidon.pico.tools.processor.TypeTools.toKind;

/**
 * The default interceptor creator strategy in use.
 */
@Singleton
@SuppressWarnings("unchecked")
public class DefaultInterceptorCreator implements InterceptorCreator, Resetable {
    private static final System.Logger LOGGER = System.getLogger(DefaultInterceptorCreator.class.getName());

    private static final LazyValue<ScanResult> SCAN = LazyValue.create(ReflectionHandler.INSTANCE::getScan);

    private static final String INNER_INTERCEPTOR_CLASS_NAME = "$$" + PicoServicesConfig.NAME + "Interceptor";
    private static final String COMPLEX_INTERCEPTOR_HBS = "complex-interceptor.hbs";
    private static final double INTERCEPTOR_PRIORITY_DELTA = 0.001;
    private static final String CTOR_ALIAS = "ctor";

    private Set<String> whiteListedAnnoTypeNames;
    private static List<String> manuallyWhiteListed;

    @Override
    public void reset() {
        whiteListedAnnoTypeNames = null;
    }

    /**
     * Sets the white listed annotation types triggering interception creation for the default interceptor creator.
     *
     * @param whiteListedAnnotationTypes the whitelisted annotation types
     * @return this instance
     */
    public DefaultInterceptorCreator setWhiteListed(Set<String> whiteListedAnnotationTypes) {
        this.whiteListedAnnoTypeNames = whiteListedAnnotationTypes;
        return this;
    }

    @Override
    public Set<String> getWhiteListedAnnotationTypes() {
        return Objects.nonNull(whiteListedAnnoTypeNames) ? whiteListedAnnoTypeNames : Collections.emptySet();
    }

    @Override
    public InterceptionPlan createInterceptorPlan(ServiceInfoBasics interceptedService,
                                                  ProcessingEnvironment processEnv,
                                                  Set<String> annotationTypeTriggers) {
        return createInterceptorProcessor(interceptedService, this, processEnv)
                .createInterceptorPlan(annotationTypeTriggers);
    }

    /**
     * Abstract base for handling the resolution of annotation types by name.
     */
    public abstract static class AnnotationTypeNameResolver {
        /**
         * Determine the all of the annotations belonging to a particular annotation type name.
         *
         * @param annoTypeName the annotation type name
         * @return the list of (meta) annotations for the given annotation
         */
        public abstract Collection<AnnotationAndValue> resolve(String annoTypeName);
    }

    private static class ProcessorResolver extends AnnotationTypeNameResolver {
        private final Elements elements;

        ProcessorResolver(Elements elements) {
            this.elements = elements;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(String annoTypeName) {
           TypeElement typeElement = elements.getTypeElement(annoTypeName);
           List<? extends AnnotationMirror> annotations = typeElement.getAnnotationMirrors();
           Set<AnnotationAndValue> result = annotations.stream()
                            .map(it -> createAnnotationAndValueFromMirror(it, elements))
                            .collect(Collectors.toSet());
           return result;
        }
    }

    private static class ReflectionResolver extends AnnotationTypeNameResolver {
        private final ScanResult scan;

        ReflectionResolver(ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public Collection<AnnotationAndValue> resolve(String annoTypeName) {
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
    public abstract static class TriggerFilter {
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

        protected TriggerFilter(InterceptorCreator creator, AnnotationTypeNameResolver resolver) {
            this.creator = Objects.requireNonNull(creator);
            this.resolver = Objects.requireNonNull(resolver);
        }

        /**
         * Returns true if the annotation qualifies/triggers interceptor creation.
         *
         * @param annotationTypeName the annotation type name
         * @return true if the annotation qualifies/triggers interceptor creation.
         */
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return Objects.nonNull(creator) && creator.isWhiteListed(annotationTypeName);
        }
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#EXPLICIT}.
     */
    private static class ExplicitStrategy extends TriggerFilter {
        protected ExplicitStrategy(InterceptorCreator creator, AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return resolver.resolve(annotationTypeName).contains(TRIGGER)
                    || TRIGGER.typeName().name().equals(annotationTypeName);
        }
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#ALL_RUNTIME}.
     */
    private static class AllRuntimeStrategy extends TriggerFilter {
        protected static final AnnotationAndValue RUNTIME = create(Retention.class, RetentionPolicy.RUNTIME.name());

        protected AllRuntimeStrategy(InterceptorCreator creator, AnnotationTypeNameResolver resolver) {
            super(creator, resolver);
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return resolver.resolve(annotationTypeName).contains(RUNTIME)
                    || (Objects.nonNull(manuallyWhiteListed) && manuallyWhiteListed.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#WHITE_LISTED}.
     */
    private static class WhiteListedStrategy extends TriggerFilter {
        private final Set<String> whiteListed;

        protected WhiteListedStrategy(InterceptorCreator creator) {
            super(creator);
            this.whiteListed = Objects.requireNonNull(creator.getWhiteListedAnnotationTypes());
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return whiteListed.contains(annotationTypeName)
                    || (Objects.nonNull(manuallyWhiteListed) && manuallyWhiteListed.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#CUSTOM}.
     */
    private static class CustomStrategy extends TriggerFilter {
        protected CustomStrategy(InterceptorCreator creator) {
            super(creator);
        }

        @Override
        public boolean isQualifyingTrigger(String annotationTypeName) {
            return creator.isWhiteListed(annotationTypeName)
                    || (Objects.nonNull(manuallyWhiteListed) && manuallyWhiteListed.contains(annotationTypeName));
        }
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#NONE}.
     */
    private static class NoneStrategy extends TriggerFilter {
    }

    /**
     * Enforces {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#BLENDED}.
     */
    private static class BlendedStrategy extends ExplicitStrategy {
        private final CustomStrategy customStrategy;

        protected BlendedStrategy(InterceptorCreator creator, AnnotationTypeNameResolver resolver) {
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
     * Returns the {@link io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.TriggerFilter} appropriate for
     * the given {@link io.helidon.pico.tools.creator.InterceptorCreator}.
     *
     * @param creator   the interceptor creator
     * @param resolver  the resolver, used in cases where the implementation needs to research more about a given annotation type
     * @return the trigger filter instance
     */
    public static TriggerFilter createTriggerFilter(InterceptorCreator creator, AnnotationTypeNameResolver resolver) {
        Strategy strategy = creator.getStrategy();
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
    public abstract static class InterceptorProcessor {
        /**
         * The service being intercepted/processed.
         */
        protected final ServiceInfoBasics interceptedService;

        /**
         * The "real" / delegate creator.
         */
        protected final InterceptorCreator creator;

        private final AnnotationTypeNameResolver resolver;
        private final TriggerFilter triggerFilter;

        protected InterceptorProcessor(ServiceInfoBasics interceptedService,
                                       InterceptorCreator realCreator,
                                       AnnotationTypeNameResolver resolver) {
            this.creator = realCreator;
            this.interceptedService = interceptedService;
            this.resolver = resolver;
            this.triggerFilter = createTriggerFilter(realCreator, resolver);
        }

        public String getServiceTypeName() {
            return interceptedService.serviceTypeName();
        }

        /**
         * @return the annotation resolver in use.
         */
        public AnnotationTypeNameResolver getResolver() {
            return resolver;
        }

        /**
         * @return the trigger filter in use.
         */
        public TriggerFilter getTriggerFilter() {
            return triggerFilter;
        }

        /**
         * @return the set of annotation types that are trigger interception.
         */
        public Set<String> getAllAnnotationTypeTriggers() {
            Set<String> allAnnotations = getAllAnnotations();
            if (allAnnotations.isEmpty()) {
                return Collections.emptySet();
            }

            TriggerFilter triggerFilter = getTriggerFilter();
            Set<String> annotationTypeTriggers = allAnnotations.stream()
                    .filter(triggerFilter::isQualifyingTrigger)
                    .filter((anno) -> !TriggerFilter.TRIGGER.typeName().name().equals(anno))
                    .collect(Collectors.toSet());
            return annotationTypeTriggers;
        }

        /**
         * Creates the interception plan.
         *
         * @param interceptorAnnotationTriggers the annotation type triggering the interception creation.
         * @return the plan
         */
        public InterceptionPlan createInterceptorPlan(Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> interceptedElements = getInterceptedElements(interceptorAnnotationTriggers);
            if (Objects.isNull(interceptedElements) || interceptedElements.isEmpty()) {
                return null;
            }

            if (!hasNoArgConstructor()) {
                ToolsException te =  new ToolsException("there must be a no-arg constructor for: " + getServiceTypeName());
                LOGGER.log(System.Logger.Level.WARNING, "skipping interception for: " + getServiceTypeName(), te);
                return null;
            }

            Set<AnnotationAndValue> serviceLevelAnnotations = getServiceLevelAnnotations();

            return DefaultInterceptionPlan.builder()
                    .interceptedService(interceptedService)
                    .serviceLevelAnnotations(serviceLevelAnnotations)
                    .annotationTriggerTypeNames(interceptorAnnotationTriggers)
                    .interceptedElements(interceptedElements)
                    .build();
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
            assert (ElementInfo.ElementKind.CTOR == kind || InjectionPointInfo.ElementKind.METHOD == kind)
                    : kind + " in:" + getServiceTypeName();

            if (Objects.nonNull(modifiers)) {
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

            if (ElementInfo.ElementKind.CTOR == kind && methodArgCount != 0) {
                return false;
            }

            return true;
        }
    }

    private static class ProcessorBased extends InterceptorProcessor {
        private final TypeElement serviceTypeElement;
        private final ProcessingEnvironment processEnv;

        ProcessorBased(ServiceInfoBasics interceptedService, InterceptorCreator realCreator, ProcessingEnvironment processEnv) {
            super(interceptedService, realCreator, createResolverFromProcessor(processEnv));
            this.serviceTypeElement = Objects
                    .requireNonNull(processEnv.getElementUtils().getTypeElement(getServiceTypeName()));
            this.processEnv = processEnv;
        }

        @Override
        public Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(serviceTypeElement, processEnv);
            return set.stream().map((a) -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return TypeTools.createAnnotationAndValueSet(serviceTypeElement);
        }

        @Override
        public boolean hasNoArgConstructor() {
            return serviceTypeElement.getEnclosedElements().stream()
                    .filter((it) -> it.getKind().equals(ElementKind.CONSTRUCTOR))
                    .map(ExecutableElement.class::cast)
                    .anyMatch((it) -> it.getParameters().isEmpty());
        }

        @Override
        protected List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new LinkedList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            serviceTypeElement.getEnclosedElements().stream()
                    .filter((e) -> e.getKind() == ElementKind.METHOD || e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .filter((e) -> isProcessed(toKind(e), e.getParameters().size(), e.getModifiers(), null, null))
                    .forEach((ee) -> result.add(create(ee, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        private InterceptedElement create(ExecutableElement ee,
                                          Set<AnnotationAndValue> serviceLevelAnnos,
                                          Set<String> interceptorAnnotationTriggers) {
            DefaultMethodInfo elementInfo = createMethodInfo(serviceTypeElement, ee, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map((a) -> a.typeName().name()).collect(Collectors.toSet()));
            DefaultInterceptedElement result = DefaultInterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
            return result;
        }
    }

    private static class ReflectionBased extends InterceptorProcessor {
        private final ClassInfo classInfo;

        ReflectionBased(ServiceInfoBasics interceptedService, InterceptorCreator realCreator, ClassInfo classInfo) {
            super(/*serviceTypeName,*/ interceptedService, realCreator, createResolverFromReflection());
            this.classInfo = classInfo;
        }

        @Override
        public Set<String> getAllAnnotations() {
            Set<AnnotationAndValue> set = gatherAllAnnotationsUsedOnPublicNonStaticMethods(classInfo);
            return set.stream().map((a) -> a.typeName().name()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<AnnotationAndValue> getServiceLevelAnnotations() {
            return TypeTools.createAnnotationAndValueSet(classInfo);
        }

        @Override
        public boolean hasNoArgConstructor() {
            return classInfo.getConstructorInfo().stream()
                    .filter((mi) -> !mi.isPrivate())
                    .anyMatch((mi) -> mi.getParameterInfo().length == 0);
        }

        @Override
        protected List<InterceptedElement> getInterceptedElements(Set<String> interceptorAnnotationTriggers) {
            List<InterceptedElement> result = new LinkedList<>();
            Set<AnnotationAndValue> serviceLevelAnnos = getServiceLevelAnnotations();
            classInfo.getMethodAndConstructorInfo()
                    .filter((m) -> isProcessed(TypeTools.toKind(m),
                                               m.getParameterInfo().length, null, m.isPrivate(), m.isStatic()))
                    .filter((m) -> containsAny(serviceLevelAnnos, interceptorAnnotationTriggers)
                                    || containsAny(createAnnotationAndValueSet(
                                            m.getAnnotationInfo()), interceptorAnnotationTriggers))
                    .forEach((mi) -> result.add(create(mi, serviceLevelAnnos, interceptorAnnotationTriggers)));
            return result;
        }

        private InterceptedElement create(MethodInfo mi,
                                          Set<AnnotationAndValue> serviceLevelAnnos,
                                          Set<String> interceptorAnnotationTriggers) {
            DefaultMethodInfo elementInfo = createMethodInfo(mi, serviceLevelAnnos);
            Set<String> applicableTriggers = new LinkedHashSet<>(interceptorAnnotationTriggers);
            applicableTriggers.retainAll(elementInfo.annotations().stream()
                                                 .map((a) -> a.typeName().name()).collect(Collectors.toSet()));
            DefaultInterceptedElement result = DefaultInterceptedElement.builder()
                    .interceptedTriggerTypeNames(applicableTriggers)
                    .elementInfo(elementInfo)
                    .build();
            return result;
        }
    }

    /**
     * Create an annotation resolver based on annotation processing.
     *
     * @param processEnv the processing env
     * @return the {@link AnnotationTypeNameResolver} to use.
     */
    public static AnnotationTypeNameResolver createResolverFromProcessor(ProcessingEnvironment processEnv) {
        return new ProcessorResolver(processEnv.getElementUtils());
    }

    /**
     * Create an annotation resolver based on reflective processing.
     *
     * @return the {@link AnnotationTypeNameResolver} to use.
     */
    public static AnnotationTypeNameResolver createResolverFromReflection() {
        return new ReflectionResolver(SCAN.get());
    }

    /**
     * Returns the processor appropriate for the context revealed in the calling arguments, favoring reflection if
     * the serviceTypeElement is provided.
     *
     * @param interceptedService    the service being intercepted
     * @param realCreator           the "real" creator
     * @param processEnv            optionally, the processing environment (should be passed if in annotation processor)
     * @return the processor to use for the given arguments.
     */
    public static InterceptorProcessor createInterceptorProcessor(ServiceInfoBasics interceptedService,
                                                                  InterceptorCreator realCreator,
                                                                  ProcessingEnvironment processEnv) {
        if (Objects.nonNull(processEnv)) {
            return createInterceptorProcessorFromProcessor(interceptedService, realCreator, processEnv);
        }
        return createInterceptorProcessorFromReflection(interceptedService, realCreator);
    }


    /**
     * Create an interceptor processor based on annotation processing.
     *
     * @param interceptedService the service being processed
     * @param realCreator     the real/delegate creator
     * @param processEnv the processing env
     * @return the {@link InterceptorProcessor} to use.
     */
    public static InterceptorProcessor createInterceptorProcessorFromProcessor(ServiceInfoBasics interceptedService,
                                                                               InterceptorCreator realCreator,
                                                                               ProcessingEnvironment processEnv) {
        Options.init(processEnv);
        if (Objects.isNull(manuallyWhiteListed)) {
            manuallyWhiteListed = Options.getOptionStringList(Options.TAG_WHITE_LISTED_INTERCEPTOR_ANNOTATIONS);
        }
        return new ProcessorBased(Objects.requireNonNull(interceptedService),
                                  Objects.requireNonNull(realCreator),
                                  Objects.requireNonNull(processEnv));
    }

    /**
     * Create an interceptor processor based on reflection processing.
     *
     * @param interceptedService the service being processed
     * @param realCreator     the real/delegate creator
     * @return the {@link InterceptorProcessor} to use.
     */
    public static InterceptorProcessor createInterceptorProcessorFromReflection(ServiceInfoBasics interceptedService,
                                                                                InterceptorCreator realCreator) {
        return new ReflectionBased(Objects.requireNonNull(interceptedService),
                                   Objects.requireNonNull(realCreator),
                                   Objects.requireNonNull(SCAN.get().getClassInfo(interceptedService.serviceTypeName())));
    }

    /**
     * Creates the interceptor source code type name given its plan.
     *
     * @param plan the plan
     * @return the the interceptor type name
     */
    public static TypeName createInterceptorSourceTypeName(InterceptionPlan plan) {
        String parent = plan.getInterceptedService().serviceTypeName();
        return toInterceptorTypeName(parent);
    }

    /**
     * Creates the source code associated with an interception plan.
     *
     * @param plan the plan
     * @return the java source code body
     */
    public static String createInterceptorSourceBody(InterceptionPlan plan) {
        String parent = plan.getInterceptedService().serviceTypeName();
        TypeName interceptorTypeName = toInterceptorTypeName(parent);
        Map<String, Object> subst = new HashMap<>();
        subst.put("packageName", interceptorTypeName.packageName());
        subst.put("className", interceptorTypeName.className());
        subst.put("parent", parent);
        subst.put("generatedanno", getGeneratedSticker());
        subst.put("weight", interceptorWeight(plan.getInterceptedService().getWeight()));
        subst.put("interceptedmethoddecls", toInterceptedMethodDecls(plan));
        subst.put("interceptedmethods", IdAndToString
                .toList(plan.getInterceptedElements(), DefaultInterceptorCreator::toBody).stream()
                .filter((it) -> !it.getId().equals(CTOR_ALIAS))
                .collect(Collectors.toList()));
        subst.put("annotationtriggertypenames", IdAndToString.toList(plan.getAnnotationTriggerTypeNames(),
                                             (str) -> new IdAndToString(str.replace(".", "_"), str)));
        subst.put("servicelevelannotations", IdAndToString
                .toList(plan.getServiceLevelAnnotations(), DefaultInterceptorCreator::toDecl));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String template = TemplateHelper.safeLoadTemplate(COMPLEX_INTERCEPTOR_HBS);
        return TemplateHelper.applySubstitutions(ps, template, subst).trim();
    }

    private static List<IdAndToString> toInterceptedMethodDecls(InterceptionPlan plan) {
        List<IdAndToString> result = new LinkedList<>();
        for (InterceptedElement element : plan.getInterceptedElements()) {
            IdAndToString methodTypedElement = toDecl(element);
            result.add(methodTypedElement);

            if (element.getElementInfo().elementKind() == ElementInfo.ElementKind.CTOR) {
                continue;
            }

            for (ElementInfo param : element.getElementInfo().getParameterInfo()) {
                IdAndToString paramTypedElement = new IdAndToString(element.getElementInfo().elementName()
                                                                            + "__" + param.elementName(),
                                                                    typeNameElementNameAnnotations(param));
                result.add(paramTypedElement);
            }
        }
        return result;
    }

    private static IdAndToString toDecl(InterceptedElement method) {
        io.helidon.pico.tools.creator.MethodInfo mi = method.getElementInfo();
        String name = (mi.elementKind() == ElementInfo.ElementKind.CTOR) ? CTOR_ALIAS : mi.elementName();
        String builder = typeNameElementNameAnnotations(mi);
        return new IdAndToString(name, builder);
    }

    private static String typeNameElementNameAnnotations(ElementInfo ei) {
        String builder = ".typeName(create(" + ei.elementTypeName() + ".class))";
        builder += "\n\t\t\t.elementName(\"" + ei.elementName() + "\")";
        for (AnnotationAndValue anno : ei.annotations()) {
            builder += "\n\t\t\t.annotation(" + toDecl(anno) + ")";
        }
        return builder;
    }

    private static IdAndToString toDecl(ElementInfo elementInfo) {
        String name = elementInfo.elementName();
        return new IdAndToString(name, elementInfo.elementTypeName() + " " + name);
    }

    private static IdAndToString toDecl(AnnotationAndValue anno) {
        StringBuilder builder = new StringBuilder("DefaultAnnotationAndValue.create(" + anno.typeName() + ".class");
        Map<String, String> map = anno.values();
        String val = anno.value().orElse(null);
        if (Objects.nonNull(map) && !map.isEmpty()) {
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
        } else if (Objects.nonNull(val)) {
            builder.append(", \"")
                    .append(val)
                    .append("\"");
        }
        builder.append(")");
        return new IdAndToString(anno.typeName().name(), builder);
    }

    @SuppressWarnings("checkstyle:OperatorWrap")
    private static IdAndToString toBody(InterceptedElement method) {
        io.helidon.pico.tools.creator.MethodInfo mi = method.getElementInfo();
        String name = (mi.elementKind() == ElementInfo.ElementKind.CTOR) ? CTOR_ALIAS : mi.elementName();
        StringBuilder builder = new StringBuilder();
        builder.append("public ").append(mi.elementTypeName()).append(" ").append(mi.elementName()).append("(");
        String args = CommonUtils.toString(mi.getParameterInfo().stream().map(ElementInfo::elementName)
                                     .collect(Collectors.toList()));
        String argDecls = "";
        String objArrayArgs = "";
        String typedElementArgs = "";
        boolean hasArgs = (args.length() > 0);
        if (hasArgs) {
            argDecls = CommonUtils.toString(IdAndToString.toList(mi.getParameterInfo(), DefaultInterceptorCreator::toDecl));
            AtomicInteger count = new AtomicInteger();
            objArrayArgs = CommonUtils.toString(mi.getParameterInfo().stream()
                                       .map((ei) -> ("(" + TypeTools.toObjectTypeName(ei.elementTypeName())) + ") "
                                               + "args[" + count.getAndIncrement() + "]")
                                       .collect(Collectors.toList()));
            typedElementArgs = CommonUtils.toString(mi.getParameterInfo().stream()
                                       .map((ei) -> "__" + mi.elementName() + "__" + ei.elementName())
                                       .collect(Collectors.toList()), null, ",\n\t\t\t");
        }
        boolean hasReturn = !mi.elementTypeName().equals(void.class.getName());
        builder.append(argDecls);
        builder.append(")");
        if (!mi.getThrowableTypeNames().isEmpty()) {
            builder.append(" throws ").append(CommonUtils.toString(mi.getThrowableTypeNames()));
        }
        builder.append(" {\n");
        if (hasArgs) {
            builder.append("\t\tObject[] args = new Object[] {" + args + "};\n");
        }
        if (hasReturn) {
            TypeName supplierType = TypeTools.toObjectTypeName(mi.elementTypeName());
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
        return new InterceptedMethodCodeGen(name, method.getInterceptedTriggerTypeNames(), builder);
    }

    private static TypeName toInterceptorTypeName(String serviceTypeName) {
        TypeName typeName = DefaultTypeName.createFromTypeName(serviceTypeName);
        return DefaultTypeName.create(typeName.packageName(), typeName.className()
                                                                      + INNER_INTERCEPTOR_CLASS_NAME);
    }

    private static Object getGeneratedSticker() {
        return TemplateHelper.getDefaultGeneratedSticker(DefaultInterceptorCreator.class.getName());
    }

    /**
     * Assign a weight slightly higher than the service weight passed in.
     *
     * @param serviceWeight the service weight, where null defaults to {@link io.helidon.common.Weighted#DEFAULT_WEIGHT}.
     * @return the higher weighted value appropriate for interceptors
     */
    public static double interceptorWeight(Double serviceWeight) {
        serviceWeight = Objects.isNull(serviceWeight) ? Weighted.DEFAULT_WEIGHT : serviceWeight;
        return serviceWeight + INTERCEPTOR_PRIORITY_DELTA;
    }


    static class InterceptedMethodCodeGen extends IdAndToString {
        private final String interceptedTriggerTypeNames;

        InterceptedMethodCodeGen(String id, Collection<String> interceptedTriggerTypeNames, Object toString) {
            super(id, toString);
            this.interceptedTriggerTypeNames = CommonUtils.toString(interceptedTriggerTypeNames,
                                                                (str) -> str.replace(".", "_"), null);
        }

        public String getInterceptedTriggerTypeNames() {
            return interceptedTriggerTypeNames;
        }
    }

}
