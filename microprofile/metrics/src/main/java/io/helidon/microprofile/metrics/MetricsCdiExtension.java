/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.Errors;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.metrics.api.BuiltInMeterNameFormat;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.microprofile.metrics.MetricAnnotationInfo.RegistrationPrep;
import io.helidon.microprofile.metrics.MetricUtil.LookupResult;
import io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver;
import io.helidon.microprofile.metrics.spi.MetricRegistrationObserver;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.observe.metrics.MetricsObserverConfig;

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
import jakarta.enterprise.inject.spi.BeforeShutdown;
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
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * MetricsCdiExtension class.
 *
 * <p>
 * Earlier versions of this class detected app-provided producer fields and methods and triggered creation and registration
 * of the corresponding metrics upon such detection. As explained in this
 * <a href="https://github.com/eclipse/microprofile-metrics/issues/456">MP metrics issue</a>
 * and this <a href="https://github.com/eclipse/microprofile-metrics/pull/594">MP metrics PR</a>,
 * this probably was never correct and does not work because {@code @Metric} no longer applies to producers per the
 * MP metrics 3.0 spec. The issue and PR discussion explain how developers who provide their own producers should use
 * CDI qualifiers on the producers (and, therefore, injection points) to avoid ambiguity between their own producers and
 * producers written by vendors implementing MP metrics.
 *
 * For Helidon, this means we no longer need to track producer fields and methods, nor do we need to augment injection points
 * with our own {@code VendorProvided} qualifier to disambiguate, because we now rely on developers who write their own
 * producers to avoid the ambiguity using qualifiers.
 * </p>
 */
public class MetricsCdiExtension extends HelidonRestCdiExtension {
    static final Set<Class<? extends Annotation>> ALL_METRIC_ANNOTATIONS = Set.of(
            Counted.class, Timed.class, Gauge.class); // There is no annotation for histograms.
    static final String REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME = "rest-request.enabled";
    static final String SYNTHETIC_TIMER_METRIC_NAME = "REST.request";
    static final String SYNTHETIC_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME =
            SYNTHETIC_TIMER_METRIC_NAME + ".unmappedException.total";
    static final Metadata SYNTHETIC_TIMER_METADATA = Metadata.builder()
            .withName(SYNTHETIC_TIMER_METRIC_NAME)
            .withDescription("""
                                     The number of invocations and total response time of this RESTful resource method since the \
                                     start of the server. The metric will not record the elapsed time nor count of a REST \
                                     request if it resulted in an unmapped exception. Also tracks the highest recorded time \
                                     duration and the 50th, 75th, 95th, 98th, 99th and 99.9th percentile.""")
            .withUnit(MetricUnits.NANOSECONDS)
            .build();
    private static final System.Logger LOGGER = System.getLogger(MetricsCdiExtension.class.getName());
    private static final Map<Class<? extends Annotation>, AnnotationLiteral<?>> INTERCEPTED_METRIC_ANNOTATIONS =
            Map.of(
                    Counted.class, InterceptorCounted.binding(),
                    Timed.class, InterceptorTimed.binding());
    private static final List<Class<? extends Annotation>> JAX_RS_ANNOTATIONS
            = Arrays.asList(GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class);
    private static final Set<Class<? extends Annotation>> METRIC_ANNOTATIONS_ON_ANY_ELEMENT =
            new HashSet<>(ALL_METRIC_ANNOTATIONS) {
                {
                    remove(Gauge.class);
                }
            };
    private static final boolean REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE = false;
    private static Metadata syntheticTimerUnmappedExceptionMetadata;
    private final Map<MetricID, AnnotatedMethod<?>> annotatedGaugeSites = new HashMap<>();
    private final List<RegistrationPrep> annotatedSites = new ArrayList<>();

    // Built by ProcessAnnotatedType observer to contain all possible REST request methods,
    // even if the bean might be vetoed.
    private final Map<Class<?>, Set<Method>> methodsWithRestRequestMetrics = new HashMap<>();

    // Built by ProcessManagedBean observer to contain only methods from non-vetoed beans.
    private final Map<Class<?>, Set<Method>> restRequestMethods = new HashMap<>();

    private final Set<Class<?>> restRequestMetricsClassesProcessed = new HashSet<>();
    private final WorkItemsManager<MetricWorkItem> workItemsManager = WorkItemsManager.create();
    private final List<MetricAnnotationDiscoveryObserver> metricAnnotationDiscoveryObservers = new ArrayList<>();
    private final List<MetricRegistrationObserver> metricRegistrationObservers = new ArrayList<>();
    private final Map<Executable, List<MetricAnnotationDiscovery>> metricAnnotationDiscoveriesByExecutable = new HashMap<>();
    @SuppressWarnings("unchecked")

    // records stereotype annotations which have metrics annotations inside them
    private final Map<Class<?>, StereotypeMetricsInfo> stereotypeMetricsInfo = new HashMap<>();
    private boolean restEndpointsMetricsEnabled = REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE;
    private Errors.Collector errors = Errors.collector();
    private String syntheticTimerMetricUnmappedExceptionName;

    /**
     * Creates a new extension instance.
     */
    public MetricsCdiExtension() {
        super(LOGGER, nestedConfigKey("metrics"), "metrics");
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
     * Creates or looks up the {@code Timer} instance for measuring REST requests on any JAX-RS method.
     *
     * @param clazz The {@code Class} on which the method to be timed exists
     * @param method the {@code Method} for which the Timer instance is needed
     * @return the located or created {@code Timer}
     */
    static Timer restEndpointTimer(Class<?> clazz, Method method) {
        // By spec, the synthetic Timers are always in the base registry.
        LOGGER.log(Level.DEBUG,
                   () -> String.format("Registering synthetic SimpleTimer for %s#%s", clazz.getName(),
                                       method.getName()));
        return getRegistryForSyntheticRestRequestMetrics()
                .timer(SYNTHETIC_TIMER_METADATA, syntheticRestRequestMetricTags(clazz, method));
    }

    /**
     * Creates or looks up the {@code Counter} instance for measuring REST requests on any JAX-RS method.
     *
     * @param clazz the {@code Class} on which the method to be counted exists
     * @param method the {@code Method} for which the Counter instance is needed
     * @return the located or created {@code Counter}
     */
    static Counter restEndpointCounter(Class<?> clazz, Method method) {
        LOGGER.log(Level.DEBUG,
                   () -> String.format("Registering synthetic Counter for %s#%s", clazz.getName(),
                                       method.getName()));
        return getRegistryForSyntheticRestRequestMetrics()
                .counter(syntheticTimerUnmappedExceptionMetadata, syntheticRestRequestMetricTags(clazz, method));
    }

    /**
     * Creates the {@link MetricID} for the synthetic {@link Timed} metric we add to each JAX-RS method.
     *
     * @param clazz Java class on which the method exists
     * @param method Java method of interest
     * @return {@code MetricID} for the simpletimer for this Java method
     */
    static MetricID restEndpointTimerMetricID(Class<?> clazz, Method method) {
        return new MetricID(SYNTHETIC_TIMER_METRIC_NAME, syntheticRestRequestMetricTags(clazz, method));
    }

    /**
     * Creates the {@link MetricID} for the synthetic {@link Counter} metric we add to each JAX-RS method.
     *
     * @param clazz Java class on which the method exists
     * @param method Java method of interest
     * @return {@code MetricID} for the counter for this Java method
     */
    MetricID restEndpointCounterMetricID(Class<?> clazz, Method method) {
        return new MetricID(syntheticTimerMetricUnmappedExceptionName, syntheticRestRequestMetricTags(clazz, method));
    }

    /**
     * Returns the {@code Tag} array for a synthetic {@code SimplyTimed} annotation.
     *
     * @param clazz the Java class on which the measured method exists
     * @param method the Java method of interest
     * @return the {@code Tag}s indicating the class and method
     */
    static Tag[] syntheticRestRequestMetricTags(Class<?> clazz, Method method) {
        return new Tag[] {new Tag("class", clazz.getName()),
                new Tag("method", methodTagValueForSyntheticRestRequestMetric(method))};
    }

    /**
     * Clears data structures.
     * <p>
     * CDI invokes the {@link #onShutdown(jakarta.enterprise.inject.spi.BeforeShutdown)} method when CDI is in play, but
     * some tests do not use the CDI environment and need to invoke this method to do the clean-up.
     * </p>
     */
    static void shutdown() {
        MetricsFactory.closeAll();
        RegistryFactory.closeAll();
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

    @Override
    public void clearAnnotationInfo(@Observes AfterDeploymentValidation adv) {
        super.clearAnnotationInfo(adv);
        methodsWithRestRequestMetrics.clear();
    }

    // register metrics with server after security and when
    // application scope is initialized

    /**
     * Register the Metrics observer with server observer feature.
     * This is a CDI observer method invoked by CDI machinery.
     *
     * @param event  event object
     * @param bm     CDI bean manager
     * @param server Server CDI extension
     */
    public void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                                Object event,
                                BeanManager bm,
                                ServerCdiExtension server) {
        Errors problems = errors.collect();
        errors = null;
        if (problems.hasFatal()) {
            throw new DeploymentException("Metrics module found issues with deployment: " + problems);
        }

         // this needs to be done early on, so the registry is configured before accessed
        MetricsObserver observer = configure();

        registerMetricsForAnnotatedSites();
        registerAnnotatedGauges(bm);
        registerRestRequestMetrics();

        Set<String> vendorMetricsAdded = new HashSet<>();

        // now we may have additional sockets we want to add vendor metrics to
        componentConfig().get("vendor-metrics-routings")
                .asList(String.class)
                .orElseGet(List::of)
                .forEach(routeName -> {
                    if (!vendorMetricsAdded.contains(routeName)) {
                        observer.configureVendorMetrics(server.serverNamedRoutingBuilder(routeName));
                        vendorMetricsAdded.add(routeName);
                    }
                });

        server.addObserver(observer);
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> pmb) {

        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        // Check for Interceptor. We have already checked developer-provided beans, but other extensions might have supplied
        // additional beans that we have not checked yet.
        if (type.isAnnotationPresent(Interceptor.class)) {
            LOGGER.log(Level.DEBUG, "Ignoring objects defined on type " + clazz.getName()
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

    Iterable<MetricWorkItem> workItems(Executable executable, Class<? extends Annotation> annotationType) {
        return workItemsManager.workItems(executable, annotationType);
    }

    <S extends MetricWorkItem> Iterable<S> workItems(Executable executable,
                                                     Class<? extends Annotation> annotationType,
                                                     Class<S> sClass) {
        return TypeFilteredIterable.create(workItems(executable, annotationType), sClass);
    }

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.DEBUG, () -> "Before bean discovery " + discovery);

        // Register beans manually with annotated type identifiers that are deliberately the same as those used by the container
        // during bean discovery to avoid accidental duplicate registration in odd packaging scenarios.
        discovery.addAnnotatedType(RegistryProducer.class, RegistryProducer.class.getName());
        discovery.addAnnotatedType(MetricProducer.class, MetricProducer.class.getName());
        discovery.addAnnotatedType(InterceptorCounted.class, InterceptorCounted.class.getName());
        discovery.addAnnotatedType(InterceptorTimed.class, InterceptorTimed.class.getName());

        // Telling CDI about our private SyntheticRestRequest annotation and its interceptor
        // is enough for CDI to intercept invocations of methods so annotated.
        discovery.addAnnotatedType(InterceptorSyntheticRestRequest.class, InterceptorSyntheticRestRequest.class.getName());
        discovery.addAnnotatedType(SyntheticRestRequest.class, SyntheticRestRequest.class.getName());

        restEndpointsMetricsEnabled = restEndpointsMetricsEnabled();
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

    @Override
    protected Config componentConfig() {
        // Combine the Helidon-specific "metrics.xxx" settings with the MP
        // "mp.metrics.xxx" settings into a single metrics config object.
        org.eclipse.microprofile.config.Config mpConfig = ConfigProvider.getConfig();

        DistributionCustomizations.init(mpConfig);

        /*
         Some MP config refers to "mp.metrics.xxx" whereas te neutral SE metrics implementation uses "metrics.yyy" (where
         sometimes xxx and yyy are different). In particular, there's "mp.metrics.appName" -> "metrics.app-name"

         The next section maps MP config settings (if present) to the corresponding SE config settings, possibly adjusting the
         key name in the process.
         */
        Map<String, String> mpToSeKeyNameMap = Map.of("appName", "app-name");
        Map<String, String> mpConfigSettings = new HashMap<>();
        Stream.of("tags",
                  "appName")
                .forEach(key ->
                                 mpConfig.getOptionalValue("mp.metrics." + key, String.class)
                                         .ifPresent(value -> mpConfigSettings.put(mpToSeKeyNameMap.getOrDefault(key, key),
                                                                                  value)));

        Config metricsConfig = super.componentConfig().detach();

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

    @SuppressWarnings("unchecked")
    private static <T> T getReference(BeanManager bm, Type type, Bean<?> bean) {
        return (T) bm.getReference(bean, type, bm.createCreationalContext(bean));
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

    private static boolean isStereotype(Annotation annotation) {
        return annotation.annotationType().isAnnotationPresent(Stereotype.class);
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

    private static boolean chooseRestEndpointsSetting(Config metricsConfig) {
        ConfigValue<Boolean> explicitRestEndpointsSetting =
                metricsConfig.get(REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME).asBoolean();
        boolean result = explicitRestEndpointsSetting.orElse(REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE);
        if (explicitRestEndpointsSetting.isPresent()) {
            LOGGER.log(Level.DEBUG, () -> String.format(
                    "Support for MP REST.request metric and annotation handling explicitly set to %b in configuration",
                    explicitRestEndpointsSetting.get()));
        } else {
            LOGGER.log(Level.DEBUG, () -> String.format(
                    "Support for MP REST.request metric and annotation handling defaulted to %b",
                    REST_ENDPOINTS_METRIC_ENABLED_DEFAULT_VALUE));
        }
        return result;
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

    private MetricsObserver configure() {
        Config config = componentConfig();

        MetricsObserverConfig.Builder builder = MetricsObserver.builder();
        builder.endpoint("/metrics")
                .config(config);

        // Initialize the metrics factory instance and, along with it, the system tags manager.
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);

        Contexts.globalContext().register(metricsFactory);
        MetricsConfig metricsConfig = metricsFactory.metricsConfig();
        syntheticTimerMetricUnmappedExceptionName =
                metricsConfig.builtInMeterNameFormat() == BuiltInMeterNameFormat.CAMEL
                ? SYNTHETIC_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME
                        : SYNTHETIC_TIMER_METRIC_NAME + ".unmapped_exception.total";
        syntheticTimerUnmappedExceptionMetadata = Metadata.builder()
                .withName(syntheticTimerMetricUnmappedExceptionName)
                .withDescription("""
                                     The total number of unmapped exceptions that occur from this RESTful resouce method since \
                                     the start of the server.""")
                .withUnit(MetricUnits.NONE)
                .build();
        MeterRegistry meterRegistry = metricsFactory.globalRegistry(metricsConfig);
        return builder.metricsConfig(metricsConfig)
                .meterRegistry(meterRegistry)
                .build();
    }

    private void registerMetricsForAnnotatedSites() {
        for (RegistrationPrep registrationPrep : annotatedSites) {
            metricAnnotationDiscoveriesByExecutable.get(registrationPrep.executable())
                    .forEach(discovery -> {
                        if (discovery.isActive()) { // All annotation discovery observers agreed to preserve the discovery.
                            org.eclipse.microprofile.metrics.Metric metric =
                                    registrationPrep.register(RegistryFactory
                                                                      .getInstance()
                                                                      .getRegistry(registrationPrep.scope()));
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
                                            @WithAnnotations({Counted.class, Timed.class}) ProcessAnnotatedType<?> pat) {
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
     * @param annotatedType   the annotated type containing the constructor or method
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
            LOGGER.log(Level.TRACE, () -> "Ignoring " + clazz.getName()
                    + " with annotations " + annotatedType.getAnnotations()
                    + " for later processing: "
                    + (Modifier.isAbstract(clazz.getModifiers()) ? "abstract " : "")
                    + (annotatedType.isAnnotationPresent(Interceptor.class) ? "interceptor " : ""));
            return false;
        }
        LOGGER.log(Level.DEBUG, () -> "Accepting annotated type " + clazz.getName() + " for later bean processing");
        return true;
    }

    /**
     * Adds a {@code SyntheticRestRequest} annotation to each JAX-RS endpoint method.
     *
     * @param pat the {@code ProcessAnnotatedType} for the type containing the JAX-RS annotated methods
     */
    private void recordTimedForRestResources(@Observes
                                             @WithAnnotations({GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class,
                                                     DELETE.class, PATCH.class})
                                             ProcessAnnotatedType<?> pat) {

        // Ignore abstract classes or interceptors. Check if synthetic Timer creation is enabled, and if so record the
        // class and JAX-RS methods to use in later bean processing.
        if (!checkCandidateMetricClass(pat)
                || !restEndpointsMetricsEnabled) {
            return;
        }

        LOGGER.log(Level.DEBUG,
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

                                         LOGGER.log(Level.DEBUG, () -> String.format("Adding @SyntheticRestRequest to %s",
                                                                                     m.toString()));
                                         annotatedMethodConfigurator.add(SyntheticRestRequest.Literal.getInstance());
                                         methodsToRecord.add(m);
                                     }
                                 }));
        if (!methodsToRecord.isEmpty()) {
            methodsWithRestRequestMetrics.put(clazz, methodsToRecord);
        }
    }

    private void registerAndSaveRestRequestMetrics(Class<?> clazz, Method method) {
        workItemsManager.put(method, SyntheticRestRequest.class,
                             SyntheticRestRequestWorkItem.create(restEndpointTimerMetricID(clazz, method),
                                                                 restEndpointTimer(clazz, method),
                                                                 restEndpointCounterMetricID(clazz, method),
                                                                 restEndpointCounter(clazz, method)));
    }

    private void collectRestRequestMetrics(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!methodsWithRestRequestMetrics.containsKey(clazz)) {
            return;
        }

        LOGGER.log(Level.DEBUG, () -> "Processing synthetic SimplyTimed annotations for " + clazz.getName());

        restRequestMetricsClassesProcessed.add(clazz);
        restRequestMethods.put(clazz, methodsWithRestRequestMetrics.get(clazz));
    }

    private void registerRestRequestMetrics() {
        restRequestMethods.forEach((clazz, methods) ->
                                           methods.forEach(method -> registerAndSaveRestRequestMetrics(clazz, method)));
        if (LOGGER.isLoggable(Level.DEBUG)) {
            Set<Class<?>> syntheticTimerAnnotatedClassesIgnored = new HashSet<>(methodsWithRestRequestMetrics.keySet());
            syntheticTimerAnnotatedClassesIgnored.removeAll(restRequestMetricsClassesProcessed);
            if (!syntheticTimerAnnotatedClassesIgnored.isEmpty()) {
                LOGGER.log(Level.DEBUG, () ->
                        "Classes with synthetic Timed annotations added that were not processed, probably "
                                + "because they were vetoed:" + syntheticTimerAnnotatedClassesIgnored.toString());
            }
        }
        restRequestMetricsClassesProcessed.clear();
        restRequestMethods.clear();
    }

    private void recordAnnotatedGaugeSite(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        LOGGER.log(Level.DEBUG, () -> "recordAnnotatedGaugeSite for class " + clazz);
        LOGGER.log(Level.DEBUG, () -> "Processing annotations for " + clazz.getName());

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
                        LOGGER.log(Level.WARNING, String.format("""
                                               @Gauge is configured on a bean %s that is neither ApplicationScoped nor \
                                               Singleton. This is most likely a bug. You may set 'metrics.warn-dependent' \
                                               configuration option to 'false' to remove this warning.""",
                                                                clazz.getName()));
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
                LOGGER.log(Level.DEBUG, () -> String.format("Recorded annotated gauge with name %s", gaugeName));
            });
        }
    }

    private void onShutdown(@Observes BeforeShutdown shutdown) {
        shutdown();
    }

    private void registerAnnotatedGauges(BeanManager bm) {
        LOGGER.log(Level.DEBUG, () -> "registerGauges");
        MetricRegistry registry = getMetricRegistry();

        List<Exception> gaugeProblems = new ArrayList<>();

        annotatedGaugeSites.entrySet().forEach(gaugeSite -> {
            LOGGER.log(Level.DEBUG, () -> "gaugeSite " + gaugeSite.toString());
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
                            .withDescription(gaugeAnnotation.description())
                            .withUnit(gaugeAnnotation.unit())
                            .build();
                    LOGGER.log(Level.DEBUG, () -> String.format("Registering gauge with metadata %s", md.toString()));
                    registry.gauge(md, dg, DelegatingGauge::getValue, gaugeID.getTagsAsList().toArray(new Tag[0]));
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

    record StereotypeMetricsInfo(Set<Annotation> metricsAnnotations) {

        static StereotypeMetricsInfo create(Set<Annotation> metricsAnnotations) {
            return new StereotypeMetricsInfo(metricsAnnotations);
        }
    }
}
