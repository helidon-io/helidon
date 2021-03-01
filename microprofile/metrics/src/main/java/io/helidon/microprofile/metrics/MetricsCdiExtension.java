/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.lang.reflect.Constructor;
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
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import io.helidon.common.Errors;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.metrics.MetricsSupport;
import io.helidon.metrics.RegistryFactory;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.microprofile.metrics.MetricUtil.LookupResult;
import static io.helidon.microprofile.metrics.MetricUtil.getMetricName;
import static io.helidon.microprofile.metrics.MetricUtil.lookupAnnotation;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * MetricsCdiExtension class.
 */
public class MetricsCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MetricsCdiExtension.class.getName());

    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS
            = Arrays.asList(Counted.class, Metered.class, Timed.class, ConcurrentGauge.class, SimplyTimed.class);

    private static final List<Class<? extends Annotation>> JAX_RS_ANNOTATIONS
            = Arrays.asList(GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class);

    static final String REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME = "rest-request.enabled";
    private static final boolean REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE = false;

    static final String SYNTHETIC_SIMPLE_TIMER_METRIC_NAME = "REST.request";

    static final Metadata SYNTHETIC_SIMPLE_TIMER_METADATA = Metadata.builder()
            .withName(SYNTHETIC_SIMPLE_TIMER_METRIC_NAME)
            .withDisplayName(SYNTHETIC_SIMPLE_TIMER_METRIC_NAME + " for all REST endpoints")
            .withDescription("The number of invocations and total response time of RESTful resource methods since the start"
                                     + " of the server.")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.NANOSECONDS)
            .notReusable()
            .build();

    private final Map<Bean<?>, AnnotatedMember<?>> producers = new HashMap<>();

    private final Map<MetricID, AnnotatedMethod<?>> annotatedGaugeSites = new HashMap<>();

    private Errors.Collector errors = Errors.collector();

    private final Set<Class<?>> metricsAnnotatedClasses = new HashSet<>();
    private final Set<Class<?>> metricsAnnotatedClassesProcessed = new HashSet<>();
    private final Map<Class<?>, Set<Method>> methodsWithSyntheticSimpleTimer = new HashMap<>();
    private final Set<Class<?>> syntheticSimpleTimerClassesProcessed = new HashSet<>();
    private final Set<Method> syntheticSimpleTimersToRegister = new HashSet<>();
    private final Map<Method, AsyncResponseInfo> asyncSyntheticSimpleTimerInfo = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T getReference(BeanManager bm, Type type, Bean<?> bean) {
        return (T) bm.getReference(bean, type, bm.createCreationalContext(bean));
    }

    /**
     * DO NOT USE THIS METHOD please.
     *
     * @param element element
     * @param clazz class
     * @param lookupResult lookup result
     * @param <E> type of element
     * @deprecated This method is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @Deprecated
    public static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, LookupResult<? extends Annotation> lookupResult) {
        MetricRegistry registry = getMetricRegistry();
        Annotation annotation = lookupResult.getAnnotation();

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), counted.name().trim(),
                                              counted.absolute());
            String displayName = counted.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(counted.description().trim())
                    .withType(MetricType.COUNTER)
                    .withUnit(counted.unit().trim())
                    .reusable(counted.reusable())
                    .build();
            registry.counter(meta, tags(counted.tags()));
            LOGGER.log(Level.FINE, () -> "Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), metered.name().trim(),
                                              metered.absolute());
            String displayName = metered.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(metered.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(metered.unit().trim())
                    .reusable(metered.reusable())
                    .build();
            registry.meter(meta, tags(metered.tags()));
            LOGGER.log(Level.FINE, () -> "Registered meter " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), timed.name().trim(),
                                              timed.absolute());
            String displayName = timed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(timed.description().trim())
                    .withType(MetricType.TIMER)
                    .withUnit(timed.unit().trim())
                    .reusable(timed.reusable())
                    .build();
            registry.timer(meta, tags(timed.tags()));
            LOGGER.log(Level.FINE, () -> "Registered timer " + metricName);
        } else if (annotation instanceof ConcurrentGauge) {
            ConcurrentGauge concurrentGauge = (ConcurrentGauge) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), concurrentGauge.name().trim(),
                                              concurrentGauge.absolute());
            String displayName = concurrentGauge.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(concurrentGauge.description().trim())
                    .withType(MetricType.CONCURRENT_GAUGE)
                    .withUnit(concurrentGauge.unit().trim())
                    .reusable(concurrentGauge.reusable())
                    .build();
            registry.concurrentGauge(meta, tags(concurrentGauge.tags()));
            LOGGER.log(Level.FINE, () -> "Registered concurrent gauge " + metricName);
        } else if (annotation instanceof SimplyTimed) {
            SimplyTimed simplyTimed = (SimplyTimed) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), simplyTimed.name().trim(),
                                              simplyTimed.absolute());
            String displayName = simplyTimed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(simplyTimed.description().trim())
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(simplyTimed.unit().trim())
                    .reusable(simplyTimed.reusable())
                    .build();
            registry.simpleTimer(meta, tags(simplyTimed.tags()));
            LOGGER.log(Level.FINE, () -> "Registered simple timer " + metricName);
        }
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
        return result.toArray(new Tag[result.size()]);
    }

    static String[] tags(Tag[] tags) {
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < tags.length; i++) {
            result.add(tags[i].getTagName() + "=" + tags[i].getTagValue());
        }
        return result.toArray(new String[0]);
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

    static MetricRegistry getRegistryForSyntheticSimpleTimers() {
        return RegistryProducer.getBaseRegistry();
    }

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.FINE, () -> "Before bean discovery " + discovery);

        // Initialize our implementation
        RegistryProducer.clearApplicationRegistry();

        // Register beans manually
        discovery.addAnnotatedType(RegistryProducer.class, "RegistryProducer");
        discovery.addAnnotatedType(MetricProducer.class, "MetricProducer");
        discovery.addAnnotatedType(InterceptorCounted.class, "InterceptorCounted");
        discovery.addAnnotatedType(InterceptorMetered.class, "InterceptorMetered");
        discovery.addAnnotatedType(InterceptorTimed.class, "InterceptorTimed");
        discovery.addAnnotatedType(InterceptorConcurrentGauge.class, "InterceptorConcurrentGauge");
        discovery.addAnnotatedType(InterceptorSimplyTimed.class, InterceptorSimplyTimed.class.getSimpleName());

        // Telling CDI about our private SyntheticSimplyTimed annotation and its interceptor
        // is enough for CDI to intercept invocations of methods so annotated.
        discovery.addAnnotatedType(InterceptorSyntheticSimplyTimed.class, InterceptorSyntheticSimplyTimed.class.getSimpleName());
        discovery.addAnnotatedType(SyntheticSimplyTimed.class, SyntheticSimplyTimed.class.getSimpleName());

        // Config might disable the MP synthetic SimpleTimer feature for JAX-RS endpoints.
        // For efficiency, prepare to consult config only once rather than from each interceptor instance.
        discovery.addAnnotatedType(RestEndpointMetricsInfo.class, RestEndpointMetricsInfo.class.getSimpleName());

        asyncSyntheticSimpleTimerInfo.clear();
    }

    Map<Method, AsyncResponseInfo> asyncResponseInfo() {
        return asyncSyntheticSimpleTimerInfo;
    }

    private void clearAnnotationInfo(@Observes AfterDeploymentValidation adv) {
        if (LOGGER.isLoggable(Level.FINE)) {
            Set<Class<?>> metricsAnnotatedClassesIgnored = new HashSet<>(metricsAnnotatedClasses);
            metricsAnnotatedClassesIgnored.removeAll(metricsAnnotatedClassesProcessed);
            if (!metricsAnnotatedClassesIgnored.isEmpty()) {
                LOGGER.log(Level.FINE, () ->
                        "Classes originally found with metrics annotations that were not processed, probably "
                                + "because they were vetoed:" + metricsAnnotatedClassesIgnored.toString());
            }
        }
        metricsAnnotatedClasses.clear();
        metricsAnnotatedClassesProcessed.clear();
        methodsWithSyntheticSimpleTimer.clear();
    }

    /**
     * Records Java classes with a metrics annotation somewhere.
     *
     * By recording the classes here, we let CDI optimize its invocations of this observer method. Later, when we
     * observe managed beans (which CDI invokes for all managed beans) where we also have to examine each method and
     * constructor, we can quickly eliminate from consideration any classes we have not recorded here.
     *
     * @param pat ProcessAnnotatedType event
     */
    private void recordMetricAnnotatedClass(@Observes
    @WithAnnotations({Counted.class, Metered.class, Timed.class, ConcurrentGauge.class,
            SimplyTimed.class}) ProcessAnnotatedType<?> pat) {
        checkAndRecordCandidateMetricClass(pat);
    }

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
        LOGGER.log(Level.FINE, () -> "Accepting " + clazz.getName() + " for later bean processing");
        return true;
    }

    /**
     * Make sure the annotated type is neither abstract nor an interceptor and stores the Java class.
     *
     * @param pat {@code ProcessAnnotatedType} event
     * @return true if the annotated type should be kept for potential processing later; false otherwise
     */
    private boolean checkAndRecordCandidateMetricClass(ProcessAnnotatedType<?> pat) {
        boolean result = checkCandidateMetricClass(pat);
        if (result) {
            metricsAnnotatedClasses.add(pat.getAnnotatedType().getJavaClass());
        }
        return result;
    }

    /**
     * Observes all beans but immediately dismisses ones for which the Java class was not previously noted
     * during {@code ProcessAnnotatedType} (which recorded only classes with metrics annotations).
     *
     * @param pmb event describing the managed bean being processed
     */
    private void registerMetrics(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type =  pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!metricsAnnotatedClasses.contains(clazz)) {
            return;
        }
        // Recheck for Interceptor.
        if (type.isAnnotationPresent(Interceptor.class)) {
            LOGGER.log(Level.FINE, "Ignoring metrics defined on type " + clazz.getName()
                    + " because a CDI portable extension added @Interceptor to it dynamically");
            return;
        }
        metricsAnnotatedClassesProcessed.add(clazz);

        LOGGER.log(Level.FINE, () -> "Processing annotations for " + clazz.getName());

        // Process methods keeping non-private declared on this class
        for (AnnotatedMethod<?> annotatedMethod : type.getMethods()) {
            if (Modifier.isPrivate(annotatedMethod.getJavaMember().getModifiers())) {
                continue;
            }
            METRIC_ANNOTATIONS.forEach(annotation -> {
                for (LookupResult<? extends Annotation> lookupResult : MetricUtil.lookupAnnotations(
                        type, annotatedMethod, annotation)) {
                    // For methods, register the metric only on the declaring
                    // class, not subclasses per the MP Metrics 2.0 TCK
                    // VisibilityTimedMethodBeanTest.
                    if (lookupResult.getType() != MetricUtil.MatchingType.METHOD
                            || clazz.equals(annotatedMethod.getJavaMember()
                            .getDeclaringClass())) {
                        registerMetric(annotatedMethod.getJavaMember(), clazz, lookupResult);
                    }
                }
            });
        }

        // Process constructors
        for (AnnotatedConstructor<?> annotatedConstructor : type.getConstructors()) {
            Constructor c = annotatedConstructor.getJavaMember();
            if (Modifier.isPrivate(c.getModifiers())) {
                continue;
            }
            METRIC_ANNOTATIONS.forEach(annotation -> {
                LookupResult<? extends Annotation> lookupResult
                        = lookupAnnotation(c, annotation, clazz);
                if (lookupResult != null) {
                    registerMetric(c, clazz, lookupResult);
                }
            });
        }
    }

    private void processInjectionPoints(@Observes ProcessInjectionPoint<?, ?> pip) {
        Type type = pip.getInjectionPoint().getType();
        if (type.equals(Counter.class) || type.equals(Histogram.class)
                || type.equals(Meter.class) || type.equals(Timer.class) || type.equals(SimpleTimer.class)
                || type.equals(org.eclipse.microprofile.metrics.ConcurrentGauge.class)) {
            pip.configureInjectionPoint().addQualifier(VendorDefined.Literal.INSTANCE);
        }
    }

    /**
     * Adds a {@code SyntheticSimplyTimed} annotation to each JAX-RS endpoint method.
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
                || !restEndpointsMetricEnabledFromConfig()) {
            return;
        }

        LOGGER.log(Level.FINE,
                () -> "Processing SyntheticSimplyTimed annotation for " + pat.getAnnotatedType()
                        .getJavaClass()
                        .getName());

        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated()
                .getJavaClass();

        Set<Method> methodsToRecord = new HashSet<>();

        // Process methods keeping non-private declared on this class
        configurator.filterMethods(method -> !Modifier.isPrivate(method.getJavaMember()
                                                                         .getModifiers()))
                .forEach(method ->
                        JAX_RS_ANNOTATIONS.forEach(jaxRsAnnotation -> {
                            AnnotatedMethod<?> annotatedMethod = method.getAnnotated();
                            if (annotatedMethod.isAnnotationPresent(jaxRsAnnotation)) {
                                Method m = annotatedMethod.getJavaMember();
                                // For methods, add the SyntheticSimplyTimed annotation only on the declaring
                                // class, not subclasses.
                                if (clazz.equals(m.getDeclaringClass())) {
                                    LOGGER.log(Level.FINE, () -> String.format("Adding @SyntheticSimplyTimed to %s#%s", clazz.getName(),
                                            m.getName()));

                                    // Add the synthetic annotation to this method's configurator and record this Java method.
                                    method.add(LiteralSyntheticSimplyTimed.getInstance());
                                    methodsToRecord.add(m);
                                }
                            }
                        }));
        if (!methodsToRecord.isEmpty()) {
            methodsWithSyntheticSimpleTimer.put(clazz, methodsToRecord);
        }
    }

    /**
     * Creates or looks up the synthetic {@code SimpleTimer} instance for a JAX-RS method.
     *
     * @param method the {@code Method} for which the synthetic SimpleTimer instance is needed
     * @return the located or created {@code SimpleTimer}
     */
    static SimpleTimer syntheticSimpleTimer(Method method) {
        // By spec, the synthetic SimpleTimers are always in the base registry.
        LOGGER.log(Level.FINE,
                () -> String.format("Registering synthetic SimpleTimer for %s#%s", method.getDeclaringClass().getName(),
                        method.getName()));
        return getRegistryForSyntheticSimpleTimers()
                .simpleTimer(SYNTHETIC_SIMPLE_TIMER_METADATA, syntheticSimpleTimerMetricTags(method));
    }

    private SimpleTimer registerAndSaveAsyncSyntheticSimpleTimer(Method method) {
        SimpleTimer result = syntheticSimpleTimer(method);
        asyncSyntheticSimpleTimerInfo.computeIfAbsent(method, this::asyncResponse);
        return result;
    }

    private AsyncResponseInfo asyncResponse(Method m) {
        int candidateAsyncResponseParameterSlot = 0;

        for (Parameter p : m.getParameters()) {
            if (AsyncResponse.class.isAssignableFrom(p.getType()) && p.getAnnotation(Suspended.class) != null) {
                return new AsyncResponseInfo(candidateAsyncResponseParameterSlot);
            }
            candidateAsyncResponseParameterSlot++;

        }
        return null;
    }

    /**
     * Creates the {@link MetricID} for the synthetic {@link SimplyTimed} annotation we add to each JAX-RS method.
     *
     * @param method Java method of interest
     * @return {@code MetricID} for the Java method
     */
    static MetricID syntheticSimpleTimerMetricID(Method method) {
        return new MetricID(SYNTHETIC_SIMPLE_TIMER_METRIC_NAME, syntheticSimpleTimerMetricTags(method));
    }

    /**
     * Returns the {@code Tag} array for a synthetic {@code SimplyTimed} annotation.
     *
     * @param method the Java method of interest
     * @return the {@code Tag}s indicating the class and method
     */
    static Tag[] syntheticSimpleTimerMetricTags(Method method) {
        return new Tag[] {new Tag("class", method.getDeclaringClass().getName()),
                new Tag("method", methodTagValueForSyntheticSimpleTimer(method))};
    }

    private static String methodTagValueForSyntheticSimpleTimer(Method method) {
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

    /**
     * Records metric producer fields defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppf Producer field.
     */
    private void recordProducerFields(@Observes ProcessProducerField<? extends org.eclipse.microprofile.metrics.Metric, ?> ppf) {
        LOGGER.log(Level.FINE, () -> "recordProducerFields " + ppf.getBean().getBeanClass());
        if (!MetricProducer.class.equals(ppf.getBean().getBeanClass())) {
            Metric metric = ppf.getAnnotatedProducerField().getAnnotation(Metric.class);
            if (metric != null) {
                Optional<? extends Annotation> hasQualifier
                        = ppf.getAnnotatedProducerField()
                        .getAnnotations()
                        .stream()
                        .filter(annotation -> annotation.annotationType().isAnnotationPresent(Qualifier.class))
                        .findFirst();
                // Ignore producers with non-default qualifiers
                if (!hasQualifier.isPresent() || hasQualifier.get() instanceof Default) {
                    producers.put(ppf.getBean(), ppf.getAnnotatedProducerField());
                }
            }
        }
    }

    /**
     * Records metric producer methods defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppm Producer method.
     */
    private void recordProducerMethods(@Observes ProcessProducerMethod<?
            extends org.eclipse.microprofile.metrics.Metric, ?> ppm) {
        LOGGER.log(Level.FINE, () -> "recordProducerMethods " + ppm.getBean().getBeanClass());
        if (!MetricProducer.class.equals(ppm.getBean().getBeanClass())) {
            Metric metric = ppm.getAnnotatedProducerMethod().getAnnotation(Metric.class);
            if (metric != null) {
                Optional<? extends Annotation> hasQualifier
                        = ppm.getAnnotatedProducerMethod()
                        .getAnnotations()
                        .stream()
                        .filter(annotation -> annotation.annotationType().isAnnotationPresent(Qualifier.class))
                        .findFirst();
                // Ignore producers with non-default qualifiers
                if (!hasQualifier.isPresent() || hasQualifier.get() instanceof Default) {
                    producers.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
                }
            }
        }
    }

    /**
     * Registers metrics for all field and method producers defined by the application.
     *
     * @param adv After deployment validation event.
     * @param bm  Bean manager.
     */
    private <T extends org.eclipse.microprofile.metrics.Metric> void registerProducers(
            @Observes AfterDeploymentValidation adv, BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "registerProducers");

        Errors problems = errors.collect();
        errors = null;
        if (problems.hasFatal()) {
            throw new DeploymentException("Metrics module found issues with deployment: " + problems.toString());
        }

        MetricRegistry registry = getMetricRegistry();
        producers.entrySet().forEach(entry -> {
            Metric metric = entry.getValue().getAnnotation(Metric.class);
            if (metric != null) {
                String metricName = getMetricName(new AnnotatedElementWrapper(entry.getValue()),
                                                  entry.getValue().getDeclaringType().getJavaClass(),
                                                  MetricUtil.MatchingType.METHOD,
                                                  metric.name(), metric.absolute());
                T instance = getReference(bm, entry.getValue().getBaseType(), entry.getKey());
                Metadata md = Metadata.builder()
                        .withName(metricName)
                        .withDisplayName(metric.displayName())
                        .withDescription(metric.description())
                        .withType(getMetricType(instance))
                        .withUnit(metric.unit())
                        .reusable(false)
                        .build();
                registry.register(md, instance);
            }
        });
        producers.clear();
    }

    private void collectSyntheticSimpleTimerMetric(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!methodsWithSyntheticSimpleTimer.containsKey(clazz)) {
            return;
        }

        LOGGER.log(Level.FINE, () -> "Processing synthetic SimplyTimed annotations for " + clazz.getName());

        syntheticSimpleTimerClassesProcessed.add(clazz);
        syntheticSimpleTimersToRegister.addAll(methodsWithSyntheticSimpleTimer.get(clazz));
    }

    private void registerSyntheticSimpleTimerMetrics(@Observes @RuntimeStart Object event) {
        syntheticSimpleTimersToRegister.forEach(this::registerAndSaveAsyncSyntheticSimpleTimer);
        if (LOGGER.isLoggable(Level.FINE)) {
            Set<Class<?>> syntheticSimpleTimerAnnotatedClassesIgnored = new HashSet<>(methodsWithSyntheticSimpleTimer.keySet());
            syntheticSimpleTimerAnnotatedClassesIgnored.removeAll(syntheticSimpleTimerClassesProcessed);
            if (!syntheticSimpleTimerAnnotatedClassesIgnored.isEmpty()) {
                LOGGER.log(Level.FINE, () ->
                        "Classes with synthetic SimplyTimer annotations added that were not processed, probably "
                                + "because they were vetoed:" + syntheticSimpleTimerAnnotatedClassesIgnored.toString());
            }
        }
        syntheticSimpleTimerClassesProcessed.clear();
        syntheticSimpleTimersToRegister.clear();
    }

    boolean restEndpointsMetricEnabledFromConfig() {
        try {
            return ((Config) (ConfigProvider.getConfig()))
                    .get("metrics")
                    .get(REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME)
                    .asBoolean().orElse(REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error looking up config setting for enabling REST endpoints SimpleTimer metrics;"
                    + " reporting 'false'", t);
            return false;
        }
    }

    // register metrics with server after security and when
    // application scope is initialized
    void registerMetrics(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
                         BeanManager bm) {
        Set<String> vendorMetricsAdded = new HashSet<>();
        Config config = ((Config) ConfigProvider.getConfig()).get("metrics");

        MetricsSupport metricsSupport = MetricsSupport.create(config);

        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);

        ConfigValue<String> routingNameConfig = config.get("routing").asString();
        Routing.Builder defaultRouting = server.serverRoutingBuilder();

        Routing.Builder endpointRouting = defaultRouting;

        if (routingNameConfig.isPresent()) {
            String routingName = routingNameConfig.get();
            // support for overriding this back to default routing using config
            if (!"@default".equals(routingName)) {
                endpointRouting = server.serverNamedRoutingBuilder(routingName);
            }
        }

        metricsSupport.configureVendorMetrics(null, defaultRouting);
        vendorMetricsAdded.add("@default");
        metricsSupport.configureEndpoint(endpointRouting);

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
    }

    private static boolean chooseRestEndpointsSetting(Config metricsConfig) {
        ConfigValue<Boolean> explicitRestEndpointsSetting =
                metricsConfig.get(REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME).asBoolean();
        boolean result = explicitRestEndpointsSetting.orElse(REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE);
        if (explicitRestEndpointsSetting.isPresent()) {
            LOGGER.log(Level.FINE, () -> String.format(
                    "Support for MP REST.reqeust metric and annotation handling explicitly set to %b in configuration",
                    explicitRestEndpointsSetting.get()));
        } else {
            LOGGER.log(Level.FINE, () -> String.format(
                    "Support for MP REST.reqeust metric and annotation handling explicitly defaulted to %b",
                    REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE));
        }
        return result;
    }

    private static <T extends org.eclipse.microprofile.metrics.Metric> MetricType getMetricType(T metric) {
        // Find subtype of Metric, needed for user-defined metrics
        Class<?> clazz = metric.getClass();
        do {
            Optional<Class<?>> optionalClass = Arrays.stream(clazz.getInterfaces())
                    .filter(org.eclipse.microprofile.metrics.Metric.class::isAssignableFrom)
                    .findFirst();
            if (optionalClass.isPresent()) {
                clazz = optionalClass.get();
                break;
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return MetricType.from(clazz == null ? metric.getClass() : clazz);
    }

    private void recordAnnotatedGaugeSite(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        LOGGER.log(Level.FINE, () -> "recordAnnoatedGaugeSite for class " + clazz);
        LOGGER.log(Level.FINE, () -> "Processing annotations for " + clazz.getName());

        // Register metrics based on annotations
        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process @Gauge methods keeping non-private declared on this class
        for (AnnotatedMethod method : type.getMethods()) {
            Method javaMethod = method.getJavaMember();
            if (!javaMethod.getDeclaringClass().equals(clazz)
                    || Modifier.isPrivate(javaMethod.getModifiers())
                    || !method.isAnnotationPresent(Gauge.class)) {
                continue;
            }
            Class<? extends Annotation> scopeAnnotation = pmb.getBean().getScope();
            if (scopeAnnotation == RequestScoped.class) {
                errors.fatal(clazz, "Cannot configure @Gauge on a request scoped bean");
                return;
            }
            if (scopeAnnotation != ApplicationScoped.class && type.getAnnotation(Singleton.class) == null) {
                if (ConfigProvider.getConfig().getOptionalValue("metrics.warn-dependent", Boolean.class).orElse(true)) {
                    LOGGER.warning("@Gauge is configured on a bean " + clazz.getName()
                            + " that is neither ApplicationScoped nor Singleton. This is most likely a bug."
                            + " You may set 'metrics.warn-dependent' configuration option to 'false' to remove "
                            + "this warning.");
                }
            }
            Gauge gaugeAnnotation = method.getAnnotation(Gauge.class);
            String explicitGaugeName = gaugeAnnotation.name();
            String gaugeNameSuffix = (
                    explicitGaugeName.length() > 0 ? explicitGaugeName
                            : javaMethod.getName());
            String gaugeName = (
                    gaugeAnnotation.absolute() ? gaugeNameSuffix
                            : String.format("%s.%s", clazz.getName(), gaugeNameSuffix));
            annotatedGaugeSites.put(new MetricID(gaugeName, tags(gaugeAnnotation.tags())), method);
            LOGGER.log(Level.FINE, () -> String.format("Recorded annotated gauge with name %s", gaugeName));
        }
    }

    private void registerAnnotatedGauges(@Observes AfterDeploymentValidation adv, BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "registerGauges");
        MetricRegistry registry = getMetricRegistry();

        annotatedGaugeSites.entrySet().forEach(gaugeSite -> {
            LOGGER.log(Level.FINE, () -> "gaugeSite " + gaugeSite.toString());
            MetricID gaugeID = gaugeSite.getKey();

            AnnotatedMethod<?> site = gaugeSite.getValue();
            // TODO uncomment following clause once MP metrics enforces restriction
            DelegatingGauge<? /* extends Number */> dg;
            try {
                dg = buildDelegatingGauge(gaugeID.getName(), site,
                                          bm);
                Gauge gaugeAnnotation = site.getAnnotation(Gauge.class);
                Metadata md = Metadata.builder()
                        .withName(gaugeID.getName())
                        .withDisplayName(gaugeAnnotation.displayName())
                        .withDescription(gaugeAnnotation.description())
                        .withType(MetricType.GAUGE)
                        .withUnit(gaugeAnnotation.unit())
                        .reusable(false)
                        .build();
                LOGGER.log(Level.FINE, () -> String.format("Registering gauge with metadata %s", md.toString()));
                registry.register(md, dg, gaugeID.getTagsAsList().toArray(new Tag[0]));
            } catch (Throwable t) {
                adv.addDeploymentProblem(new IllegalArgumentException("Error processing @Gauge "
                                                                              + "annotation on " + site
                        .getJavaMember().getDeclaringClass().getName()
                                                                              + ":" + site.getJavaMember()
                        .getName(), t));
            }
        });

        annotatedGaugeSites.clear();
    }

    private DelegatingGauge<? /* extends Number */> buildDelegatingGauge(String gaugeName,
                                                                         AnnotatedMethod<?> site, BeanManager bm) {
        // TODO uncomment preceding clause once MP metrics enforces restriction
        Bean<?> bean = bm.getBeans(site.getJavaMember().getDeclaringClass())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find bean for annotated gauge " + gaugeName));

        Class<?> returnType = site.getJavaMember().getReturnType();
        // TODO uncomment following line once MP metrics enforces restriction
        //        Class<? extends Number> narrowedReturnType = typeToNumber(returnType);

        return DelegatingGauge.newInstance(
                site.getJavaMember(),
                getReference(bm, bean.getBeanClass(), bean),
                // TODO use narrowedReturnType instead of returnType below once MP metrics enforces restriction
                returnType);
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

    static class AnnotatedElementWrapper implements AnnotatedElement, Member {

        private final AnnotatedMember<?> annotatedMember;

        AnnotatedElementWrapper(AnnotatedMember<?> annotatedMember) {
            this.annotatedMember = annotatedMember;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return annotatedMember.isAnnotationPresent(annotationClass);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return annotatedMember.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotatedMember.getAnnotations().toArray(new Annotation[] {});
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return getAnnotations();
        }

        @Override
        public Class<?> getDeclaringClass() {
            return annotatedMember.getDeclaringType().getJavaClass();
        }

        @Override
        public String getName() {
            return annotatedMember.getJavaMember().getName();
        }

        @Override
        public int getModifiers() {
            return annotatedMember.getJavaMember().getModifiers();
        }

        @Override
        public boolean isSynthetic() {
            return annotatedMember.getJavaMember().isSynthetic();
        }
    }

    /**
     * A {@code AsyncResponse} parameter annotated with {@code Suspended} in a JAX-RS method subject to inferred
     * {@code SimplyTimed} behavior.
     */
    static class AsyncResponseInfo {

        // which parameter slot in the method the AsyncResponse is
        private final int parameterSlot;

        AsyncResponseInfo(int parameterSlot) {
            this.parameterSlot = parameterSlot;
        }

        /**
         * Returns the {@code AsyncResponse} argument object in the given invocation.
         *
         * @param context the {@code InvocationContext} representing the call with an {@code AsyncResponse} parameter
         * @return the {@code AsyncResponse} instance
         */
        AsyncResponse asyncResponse(InvocationContext context) {
            return AsyncResponse.class.cast(context.getParameters()[parameterSlot]);
        }
    }
}
