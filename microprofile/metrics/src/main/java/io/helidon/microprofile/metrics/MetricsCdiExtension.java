/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import javax.interceptor.Interceptor;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.metrics.MetricsSupport;
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

/**
 * MetricsCdiExtension class.
 */
public class MetricsCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MetricsCdiExtension.class.getName());

    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS
            = Arrays.asList(Counted.class, Metered.class, Timed.class, Gauge.class, ConcurrentGauge.class,
                            SimplyTimed.class);

    private static final List<Class<? extends Annotation>> JAX_RS_ANNOTATIONS
            = Arrays.asList(GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class);

    static final String REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME = "rest-endpoints-enabled";
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

    private final Map<MetricID, AnnotatedMethodConfigurator<?>> annotatedGaugeSites = new HashMap<>();

    private Errors.Collector errors = Errors.collector();

    private final List<Method> methodsForSyntheticSimplyTimed = new ArrayList<>();

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
        discovery.addAnnotatedType(InterceptorSimplyTimed.class, InterceptorSimplyTimed.class.getName());

        // Telling CDI about our private SyntheticSimplyTimed annotation and its interceptor
        // is enough for CDI to intercept invocations of methods so annotated.
        discovery.addAnnotatedType(InterceptorSyntheticSimplyTimed.class, InterceptorSyntheticSimplyTimed.class.getSimpleName());
        discovery.addAnnotatedType(SyntheticSimplyTimed.class, SyntheticSimplyTimed.class.getSimpleName());

        // Config might disable the MP synthetic SimpleTimer feature for JAX-RS endpoints.
        // For efficiency, prepare to consult config only once rather than from each interceptor instance.
        discovery.addAnnotatedType(RestEndpointMetricsInfo.class, RestEndpointMetricsInfo.class.getSimpleName());
    }

    /**
     * Observes sites annotated with the metrics annotations.
     *
     * @param pat annotated type instance being processed
     */
    private void registerMetrics(@Observes @WithAnnotations({Counted.class, Metered.class, Timed.class,
            ConcurrentGauge.class, SimplyTimed.class})
                                         ProcessAnnotatedType<?> pat) {
        // Filter out interceptors
        AnnotatedType<?> type = pat.getAnnotatedType();
        Interceptor annot = type.getAnnotation(Interceptor.class);
        if (annot != null) {
            return;
        }

        LOGGER.log(Level.FINE, () -> "Processing annotations for " + pat.getAnnotatedType().getJavaClass().getName());

        // Register metrics based on annotations
        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated().getJavaClass();

        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process methods keeping non-private declared on this class
        configurator.filterMethods(method -> !Modifier.isPrivate(method.getJavaMember().getModifiers()))
                .forEach(method -> {
                    METRIC_ANNOTATIONS.forEach(annotation -> {
                        Method m = method.getAnnotated().getJavaMember();
                        for (LookupResult<? extends Annotation> lookupResult : MetricUtil.lookupAnnotations(
                                configurator.getAnnotated(), method.getAnnotated(), annotation)) {
                            // For methods, register the metric only on the declaring
                            // class, not subclasses per the MP Metrics 2.0 TCK
                            // VisibilityTimedMethodBeanTest.
                            if (lookupResult.getType() != MetricUtil.MatchingType.METHOD
                                    || clazz.equals(m.getDeclaringClass())) {
                                registerMetric(m, clazz, lookupResult);
                            }
                        }
                    });
                });

        // Process constructors
        configurator.filterConstructors(constructor -> !Modifier.isPrivate(constructor.getJavaMember().getModifiers()))
                .forEach(constructor -> {
                    METRIC_ANNOTATIONS.forEach(annotation -> {
                        LookupResult<? extends Annotation> lookupResult
                                = lookupAnnotation(constructor.getAnnotated().getJavaMember(), annotation, clazz);
                        if (lookupResult != null) {
                            registerMetric(constructor.getAnnotated().getJavaMember(), clazz, lookupResult);
                        }
                    });
                });
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
     * Records the need to add a {@code SyntheticSimplyTimed} annotation to each JAX-RS endpoint method.
     *
     * @param pat the {@code ProcessAnnotatedType} for the type containing the JAX-RS annotated methods
     */
    private void recordSimplyTimedForRestResources(@Observes
            @WithAnnotations({GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class})
            ProcessAnnotatedType<?> pat) {

        // Filter out interceptors
        AnnotatedType<?> type = pat.getAnnotatedType();
        Interceptor annot = type.getAnnotation(Interceptor.class);
        if (annot != null) {
            return;
        }

        LOGGER.log(Level.FINE,
                () -> "Processing SyntheticSimplyTimed annotation for " + pat.getAnnotatedType()
                        .getJavaClass()
                        .getName());

        // Register metrics based on annotations
        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated().getJavaClass();

        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process methods keeping non-private declared on this class
        configurator.filterMethods(method -> !Modifier.isPrivate(method.getJavaMember()
                .getModifiers()))
                .forEach(method -> {
                    JAX_RS_ANNOTATIONS.forEach(annotation -> {
                        Method m = method.getAnnotated()
                                .getJavaMember();
                        LookupResult<? extends Annotation> lookupResult
                                = lookupAnnotation(m, annotation, clazz);
                        // For methods, add the SyntheticSimplyTimed annotation only on the declaring
                        // class, not subclasses.
                        if (lookupResult != null
                                && (
                                lookupResult.getType() != MetricUtil.MatchingType.METHOD
                                        || clazz.equals(m.getDeclaringClass()))) {
                            LOGGER.log(Level.FINE, () -> String.format("Adding @SyntheticSimplyTimed to %s#%s", clazz.getName(),
                                    m.getName()));

                            // Add the synthetic annotation to this method's configurator.
                            method.add(LiteralSyntheticSimplyTimed.getInstance());

                            // Record the need to register the synthetic REST.request metric for this class/method. These
                            // dynamically-added annotations will not trigger the @ProcessAnnotatedType invocation that would
                            // normally do the metric registration because CDI has already decided which classes have annotations
                            // that trigger those invocations.
                            methodsForSyntheticSimplyTimed.add(m);
                        }
                    });
                });
    }

    /**
     * Creates or looks up the synthetic {@code SimpleTimer} instance for a JAX-RS method.
     *
     * @param method the {@code Method} for which the synthetic SimpleTimer instance is needed
     * @return the located or created {@code SimpleTimer}
     */
    static SimpleTimer syntheticSimpleTimer(Method method) {
        String classTagValue = method.getDeclaringClass().getName();
        // By spec, the synthetic SimpleTimers are always in the application registry.
        return syntheticSimpleTimer(getRegistryForSyntheticSimpleTimers(), classTagValue, methodTagValueForSyntheticSimpleTimer(method));
    }

    private static SimpleTimer syntheticSimpleTimer(MetricRegistry registry, String classTagValue, String methodTagValue) {
        return registry.simpleTimer(SYNTHETIC_SIMPLE_TIMER_METADATA,
                new Tag[] {new Tag("class", classTagValue), new Tag("method", methodTagValue)});
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

        // and now configure webserver features
        registerWithServer(bm);
    }

    private void registerSyntheticSimplyTimedMetrics(@Observes AfterDeploymentValidation adv) {
        if (restEndpointsMetricEnabledFromConfig()) {
            LOGGER.log(Level.FINE, () -> "Registering synthetic REST SimpleTimer metrics for JAX-RS endpoints");
            methodsForSyntheticSimplyTimed.forEach(MetricsCdiExtension::syntheticSimpleTimer);
        } else {
            LOGGER.log(Level.FINE, "Skipping registration of synthetic REST SimpleTimer metrics due to configuration");
        }
    }

    static boolean restEndpointsMetricEnabledFromConfig() {
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

    private void registerWithServer(BeanManager bm) {
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

    private void recordAnnotatedGaugeSite(@Observes @WithAnnotations(Gauge.class) ProcessAnnotatedType<?> pat) {
        LOGGER.log(Level.FINE, () -> "recordAnnoatedGaugeSite for class " + pat.getAnnotatedType().getJavaClass());
        AnnotatedType<?> type = pat.getAnnotatedType();

        LOGGER.log(Level.FINE, () -> "Processing annotations for " + type.getJavaClass().getName());

        // Register metrics based on annotations
        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated().getJavaClass();

        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        Annotation annotation = type.getAnnotation(RequestScoped.class);
        if (annotation != null) {
            errors.fatal(clazz, "Cannot configure @Gauge on a request scoped bean");
            return;
        }

        if (type.getAnnotation(ApplicationScoped.class) == null && type.getAnnotation(Singleton.class) == null) {
            if (ConfigProvider.getConfig().getOptionalValue("metrics.warn-dependent", Boolean.class).orElse(true)) {
                LOGGER.warning("@Gauge is configured on a bean " + clazz.getName()
                                       + " that is neither ApplicationScoped nor Singleton. This is most likely a bug."
                                       + " You may set 'metrics.warn-dependent' configuration option to 'false' to remove "
                                       + "this warning.");
            }
        }

        // Process @Gauge methods keeping non-private declared on this class
        configurator.filterMethods(method -> method.getJavaMember().getDeclaringClass().equals(clazz)
                && !Modifier.isPrivate(method.getJavaMember().getModifiers())
                && method.isAnnotationPresent(Gauge.class))
                .forEach(method -> {
                    Method javaMethod = method.getAnnotated().getJavaMember();
                    Gauge gaugeAnnotation = method.getAnnotated().getAnnotation(Gauge.class);
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

    private void registerAnnotatedGauges(@Observes AfterDeploymentValidation adv, BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "registerGauges");
        MetricRegistry registry = getMetricRegistry();

        annotatedGaugeSites.entrySet().forEach(gaugeSite -> {
            LOGGER.log(Level.FINE, () -> "gaugeSite " + gaugeSite.toString());
            MetricID gaugeID = gaugeSite.getKey();

            AnnotatedMethodConfigurator<?> site = gaugeSite.getValue();
            // TODO uncomment following clause once MP metrics enforces restriction
            DelegatingGauge<? /* extends Number */> dg;
            try {
                dg = buildDelegatingGauge(gaugeID.getName(), site,
                                          bm);
                Gauge gaugeAnnotation = site.getAnnotated().getAnnotation(Gauge.class);
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
                                                                              + "annotation on " + site.getAnnotated()
                        .getJavaMember().getDeclaringClass().getName()
                                                                              + ":" + site.getAnnotated().getJavaMember()
                        .getName(), t));
            }
        });

        annotatedGaugeSites.clear();
    }

    private DelegatingGauge<? /* extends Number */> buildDelegatingGauge(String gaugeName,
                                                                         AnnotatedMethodConfigurator<?> site, BeanManager bm) {
        // TODO uncomment preceding clause once MP metrics enforces restriction
        Bean<?> bean = bm.getBeans(site.getAnnotated().getJavaMember().getDeclaringClass())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find bean for annotated gauge " + gaugeName));

        Class<?> returnType = site.getAnnotated().getJavaMember().getReturnType();
        // TODO uncomment following line once MP metrics enforces restriction
        //        Class<? extends Number> narrowedReturnType = typeToNumber(returnType);

        return DelegatingGauge.newInstance(
                site.getAnnotated().getJavaMember(),
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
}
