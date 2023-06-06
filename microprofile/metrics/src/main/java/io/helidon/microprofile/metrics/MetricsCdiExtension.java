/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.Errors;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.serviceapi.MetricsSupport;
import io.helidon.microprofile.metrics.MetricAnnotationInfo.RegistrationPrep;
import io.helidon.microprofile.metrics.MetricUtil.LookupResult;
import io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver;
import io.helidon.microprofile.metrics.spi.MetricRegistrationObserver;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.servicecommon.restcdi.HelidonRestCdiExtension;
import io.helidon.webserver.Routing;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * MetricsCdiExtension class.
 *
 * <p>
 *     Earlier versions of this class detected app-provided producer fields and methods and triggered creation and registration
 *     of the corresponding metrics upon such detection. As explained in this
 *     <a href="https://github.com/eclipse/microprofile-metrics/issues/456">MP metrics issue</a>
 *     and this <a href="https://github.com/eclipse/microprofile-metrics/pull/594">MP metrics PR</a>,
 *     this probably was never correct and does not work because {@code @Metric} no longer applies to producers per the
 *     MP metrics 3.0 spec. The issue and PR discussion explain how developers who provide their own producers should use
 *     CDI qualifiers on the producers (and, therefore, injection points) to avoid ambiguity between their own producers and
 *     producers written by vendors implementing MP metrics.
 *
 *     For Helidon, this means we no longer need to track producer fields and methods, nor do we need to augment injection points
 *     with our own {@code VendorProvided} qualifier to disambiguate, because we now rely on developers who write their own
 *     producers to avoid the ambiguity using qualifiers.
 * </p>
 */
public class MetricsCdiExtension extends HelidonRestCdiExtension<MetricsSupport> {

    private static final Logger LOGGER = Logger.getLogger(MetricsCdiExtension.class.getName());

    static final Set<Class<? extends Annotation>> ALL_METRIC_ANNOTATIONS = Set.of(
            Counted.class, Metered.class, Timed.class, ConcurrentGauge.class, SimplyTimed.class, Gauge.class);

    private static final Map<Class<? extends Annotation>, AnnotationLiteral<?>> INTERCEPTED_METRIC_ANNOTATIONS =
            Map.of(
                    Counted.class, InterceptorCounted.binding(),
                    Metered.class, InterceptorMetered.binding(),
                    Timed.class, InterceptorTimed.binding(),
                    ConcurrentGauge.class, InterceptorConcurrentGauge.binding(),
                    SimplyTimed.class, InterceptorSimplyTimed.binding());

    private static final List<Class<? extends Annotation>> JAX_RS_ANNOTATIONS
            = Arrays.asList(GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class);

    private static final Set<Class<? extends Annotation>> METRIC_ANNOTATIONS_ON_ANY_ELEMENT =
            new HashSet<>(ALL_METRIC_ANNOTATIONS) {
                {
                    remove(Gauge.class);
                }
            };


    static final String REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME = "rest-request.enabled";
    private static final boolean REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE = false;

    static final String SYNTHETIC_SIMPLE_TIMER_METRIC_NAME = "REST.request";
    static final String SYNTHETIC_SIMPLE_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME =
            SYNTHETIC_SIMPLE_TIMER_METRIC_NAME + ".unmappedException.total";

    static final Metadata SYNTHETIC_SIMPLE_TIMER_METADATA = Metadata.builder()
            .withName(SYNTHETIC_SIMPLE_TIMER_METRIC_NAME)
            .withDisplayName("Total Requests and Response Time")
            .withDescription("""
                                     The number of invocations and total response time of this RESTful resource method since the \
                                     start of the server. The metric will not record the elapsed time nor count of a REST \
                                     request if it resulted in an unmapped exception. Also tracks the highest recorded time \
                                     duration within the previous completed full minute and lowest recorded time duration within \
                                     the previous completed full minute.""")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.NANOSECONDS)
            .build();

    static final Metadata SYNTHETIC_SIMPLE_TIMER_UNMAPPED_EXCEPTION_METADATA = Metadata.builder()
            .withName(SYNTHETIC_SIMPLE_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME)
            .withDisplayName("Total Unmapped Exceptions count")
            .withDescription("""
                                     The total number of unmapped exceptions that occur from this RESTful resouce method since \
                                     the start of the server.""")
            .withType(MetricType.COUNTER)
            .withUnit(MetricUnits.NONE)
            .build();

    private boolean restEndpointsMetricsEnabled = REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE;

    private final Map<MetricID, AnnotatedMethod<?>> annotatedGaugeSites = new HashMap<>();
    private final List<RegistrationPrep> annotatedSites = new ArrayList<>();

    private Errors.Collector errors = Errors.collector();

    private final Map<Class<?>, Set<Method>> methodsWithRestRequestMetrics = new HashMap<>();
    private final Set<Class<?>> restRequestMetricsClassesProcessed = new HashSet<>();
    private final Set<Method> restRequestMetricsToRegister = new HashSet<>();

    private final WorkItemsManager<MetricWorkItem> workItemsManager = WorkItemsManager.create();

    private final List<MetricAnnotationDiscoveryObserver> metricAnnotationDiscoveryObservers = new ArrayList<>();
    private final List<MetricRegistrationObserver> metricRegistrationObservers = new ArrayList<>();

    private final Map<Executable, List<MetricAnnotationDiscovery>> metricAnnotationDiscoveriesByExecutable = new HashMap<>();

    @SuppressWarnings("unchecked")

    // records stereotype annotations which have metrics annotations inside them
    private final Map<Class<?>, StereotypeMetricsInfo> stereotypeMetricsInfo = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T getReference(BeanManager bm, Type type, Bean<?> bean) {
        return (T) bm.getReference(bean, type, bm.createCreationalContext(bean));
    }

    /**
     * Creates a new extension instance.
     */
    public MetricsCdiExtension() {
        super(LOGGER, MetricsSupport::create, "metrics");
    }

    /**
     * Records an observer of metric annotation discoveries.
     *
     * @param metricAnnotationDiscoveryObserver the observer to enroll
     */
    public void enroll(MetricAnnotationDiscoveryObserver metricAnnotationDiscoveryObserver) {
        metricAnnotationDiscoveryObservers.add(metricAnnotationDiscoveryObserver);
    }

    /**
     * Records an observer of metric registrations.
     *
     * @param metricRegistrationObserver the observer to enroll
     */
    public void enroll(MetricRegistrationObserver metricRegistrationObserver) {
        metricRegistrationObservers.add(metricRegistrationObserver);
    }

    private static <E extends Member & AnnotatedElement> void recordAnnotatedSite(
            List<RegistrationPrep> sites,
            E element,
            Class<?> annotatedClass,
            LookupResult<? extends Annotation> lookupResult,
            Executable executable) {

        Annotation annotation = lookupResult.getAnnotation();
        RegistrationPrep registrationPrep = RegistrationPrep
                .create(annotation, element, annotatedClass, lookupResult.getType(), executable);
        sites.add(registrationPrep);
    }

    private void registerMetricsForAnnotatedSites() {
        MetricRegistry registry = getMetricRegistry();
        for (RegistrationPrep registrationPrep : annotatedSites) {
            metricAnnotationDiscoveriesByExecutable.get(registrationPrep.executable())
                    .forEach(discovery -> {
                        if (discovery.isActive()) { // All annotation discovery observers agreed to preserve the discovery.
                            org.eclipse.microprofile.metrics.Metric metric = registrationPrep.register(registry);
                            MetricID metricID = new MetricID(registrationPrep.metricName(), registrationPrep.tags());
                            metricRegistrationObservers.forEach(
                                    o -> o.onRegistration(discovery, registrationPrep.metadata(), metricID, metric));
                            workItemsManager.put(registrationPrep.executable(), registrationPrep.annotationType(),
                                                 BasicMetricWorkItem
                                                         .create(new MetricID(registrationPrep.metricName(),
                                                                              registrationPrep.tags()),
                                                                 metric));
                        }
                    });
        }
        annotatedSites.clear();
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> pmb) {

        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        // Check for Interceptor. We have already checked developer-provided beans, but other extensions might have supplied
        // additional beans that we have not checked yet.
        if (type.isAnnotationPresent(Interceptor.class)) {
            LOGGER.log(Level.FINE, "Ignoring objects defined on type " + clazz.getName()
                    + " because a CDI portable extension added @Interceptor to it dynamically");
            return;
        }

        Stream.concat(type.getMethods().stream(), type.getConstructors().stream())
                .filter(annotatedCallable -> !Modifier.isPrivate(annotatedCallable.getJavaMember().getModifiers()))
                .filter(annotatedCallable -> type.equals(annotatedCallable.getDeclaringType()))
                .forEach(annotatedCallable ->
                                 METRIC_ANNOTATIONS_ON_ANY_ELEMENT // all except gauges; they are handled elsewhere
                                         .forEach(annotation ->
                                                          MetricUtil.lookupAnnotations(type,
                                                                                       annotatedCallable,
                                                                                       annotation,
                                                                                       stereotypeMetricsInfo)
                                                                  .forEach(lookupResult -> {
                                                                      Executable executable = (Executable) annotatedCallable
                                                                              .getJavaMember();
                                                                      recordAnnotatedSite(annotatedSites,
                                                                                          executable,
                                                                                          clazz,
                                                                                          lookupResult,
                                                                                          executable);
                                                                  })));

    }

    private static Tag[] tags(String[] tagStrings) {
        final List<Tag> result = new ArrayList<>();
        for (int i = 0; i < tagStrings.length; i++) {
            final int eq = tagStrings[i].indexOf("=");
            if (eq > 0) {
                final String tagName = tagStrings[i].substring(0, eq);
                final String tagValue = tagStrings[i].substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[0]);
    }

    Iterable<MetricWorkItem> workItems(Executable executable, Class<? extends Annotation> annotationType) {
        return workItemsManager.workItems(executable, annotationType);
    }

    <S extends MetricWorkItem> Iterable<S> workItems(Executable executable,
                                                     Class<? extends Annotation> annotationType,
                                                     Class<S> sClass) {
        return TypeFilteredIterable.create(workItems(executable, annotationType), sClass);
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    static Class<?> getRealClass(Object object) {
        Class<?> result = object.getClass();
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    static MetricRegistry getMetricRegistry() {
        return RegistryProducer.getDefaultRegistry();
    }

    static MetricRegistry getRegistryForSyntheticRestRequestMetrics() {
        return RegistryProducer.getBaseRegistry();
    }

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.FINE, () -> "Before bean discovery " + discovery);

        // Register beans manually with annotated type identifiers that are deliberately the same as those used by the container
        // during bean discovery to avoid accidental duplicate registration in odd packaging scenarios.
        discovery.addAnnotatedType(RegistryProducer.class, RegistryProducer.class.getName());
        discovery.addAnnotatedType(MetricProducer.class, MetricProducer.class.getName());
        discovery.addAnnotatedType(InterceptorCounted.class, InterceptorCounted.class.getName());
        discovery.addAnnotatedType(InterceptorMetered.class, InterceptorMetered.class.getName());
        discovery.addAnnotatedType(InterceptorTimed.class, InterceptorTimed.class.getName());
        discovery.addAnnotatedType(InterceptorConcurrentGauge.class, InterceptorConcurrentGauge.class.getName());
        discovery.addAnnotatedType(InterceptorSimplyTimed.class, InterceptorSimplyTimed.class.getName());

        // Telling CDI about our private SyntheticRestRequest annotation and its interceptor
        // is enough for CDI to intercept invocations of methods so annotated.
        discovery.addAnnotatedType(InterceptorSyntheticRestRequest.class, InterceptorSyntheticRestRequest.class.getName());
        discovery.addAnnotatedType(SyntheticRestRequest.class, SyntheticRestRequest.class.getName());

        restEndpointsMetricsEnabled = restEndpointsMetricsEnabled();
    }

    @Override
    public void clearAnnotationInfo(@Observes AfterDeploymentValidation adv) {
        super.clearAnnotationInfo(adv);
        methodsWithRestRequestMetrics.clear();
    }

    /**
     * Records Java classes with a metrics annotation somewhere.
     *
     * By recording the classes here, we let CDI optimize its invocations of this observer method. Later, when we
     * observe managed beans (which CDI invokes for all managed beans) where we also have to examine each method and
     * constructor, we can quickly eliminate from consideration any classes we have not recorded here.
     *
     * This observer runs after other {@code ProcessAnnotatedType} observers to give other extensions a chance to provide their
     * own interceptors for selected constructors and methods by adding {@link MetricAnnotationDiscoveryObserver}
     * to the configured type, constructor, or method.
     *
     * @param pat ProcessAnnotatedType event
     */
    private void recordMetricAnnotatedClass(@Observes @Priority(Interceptor.Priority.APPLICATION + 500 + 10)
                                            @WithAnnotations({Counted.class,
                                                    Metered.class,
                                                    Timed.class,
                                                    ConcurrentGauge.class,
                                                    SimplyTimed.class}) ProcessAnnotatedType<?> pat) {
        if (isConcreteNonInterceptor(pat)) {
            recordAnnotatedType(pat);
            recordStereotypes(pat);

            /*
                Wherever metrics annotations appear--at the type, at each constructor, and at each method--record those sites
                and, if the annotation discovery observers concur, set up the normal metrics interceptors as needed.

                We need to visit all the possible sites now so we can add the private interceptor-bound annotations as
                appropriate.

            */
            bindInterceptors(pat);
        }
    }

    private void bindInterceptors(ProcessAnnotatedType<?> pat) {

        // Sadly, the various AnnotatedXXXConfigurator types do not share a common supertype, so we deal with each individually
        // using plenty of method references.

        AnnotatedTypeConfigurator<?> annotatedTypeConfigurator = pat.configureAnnotatedType();

        bindInterceptorsAndRecordDiscoveries(annotatedTypeConfigurator,
                                             pat.configureAnnotatedType().constructors(),
                                             AnnotatedConstructorConfigurator::getAnnotated,
                                             AnnotatedConstructorConfigurator::add);

        bindInterceptorsAndRecordDiscoveries(annotatedTypeConfigurator,
                                             pat.configureAnnotatedType().methods(),
                                             AnnotatedMethodConfigurator::getAnnotated,
                                             (BiFunction<AnnotatedMethodConfigurator<?>, Annotation,
                                                       AnnotatedMethodConfigurator<?>>) AnnotatedMethodConfigurator::add);
    }

    private <T, C, A extends AnnotatedCallable<?>> void bindInterceptorsAndRecordDiscoveries(
            AnnotatedTypeConfigurator<T> annotatedTypeConfigurator,
            Iterable<? extends C> executableConfigurators,
            Function<C, A> configuratorAnnotatedGetter,
            BiFunction<C, Annotation, C> annotationAdder) {
        executableConfigurators.forEach(executableConfigurator -> {
            // Process all metric annotations which apply to this executable, either from the type-level or from this
            // executable itself.
            A annotatedCallable = configuratorAnnotatedGetter.apply(executableConfigurator);
            Executable exec = (annotatedCallable.getJavaMember() instanceof Executable)
                    ? (Executable) annotatedCallable.getJavaMember()
                    : null;
            metricsLookupResults(annotatedTypeConfigurator.getAnnotated(),
                                 annotatedCallable)
                    .forEach(lookupResult -> {
                        MetricAnnotationDiscoveryBase discoveryEvent = MetricAnnotationDiscoveryBase.create(
                                annotatedTypeConfigurator,
                                executableConfigurator,
                                lookupResult.getAnnotation());
                        if (exec != null) {
                            metricAnnotationDiscoveriesByExecutable.computeIfAbsent(exec, o -> new ArrayList<>())
                                    .add(discoveryEvent);
                            metricAnnotationDiscoveryObservers.forEach(observer -> observer.onDiscovery(discoveryEvent));
                        }
                        if (discoveryEvent.shouldUseDefaultInterceptor()) {
                            Class<? extends Annotation> metricAnnotationClass = lookupResult.getAnnotation().annotationType();
                            annotationAdder.apply(executableConfigurator,
                                                  INTERCEPTED_METRIC_ANNOTATIONS.get(metricAnnotationClass));
                        }
                    });
        });
    }

    private void recordStereotypes(ProcessAnnotatedType<?> pat) {
        AnnotatedType<?> annotatedType = pat.getAnnotatedType();
        // Find and record stereotypes applied to the type or its members which themselves carry metrics annotations.
        Stream.concat(Stream.of(annotatedType),
                      Stream.concat(pat.getAnnotatedType().getMethods().stream(),
                                    Stream.concat(pat.getAnnotatedType().getConstructors().stream(),
                                                  pat.getAnnotatedType().getFields().stream())))
                .map(Annotated::getAnnotations)
                .flatMap(Set::stream)
                .distinct()
                .filter(MetricsCdiExtension::isStereotype)
                .forEach(this::recordIfMetricsRelatedStereotype);
    }

    private static boolean isStereotype(Annotation annotation) {
        return annotation.annotationType().isAnnotationPresent(Stereotype.class);
    }

    private void recordIfMetricsRelatedStereotype(Annotation stereotypeAnnotation) {
        Class<? extends Annotation> candidateType = stereotypeAnnotation.annotationType();
        Set<Annotation> metricsRelatedAnnotations = Arrays.stream(candidateType.getAnnotations())
                .filter(a -> ALL_METRIC_ANNOTATIONS.contains(a.annotationType()))
                .collect(Collectors.toSet());

        if (!metricsRelatedAnnotations.isEmpty()) {
            stereotypeMetricsInfo.put(candidateType, StereotypeMetricsInfo.create(metricsRelatedAnnotations));
        }
    }

    /**
     * Collects all {@code LookupResult} objects for metrics annotations on a given annotated executable.
     *
     * @param annotatedType the annotated type containing the constructor or method
     * @param annotatedMember the constructor or method
     * @return {@code LookupResult} instances that apply to the executable
     */
    private Iterable<LookupResult<?>> metricsLookupResults(AnnotatedType<?> annotatedType,
                                                           AnnotatedCallable<?> annotatedMember) {
        List<LookupResult<?>> result = new ArrayList<>();
        INTERCEPTED_METRIC_ANNOTATIONS.keySet().forEach(metricAnnotationClass -> {
            result.addAll(MetricUtil.lookupAnnotations(annotatedType,
                                                       annotatedMember,
                                                       metricAnnotationClass,
                                                       stereotypeMetricsInfo));
        });
        return result;
    }

//    private Set<Class<? extends Annotation>> metricsAnnotationClasses(Annotated annotated) {
//        return annotated
//                .getAnnotations()
//                .stream()
//                .map(Annotation::annotationType)
//                .filter(METRIC_ANNOTATIONS::containsKey)
//                .collect(Collectors.toSet());
//    }

    /**
     * Checks to make sure the annotated type is not abstract and is not an interceptor.
     *
     * @param pat {@code ProcessAnnotatedType} event
     * @return true if the annotated type should be kept for potential processing later; false otherwise
     */
    private boolean checkCandidateMetricClass(ProcessAnnotatedType<?> pat) {
        AnnotatedType<?> annotatedType = pat.getAnnotatedType();
        Class<?> clazz = annotatedType.getJavaClass();

        // Abstract classes are handled when we deal with a concrete subclass. Also, ignore if @Interceptor is present.
        if (annotatedType.isAnnotationPresent(Interceptor.class)
                || Modifier.isAbstract(clazz.getModifiers())) {
            LOGGER.log(Level.FINER, () -> "Ignoring " + clazz.getName()
                    + " with annotations " + annotatedType.getAnnotations()
                    + " for later processing: "
                    + (Modifier.isAbstract(clazz.getModifiers()) ? "abstract " : "")
                    + (annotatedType.isAnnotationPresent(Interceptor.class) ? "interceptor " : ""));
            return false;
        }
        LOGGER.log(Level.FINE, () -> "Accepting annotated type " + clazz.getName() + " for later bean processing");
        return true;
    }

    /**
     * Adds a {@code SyntheticRestRequest} annotation to each JAX-RS endpoint method.
     *
     * @param pat the {@code ProcessAnnotatedType} for the type containing the JAX-RS annotated methods
     */
    private void recordSimplyTimedForRestResources(@Observes
                                                   @WithAnnotations({GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class,
                                                           DELETE.class, PATCH.class})
                                                           ProcessAnnotatedType<?> pat) {

        /// Ignore abstract classes or interceptors. Make sure synthetic SimpleTimer creation is enabled, and if so record the
        // class and JAX-RS methods to use in later bean processing.
        if (!checkCandidateMetricClass(pat)
                || !restEndpointsMetricsEnabled) {
            return;
        }

        LOGGER.log(Level.FINE,
                () -> "Processing @SyntheticRestRequest annotation for " + pat.getAnnotatedType()
                        .getJavaClass()
                        .getName());

        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated()
                .getJavaClass();

        Set<Method> methodsToRecord = new HashSet<>();

        // Process methods keeping non-private declared on this class
        configurator.filterMethods(method -> !Modifier.isPrivate(method.getJavaMember()
                                                                         .getModifiers()))
                .forEach(annotatedMethodConfigurator ->
                        JAX_RS_ANNOTATIONS.forEach(jaxRsAnnotation -> {
                            AnnotatedMethod<?> annotatedMethod = annotatedMethodConfigurator.getAnnotated();
                            if (annotatedMethod.isAnnotationPresent(jaxRsAnnotation)) {
                                Method m = annotatedMethod.getJavaMember();
                                // For methods, add the SyntheticRestRequest annotation only on the declaring
                                // class, not subclasses.
                                if (clazz.equals(m.getDeclaringClass())) {

                                    LOGGER.log(Level.FINE, () -> String.format("Adding @SyntheticRestRequest to %s",
                                            m.toString()));
                                    annotatedMethodConfigurator.add(SyntheticRestRequest.Literal.getInstance());
                                    methodsToRecord.add(m);
                                }
                            }
                        }));
        if (!methodsToRecord.isEmpty()) {
            methodsWithRestRequestMetrics.put(clazz, methodsToRecord);
        }
    }

    /**
     * Creates or looks up the {@code SimpleTimer} instance for measuring REST requests on any JAX-RS method.
     *
     * @param method the {@code Method} for which the SimpleTimer instance is needed
     * @return the located or created {@code SimpleTimer}
     */
    static SimpleTimer restEndpointSimpleTimer(Method method) {
        // By spec, the synthetic SimpleTimers are always in the base registry.
        LOGGER.log(Level.FINE,
                () -> String.format("Registering synthetic SimpleTimer for %s#%s", method.getDeclaringClass().getName(),
                        method.getName()));
        return getRegistryForSyntheticRestRequestMetrics()
                .simpleTimer(SYNTHETIC_SIMPLE_TIMER_METADATA, syntheticRestRequestMetricTags(method));
    }

    /**
     * Creates or looks up the {@code Counter} instance for measuring REST requests on any JAX-RS method.
     *
     * @param method the {@code Method} for which the Counter instance is needed
     * @return the located or created {@code Counter}
     */
    static Counter restEndpointCounter(Method method) {
        LOGGER.log(Level.FINE,
                   () -> String.format("Registering synthetic Counter for %s#%s", method.getDeclaringClass().getName(),
                                       method.getName()));
        return getRegistryForSyntheticRestRequestMetrics()
                .counter(SYNTHETIC_SIMPLE_TIMER_UNMAPPED_EXCEPTION_METADATA, syntheticRestRequestMetricTags(method));
    }

    private void registerAndSaveRestRequestMetrics(Method method) {
        workItemsManager.put(method, SyntheticRestRequest.class,
                             SyntheticRestRequestWorkItem.create(restEndpointSimpleTimerMetricID(method),
                                                                 restEndpointSimpleTimer(method),
                                                                 restEndpointCounterMetricID(method),
                                                                 restEndpointCounter(method)));
    }

    /**
     * Creates the {@link MetricID} for the synthetic {@link SimplyTimed} metric we add to each JAX-RS method.
     *
     * @param method Java method of interest
     * @return {@code MetricID} for the simpletimer for this Java method
     */
    static MetricID restEndpointSimpleTimerMetricID(Method method) {
        return new MetricID(SYNTHETIC_SIMPLE_TIMER_METRIC_NAME, syntheticRestRequestMetricTags(method));
    }

    /**
     * Creates the {@link MetricID} for the synthetic {@link Counter} metric we add to each JAX-RS method.
     *
     * @param method Java method of interest
     * @return {@code MetricID} for the counter for this Java method
     */
    static MetricID restEndpointCounterMetricID(Method method) {
        return new MetricID(SYNTHETIC_SIMPLE_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME, syntheticRestRequestMetricTags(method));
    }

    /**
     * Returns the {@code Tag} array for a synthetic {@code SimplyTimed} annotation.
     *
     * @param method the Java method of interest
     * @return the {@code Tag}s indicating the class and method
     */
    static Tag[] syntheticRestRequestMetricTags(Method method) {
        return new Tag[] {new Tag("class", method.getDeclaringClass().getName()),
                new Tag("method", methodTagValueForSyntheticRestRequestMetric(method))};
    }

    private static String methodTagValueForSyntheticRestRequestMetric(Method method) {
        StringBuilder methodTagValue = new StringBuilder(method.getName());
        for (Parameter p : method.getParameters()) {
            methodTagValue.append("_").append(prettyParamType(p));
        }
        return methodTagValue.toString();
    }

    private static String prettyParamType(Parameter parameter) {
        return parameter.getType().isArray() || parameter.isVarArgs()
                ? parameter.getType().getComponentType().getName() + "[]"
                : parameter.getType().getName();
    }

    private void collectRestRequestMetrics(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!methodsWithRestRequestMetrics.containsKey(clazz)) {
            return;
        }

        LOGGER.log(Level.FINE, () -> "Processing synthetic SimplyTimed annotations for " + clazz.getName());

        restRequestMetricsClassesProcessed.add(clazz);
        restRequestMetricsToRegister.addAll(methodsWithRestRequestMetrics.get(clazz));
    }

    private void registerRestRequestMetrics() {
        restRequestMetricsToRegister.forEach(this::registerAndSaveRestRequestMetrics);
        if (LOGGER.isLoggable(Level.FINE)) {
            Set<Class<?>> syntheticSimpleTimerAnnotatedClassesIgnored = new HashSet<>(methodsWithRestRequestMetrics.keySet());
            syntheticSimpleTimerAnnotatedClassesIgnored.removeAll(restRequestMetricsClassesProcessed);
            if (!syntheticSimpleTimerAnnotatedClassesIgnored.isEmpty()) {
                LOGGER.log(Level.FINE, () ->
                        "Classes with synthetic SimplyTimer annotations added that were not processed, probably "
                                + "because they were vetoed:" + syntheticSimpleTimerAnnotatedClassesIgnored.toString());
            }
        }
        restRequestMetricsClassesProcessed.clear();
        restRequestMetricsToRegister.clear();
    }

    boolean restEndpointsMetricsEnabled() {
        try {
            return chooseRestEndpointsSetting(((Config) (ConfigProvider.getConfig()))
                    .get("metrics"));
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error looking up config setting for enabling REST endpoints SimpleTimer metrics;"
                    + " reporting 'false'", t);
            return false;
        }
    }

    // register metrics with server after security and when
    // application scope is initialized
    @Override
    public Routing.Builder registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                Object adv,
                BeanManager bm,
                ServerCdiExtension server) {

        Errors problems = errors.collect();
        errors = null;
        if (problems.hasFatal()) {
            throw new DeploymentException("Metrics module found issues with deployment: " + problems.toString());
        }

        Routing.Builder defaultRouting = super.registerService(adv, bm, server);
        MetricsSupport metricsSupport = serviceSupport();

        registerMetricsForAnnotatedSites();
        registerAnnotatedGauges(bm);
        registerRestRequestMetrics();

        Set<String> vendorMetricsAdded = new HashSet<>();
        vendorMetricsAdded.add("@default");

        Config config = MpConfig.toHelidonConfig(ConfigProvider.getConfig()).get(MetricsSettings.Builder.METRICS_CONFIG_KEY);

        // now we may have additional sockets we want to add vendor metrics to
        config.get("vendor-metrics-routings")
                .asList(String.class)
                .orElseGet(List::of)
                .forEach(routeName -> {
                    if (!vendorMetricsAdded.contains(routeName)) {
                        metricsSupport.configureVendorMetrics(routeName, server.serverNamedRoutingBuilder(routeName));
                        vendorMetricsAdded.add(routeName);
                    }
                });

        // registry factory is available in global
        Contexts.globalContext().register(RegistryFactory.getInstance());

        return defaultRouting;
    }

    @Override
    protected Config seComponentConfig() {
        // Combine the Helidon-specific "metrics.xxx" settings with the MP
        // "mp.metrics.xxx" settings into a single metrics config object.
        Config mpConfig = MpConfig.toHelidonConfig(ConfigProvider.getConfig());

        Map<String, String> mpConfigSettings = new HashMap<>();
        Stream.of("tags", "appName")
                .forEach(key -> {
                    mpConfig.get("mp.metrics." + key)
                            .asString()
                            .ifPresent(value -> mpConfigSettings.put(key, value));
                });

        Config metricsConfig = mpConfig.get("metrics").detach();

        Config.Builder builder = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();
        if (!mpConfigSettings.isEmpty()) {
            builder.addSource(ConfigSources.create(mpConfigSettings));
        }
        if (metricsConfig.exists()) {
            builder.addSource(ConfigSources.create(metricsConfig));
        }
        return builder.build();
    }

    private static boolean chooseRestEndpointsSetting(Config metricsConfig) {
        ConfigValue<Boolean> explicitRestEndpointsSetting =
                metricsConfig.get(REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME).asBoolean();
        boolean result = explicitRestEndpointsSetting.orElse(REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE);
        if (explicitRestEndpointsSetting.isPresent()) {
            LOGGER.log(Level.FINE, () -> String.format(
                    "Support for MP REST.request metric and annotation handling explicitly set to %b in configuration",
                    explicitRestEndpointsSetting.get()));
        } else {
            LOGGER.log(Level.FINE, () -> String.format(
                    "Support for MP REST.request metric and annotation handling defaulted to %b",
                    REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE));
        }
        return result;
    }

    private void recordAnnotatedGaugeSite(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        LOGGER.log(Level.FINE, () -> "recordAnnotatedGaugeSite for class " + clazz);
        LOGGER.log(Level.FINE, () -> "Processing annotations for " + clazz.getName());

        // Register metrics based on annotations
        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process @Gauge methods keeping non-private declared on this class
        for (AnnotatedMethod<?> method : type.getMethods()) {
            Method javaMethod = method.getJavaMember();
            if (!javaMethod.getDeclaringClass().equals(clazz)
                    || Modifier.isPrivate(javaMethod.getModifiers())) {
                continue;
            }
            MetricUtil.metricsAnnotationsOnElement(method, Gauge.class, stereotypeMetricsInfo).forEach(gaugeAnnotation -> {
                // We have at least one Gauge annotation on the method, so do some checking at the class level.
                Class<? extends Annotation> scopeAnnotation = pmb.getBean().getScope();
                if (scopeAnnotation == RequestScoped.class) {
                    errors.fatal(clazz, "Cannot configure @Gauge on a request scoped bean");
                    return;
                }
                if (scopeAnnotation != ApplicationScoped.class && type.getAnnotation(Singleton.class) == null) {
                    if (ConfigProvider.getConfig().getOptionalValue("metrics.warn-dependent", Boolean.class).orElse(true)) {
                        LOGGER.warning(String.format("""
                                               @Gauge is configured on a bean %s that is neither ApplicationScoped nor \
                                               Singleton. This is most likely a bug. You may set 'metrics.warn-dependent' \
                                               configuration option to 'false' to remove this warning.""", clazz.getName()));
                    }
                }

                String explicitGaugeName = gaugeAnnotation.name();
                String gaugeNameSuffix = (
                        explicitGaugeName.length() > 0 ? explicitGaugeName
                                : javaMethod.getName());
                String gaugeName = (
                        gaugeAnnotation.absolute() ? gaugeNameSuffix
                                : String.format("%s.%s", clazz.getName(), gaugeNameSuffix));
                annotatedGaugeSites.put(new MetricID(gaugeName, tags(gaugeAnnotation.tags())), method);
                LOGGER.log(Level.FINE, () -> String.format("Recorded annotated gauge with name %s", gaugeName));
            });
        }
    }

    private void registerAnnotatedGauges(BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "registerGauges");
        MetricRegistry registry = getMetricRegistry();

        List<Exception> gaugeProblems = new ArrayList<>();

        annotatedGaugeSites.entrySet().forEach(gaugeSite -> {
            LOGGER.log(Level.FINE, () -> "gaugeSite " + gaugeSite.toString());
            MetricID gaugeID = gaugeSite.getKey();

            AnnotatedMethod<?> site = gaugeSite.getValue();
            DelegatingGauge<? extends Number> dg;
            try {
                dg = buildDelegatingGauge(gaugeID.getName(), site,
                                          bm);
                Gauge gaugeAnnotation = siteAnnotation(site, Gauge.class);
                if (gaugeAnnotation == null) {
                    gaugeProblems.add(new IllegalArgumentException(
                               String.format("""
                                             Unable to find expected @Gauge annotation at previously-identified site %s; \
                                             ignoring site""",
                                             site.getJavaMember())));
                } else {
                    Metadata md = Metadata.builder()
                            .withName(gaugeID.getName())
                            .withDisplayName(gaugeAnnotation.displayName())
                            .withDescription(gaugeAnnotation.description())
                            .withType(MetricType.GAUGE)
                            .withUnit(gaugeAnnotation.unit())
                            .build();
                    LOGGER.log(Level.FINE, () -> String.format("Registering gauge with metadata %s", md.toString()));
                    registry.register(md, dg, gaugeID.getTagsAsList().toArray(new Tag[0]));
                }
            } catch (Throwable t) {
                gaugeProblems.add(new IllegalArgumentException(
                        String.format("Error processing @Gauge annotation on %s#%s: %s",
                                      site.getJavaMember().getDeclaringClass().getName(),
                                      site.getJavaMember().getName(),
                                      t.getMessage()),
                        t));
            }
        });

        if (!gaugeProblems.isEmpty()) {
            throw new RuntimeException("Could not process one or more @Gauge annotations" + gaugeProblems);
        }
        annotatedGaugeSites.clear();
    }

    private <T extends Annotation> T siteAnnotation(Annotated site, Class<T> annotationType) {
        Annotation result = site.getAnnotation(annotationType);
        if (result != null) {
            return annotationType.cast(result);
        }
        for (Annotation a : site.getAnnotations()) {
            if (isStereotype(a)) {
                StereotypeMetricsInfo info = stereotypeMetricsInfo.get(a.annotationType());
                if (info != null) {
                    for (Annotation annotationOnStereotype : info.metricsAnnotations()) {
                        if (annotationType.isInstance(annotationOnStereotype)) {
                            return annotationType.cast(annotationOnStereotype);
                        }
                    }
                }
            }
        }
        return null;
    }

    private DelegatingGauge<? extends Number> buildDelegatingGauge(String gaugeName,
                                                                         AnnotatedMethod<?> site, BeanManager bm) {
        Bean<?> bean = bm.getBeans(site.getJavaMember().getDeclaringClass())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find bean for annotated gauge " + gaugeName));

        Class<?> returnType = site.getJavaMember().getReturnType();
        Class<? extends Number> narrowedReturnType = typeToNumber(returnType);

        return DelegatingGauge.newInstance(
                site.getJavaMember(),
                getReference(bm, bean.getBeanClass(), bean),
                narrowedReturnType);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Number> typeToNumber(Class<?> clazz) {
        Class<? extends Number> narrowedReturnType;
        if (byte.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Byte.class;
        } else if (short.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Short.class;
        } else if (int.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Integer.class;
        } else if (long.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Long.class;
        } else if (float.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Float.class;
        } else if (double.class.isAssignableFrom(clazz)) {
            narrowedReturnType = Double.class;
        } else if (Number.class.isAssignableFrom(clazz)) {
            narrowedReturnType = (Class<? extends Number>) clazz;
        } else {
            throw new IllegalArgumentException("Annotated gauge type must extend or be "
                                                       + "assignment-compatible with Number but is " + clazz.getName());
        }
        return narrowedReturnType;
    }

    record StereotypeMetricsInfo(Set<Annotation> metricsAnnotations) {

        static StereotypeMetricsInfo create(Set<Annotation> metricsAnnotations) {
            return new StereotypeMetricsInfo(metricsAnnotations);
        }
    }
}
