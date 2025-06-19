/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.restclientmetrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientRequestContext;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * CDI extension for REST client metrics support.
 */
public class RestClientMetricsCdiExtension implements Extension {

    private static final System.Logger LOGGER = System.getLogger(RestClientMetricsCdiExtension.class.getName());

    private static final String SAVED_START_TIME_PROPERTY_NAME = RestClientMetricsFilter.class.getName() + ".startTime";

    private static final List<Class<?>> REST_METHOD_ANNOTATIONS = List.of(OPTIONS.class,
                                                                          HEAD.class,
                                                                          GET.class,
                                                                          POST.class,
                                                                          PUT.class,
                                                                          DELETE.class);

    private static final Set<Class<? extends Annotation>> METRICS_ANNOTATIONS = Set.of(Timed.class, Counted.class);

    private final Set<Class<?>> candidateRestClientTypes = new HashSet<>();

    private final Map<Method, List<MetricsUpdateWork>> metricsUpdateWorkByMethod = new HashMap<>();

    private final Map<Class<?>, Map<Method, Set<Registration>>> registrations = new HashMap<>();

    // Some code might create REST clients before CDI announces that app-scoped beans are initialized.
    // We record those "early" REST clients and set up metrics for them only when we are ready.
    private final Collection<Class<?>> deferredRestClients = new ConcurrentLinkedQueue<>();

    private MetricRegistry metricRegistry;

    /**
     * For service loading.
     */
    public RestClientMetricsCdiExtension() {
    }

    void recordRestClientTypes(@Observes @WithAnnotations({OPTIONS.class,
            HEAD.class,
            GET.class,
            POST.class,
            PUT.class,
            DELETE.class,
            PATCH.class,
            RegisterRestClient.class,
            Path.class}) ProcessAnnotatedType<?> pat) {
        if (pat.getAnnotatedType().getJavaClass().isInterface()) {
            /*
            All REST client declarations are interfaces, so at least this annotated type has a chance of being a REST client.
            At this stage in processing simply record all types bearing @RegisterRestClient or @Path or with at least one method
            bearing a REST annotation.
             */
            Class<?> javaType = pat.getAnnotatedType().getJavaClass();
            candidateRestClientTypes.add(javaType);
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Recording " + javaType.getCanonicalName() + " for REST client processing");
            }
        }
    }

    void prepareMetricRegistrations(@Observes AfterBeanDiscovery abd) {
        // For each identified candidate REST client type determine what metric registration(s) are needed for its methods
        // and prepare the pre-invoke and post-invoke operations as needed for each to update the metrics needed for each method.
        // We do not actually register the metrics yet; instead we wait until the REST client infrastructure informs us type
        // by type that an interface is being used as a REST client interface.

        candidateRestClientTypes.forEach(type -> {

            LOGGER.log(DEBUG, "Analyzing REST client interface " + type.getCanonicalName());

            // We need to get the AnnotatedType for the type of interest, but without knowing the ID with which it was added
            // to CDI we cannot retrieve just that one directly. Instead retrieve all annotated types for the type (most likely
            // there will be just one).
            addRegistrations(abd, type);
        });
    }

    void ready(@Observes @Initialized(ApplicationScoped.class) Object event,
               MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        deferredRestClients.forEach(this::registerMetricsForRestClient);
        deferredRestClients.clear();
    }

    void registerMetricsForRestClient(Class<?> restClient) {
        if (metricRegistry == null) {
            deferredRestClients.add(restClient);
            return;
        }

        // User code can invoke REST clients based on an interface that is not known to CDI and, therefore, not known to
        // this extension. Yet our REST client listener is still invoked for such REST clients. Create metric registrations
        // for such an interface just in time.
        if (!registrations.containsKey(restClient)) {
            LOGGER.log(DEBUG, "REST client %s has been registered but was not discovered as a bean;  "
                               + "adding just-in-time metrics registrations",
                       restClient.getCanonicalName());
            addRegistrations(restClient);
        }

        registrations.get(restClient).forEach((method, regs) -> {
            List<Metric> metricsRegisteredForRestClient = LOGGER.isLoggable(DEBUG) ? new ArrayList<>() : null;
            regs.forEach(registration -> {
                Metric metric = registration.registrationOp.apply(metricRegistry);
                if (metricsRegisteredForRestClient != null) {
                    metricsRegisteredForRestClient.add(metric);
                    LOGGER.log(DEBUG, String.format("For REST client method %s#%s registering metric using %s",
                                                    restClient.getCanonicalName(),
                                                    method.getName(),
                                                    registration));
                }
                metricsUpdateWorkByMethod.computeIfAbsent(method, k -> new ArrayList<>())
                        .add(MetricsUpdateWork.create(metric));
                if (metricsRegisteredForRestClient != null && metricsRegisteredForRestClient.isEmpty()) {
                    LOGGER.log(DEBUG, "No metrics registered for REST client " + restClient.getCanonicalName());
                }
            });
        });
    }

    void doPreWork(Method method, ClientRequestContext context) {
        List<MetricsUpdateWork> workItems = metricsUpdateWorkByMethod.get(method);
        if (workItems != null) {
            workItems.forEach(workItem -> workItem.preWork(context));
        }
    }

    void doPostWork(Method method, ClientRequestContext context) {

        List<MetricsUpdateWork> workItems = metricsUpdateWorkByMethod.get(method);
        if (workItems != null) {
            workItems.forEach(workItem -> workItem.postWork(context));
        }
    }

    // For testing.
    Map<Method, List<MetricsUpdateWork>> metricsUpdateWorkByMethod() {
        return metricsUpdateWorkByMethod;
    }

    Set<Class<?>> typeClosure(Class<?> type) {
        Set<Class<?>> result = new HashSet<>();
        typeClosure(result, type);
        return result;
    }

    void typeClosure(Set<Class<?>> result, Class<?> type) {
        if (type == null || result.contains(type)) {
            return;
        }
        result.add(type);
        typeClosure(result, type.getSuperclass());
        for (Class<?> c : type.getInterfaces()) {
            typeClosure(result, c);
        }
    }

    /**
     * Adds meter registrations for types that are REST clients but were not discovered by CDI processing ahead of time.
     *
     * @param type the Java type now known to be a REST client
     */
    private void addRegistrations(Class<?> type) {
        this.<Class<?>, Method>addRegistrations(type,
                              typeClosure(type),
                              Class::getAnnotation,
                              Method::getAnnotation,
                              Function.identity(),
                              Function.identity(),
                              (Class<?> c) -> List.of(c.getMethods()),
                              (Method m) -> List.of(m.getAnnotations())
                              );
    }

    /**
     * Adds meter registrations for types recognized as REST clients which were discovered by CDI observer methods.
     *
     * @param abd the {@link jakarta.enterprise.inject.spi.AfterBeanDiscovery} object that knows about all discovered types
     * @param type the Java type corresponding to the {@link jakarta.enterprise.inject.spi.AnnotatedType} discovered to be a
     *             REST client by virtual of annotations
     */
    private void addRegistrations(AfterBeanDiscovery abd, Class<?> type) {

        // Earlier we collected all interfaces with REST annotations. For each of the types in its closure:
        //   * Capture any type-level REST annotations - these will apply to all REST methods on or inherited by the
        //     interface being processed.
        //   * For each REST method on the interface or on a type in its type closure use the method-level REST annotations
        //     and any type-level REST annotations to prepare metric registrations.

        // We do not know the ID with which each annotated type was registered with CDI. So we ask CDI for all annotated types
        // that match the Java type of interest.

        this.addRegistrations(type,
                              StreamSupport.stream(abd.getAnnotatedTypes(type).spliterator(), false)
                                      .flatMap(at -> at.getTypeClosure().stream())
                                      .filter(t -> t instanceof Class<?>)
                                      .map(t -> (Class<?>) t)
                                      .filter(candidateRestClientTypes::contains)
                                      .flatMap(t -> StreamSupport.stream(abd.getAnnotatedTypes(t).spliterator(), false))
                                      .collect(Collectors.toSet()),
                              AnnotatedType::getAnnotation,
                              (AnnotatedMethod<?> am, Class<? extends Annotation> annoClass) -> am.getAnnotation(annoClass),
                              AnnotatedType::getJavaClass,
                              AnnotatedMethod::getJavaMember,
                              (AnnotatedType at) -> at.getMethods(),
                              AnnotatedMethod::getAnnotations);

    }

    /**
     * Computes and adds metrics registrations for the methods on or inherited by the specified type.
     * <p>
     * The same logic applies whether we are processing CDI-discovered types or just-in-time discovered Java types. But
     * the two APIs are not the same, so this method accepts several functions specific to the API the caller uses.
     *
     * @param type Java type of interest
     * @param typeClosure type closure expressed in the calling API's TYPE
     * @param annotationFromType function to retrieve a given annotation from the API's TYPE
     * @param annotationFromMethod function to retrieve a given annotation from the API's METHOD
     * @param typeToClass converts the API's TYPE to the underlying Java Class
     * @param methodToMethod converts the API's METHOD to the underlying Java Method
     * @param methodsForType function to returns METHOD instances for a given TYPE instance
     * @param annotationsForMethod function to return all annotations from a given METHOD instance
     * @param <TYPE> the "type type" for the calling API
     * @param <METHOD> the method type for the calling API
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private <TYPE, METHOD> void addRegistrations(Class<?> type,
                                                 Iterable<TYPE> typeClosure,
                                                 BiFunction<TYPE, Class<? extends Annotation>, Annotation> annotationFromType,
                                                 BiFunction<METHOD, Class<? extends Annotation>, Annotation> annotationFromMethod,
                                                 Function<TYPE, Class<?>> typeToClass,
                                                 Function<METHOD, Method> methodToMethod,
                                                 Function<TYPE, Collection<METHOD>> methodsForType,
                                                 Function<METHOD, Collection<Annotation>> annotationsForMethod) {

        LOGGER.log(DEBUG, "Analyzing REST client interface " + type.getCanonicalName());

        Set<Annotation> typeLevelMetricAnnotationsOverTypeClosure =
                StreamSupport.stream(typeClosure.spliterator(), false)
                        .flatMap(t -> METRICS_ANNOTATIONS.stream()
                                .map(annoType -> annotationFromType.apply(t, annoType))
                                .filter(Objects::nonNull))
                        .collect(Collectors.toSet());

        Map<Method, Set<Registration>> registrationsByMethodForType = new HashMap<>();

        StreamSupport.stream(typeClosure.spliterator(), false)
                .forEach(typeInClosure -> {
                    LOGGER.log(DEBUG,
                               "Examining type " + typeToClass.apply(typeInClosure));
                    methodsForType.apply(typeInClosure).stream()
                            .filter(m -> RestClientMetricsCdiExtension.hasRestAnnotation(annotationsForMethod.apply(m)))
                            .forEach(m -> {
                                Collection<Annotation> annotationsForThisMethod = annotationsForMethod.apply(m);
                                if (RestClientMetricsCdiExtension.hasRestAnnotation(annotationsForThisMethod)) {
                                    Method javaMethod = methodToMethod.apply(m);
                                    Set<Registration> registrationsForMethod = registrationsByMethodForType.computeIfAbsent(
                                            javaMethod,
                                            k -> new HashSet<>());

                                    Set<Registration> registrationsFromMethod =
                                            METRICS_ANNOTATIONS.stream()
                                                    .map(metricAnnoType -> annotationFromMethod.apply(m, metricAnnoType))
                                                    .filter(Objects::nonNull)
                                                    .map(metricAnno -> Registration.create(
                                                            type,
                                                            javaMethod,
                                                            metricAnno,
                                                            false))
                                                    .collect(Collectors.toSet());

                                    registrationsForMethod.addAll(registrationsFromMethod);

                                    LOGGER.log(DEBUG,
                                               "Adding metric registrations for annotations "
                                                       + "on method " + javaMethod
                                                       .getDeclaringClass().getCanonicalName()
                                                       + "." + javaMethod.getName()
                                                       + ":" + registrationsFromMethod);


                                    // Record registrations needed for this method based on
                                    // type-level annotations.

                                    var registrationsFromTypeLevelAnnotations =
                                            typeLevelMetricAnnotationsOverTypeClosure.stream()
                                                    .map(anno -> Registration.create(
                                                            type,
                                                            javaMethod,
                                                            anno,
                                                            true))
                                            .collect(Collectors.toSet());

                                    registrationsForMethod.addAll(registrationsFromTypeLevelAnnotations);
                                    LOGGER.log(DEBUG,
                                               "Adding metric registrations for type-level annotations"
                                                       + registrationsFromTypeLevelAnnotations);
                                }

                            });
                    registrations.put(type, registrationsByMethodForType);

                });
    }

    private static Tag[] tags(Annotation metricAnnotation) {
        return switch (metricAnnotation) {
            case Counted counted -> tags(counted.tags());
            case Timed timed -> tags(timed.tags());
            default -> null;
        };
    }

    /**
     * Converts tag expressions in a metrics annotation to an array of {@link org.eclipse.microprofile.metrics.Tag} for use
     * during metric registration.
     *
     * @param tagExprs tag expressions (tag=value) from the metrics annotation
     * @return tag array
     */
    private static Tag[] tags(String[] tagExprs) {
        return Stream.of(tagExprs)
                .map(tagExpr -> {
                    int eq = tagExpr.indexOf("=");
                    if (eq <= 0 || eq == tagExpr.length() - 1) {
                        throw new IllegalArgumentException("Tag expression "
                                                                   + tagExpr
                                                                   + " in annotation has missing or misplaced = sign.");
                    }
                    return new Tag(tagExpr.substring(0, eq).trim(), tagExpr.substring(eq).trim());
                })
                .toArray(Tag[]::new);
    }

    private static boolean hasRestAnnotation(Collection<Annotation> annotations) {
        return annotations.stream()
                .anyMatch(anno -> REST_METHOD_ANNOTATIONS.contains(anno.annotationType()));
    }

    private static String chooseMetricName(Class<?> type,
                                           Method method,
                                           Annotation metricAnnotation,
                                           boolean isTypeLevel) {

        boolean isAbsolute = switch (metricAnnotation) {
            case Timed timed -> timed.absolute();
            case Counted counted -> counted.absolute();
            default -> false;
        };
        String specifiedName = switch (metricAnnotation) {
            case Timed timed -> timed.name();
            case Counted counted -> counted.name();
            default -> "";
        };
        Class<?> declaringClass = method.getDeclaringClass();

        // The following code mimics the structure of the Annotated Naming Convention tables in the MP Metrics spec document.
        return !isTypeLevel
                ? // Annotation is at the method level.
                isAbsolute
                        ? (
                        specifiedName.isEmpty()
                                ? method.getName()
                                : specifiedName)
                        : // Non-absolute name at the method level always has the canonical class name as its prefix.
                                declaringClass.getCanonicalName()
                                        + "."
                                        + (
                                        specifiedName.isEmpty()
                                                ? method.getName()
                                                : specifiedName)

                : // Annotation is at the type level. Choose the prefix; the metric name always ends with the method name.
                        (
                                isAbsolute
                                        ? (
                                        specifiedName.isEmpty() ? type.getSimpleName()
                                                : specifiedName)
                                        : (
                                                specifiedName.isEmpty() ? declaringClass.getCanonicalName()
                                                        : declaringClass.getPackageName() + "." + specifiedName))
                                + "." + method.getName();
    }

    /**
     * Metrics update work to be performed by a filter to update a metric, consisting of either of both of pre-work (metrics
     * work to be done before the operation is performed) and post-work (metrics work to be done after the operation completes).
     *
     * @param preWork  metrics work to do before the operation runs
     * @param postWork metrics work to do after the operation runs
     */
    record MetricsUpdateWork(Consumer<ClientRequestContext> preWork, Consumer<ClientRequestContext> postWork) {

        static MetricsUpdateWork create(Metric metric) {
            return switch (metric) {
                case Timer timer -> MetricsUpdateWork.create(cctx -> cctx.setProperty(SAVED_START_TIME_PROPERTY_NAME,
                                                                                      System.nanoTime()),
                                                             cctx -> {
                                                                 long startTime =
                                                                         (Long) cctx.getProperty(SAVED_START_TIME_PROPERTY_NAME);
                                                                 timer.update(Duration.ofNanos(System.nanoTime() - startTime));
                                                             });
                case Counter counter -> MetricsUpdateWork.create(cctx -> counter.inc());
                default -> null;
            };
        }

        void preWork(ClientRequestContext requestContext) {
            if (preWork != null) {
                preWork.accept(requestContext);
            }
        }

        void postWork(ClientRequestContext requestContext) {
            if (postWork != null) {
                postWork.accept(requestContext);
            }
        }

        private static MetricsUpdateWork create(Consumer<ClientRequestContext> preWork, Consumer<ClientRequestContext> postWork) {
            return new MetricsUpdateWork(preWork, postWork);
        }

        private static MetricsUpdateWork create(Consumer<ClientRequestContext> preWork) {
            return new MetricsUpdateWork(preWork, null);
        }
    }

    /**
     * A future group of metric registrations to be performed if and when the corresponding REST client interface is created.
     *
     * @param metricName       metric name
     * @param metricAnnotation metric annotation which gave rise to this registration
     * @param registrationOp   function to register the new metric in a metric registry
     */
    private record Registration(String metricName,
                                Tag[] tags,
                                Metadata metadata,
                                Annotation metricAnnotation,
                                Function<MetricRegistry, ? extends Metric> registrationOp) {

        static Registration create(Class<?> declaringType,
                                   Method method,
                                   Annotation metricAnnotation,
                                   boolean isTypeLevel) {
            Metadata metadata = Metadata.builder()
                    .withName(chooseMetricName(declaringType, method, metricAnnotation, isTypeLevel))
                    .withDescription("REST client "
                                             + (
                            metricAnnotation.annotationType().isAssignableFrom(Timed.class)
                                    ? "timer"
                                    : "counter")
                                             + declaringType.getSimpleName()
                                             + "."
                                             + method.getName())
                    .build();

            Tag[] tagsFromAnnotation = RestClientMetricsCdiExtension.tags(metricAnnotation);
            return switch (metricAnnotation) {
                case Timed timed -> new Registration(metadata.getName(),
                                                     tagsFromAnnotation,
                                                     metadata,
                                                     timed,
                                                     mr -> mr.timer(metadata, tagsFromAnnotation));
                case Counted counted -> new Registration(metadata.getName(),
                                                         tagsFromAnnotation,
                                                         metadata,
                                                         counted,
                                                         mr -> mr.counter(metadata, tagsFromAnnotation));
                default -> null;
            };
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Registration.class.getSimpleName() + "[", "]")
                    .add("metadata=" + metadata)
                    .add("tags=" + Arrays.toString(tags))
                    .add("metricAnnotation=" + metricAnnotation)
                    .toString();
        }
    }
}
