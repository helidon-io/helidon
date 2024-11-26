/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
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
import jakarta.ws.rs.client.ClientRequestContext;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

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

    private final Set<Class<?>> candidateRestClientTypes = new HashSet<>();

    private final Map<Method, List<MetricsUpdateWork>> metricsUpdateWorkByMethod = new HashMap<>();

    private final Map<Class<?>, Map<Method, Set<Registration>>> registrations = new HashMap<>();

    private MetricRegistry metricRegistry;

    /**
     * For service loading.
     */
    public RestClientMetricsCdiExtension() {
    }

    void checkForMpMetrics(@Observes BeforeBeanDiscovery bbd) {

    }

    void recordRestClientTypes(@Observes @WithAnnotations({OPTIONS.class,
            HEAD.class,
            GET.class,
            POST.class,
            PUT.class,
            DELETE.class,
            PATCH.class}) ProcessAnnotatedType<?> pat) {
        if (pat.getAnnotatedType().getJavaClass().isInterface()) {
            /*
            All REST client declarations are interfaces, so at least this annotated type has a chance of being a REST client.
            At this stage in processing simply record all classes with at least one method bearing a REST annotation.
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
        // We do not actually register the metrics yet; instead we want until the REST client infrastructure informs us type
        // by type that an interface is being used as a REST client interface.

        candidateRestClientTypes.forEach(type -> {

            LOGGER.log(DEBUG, "Analyzing candidate REST client interface " + type.getCanonicalName());

            // Earlier we collected all interfaces with REST annotations. For each of those compute the type closure and, for the
            // type itself and all types in its closure:
            //   * capture any type-level REST annotations - these will apply to all REST methods on the interface being processed
            //   * for each REST method on the interface or on a type in its type closure use the method-level REST annotations
            //     and any type-level REST annotations to prepare metric registrations.

            Set<Annotation> typeLevelMetricAnnotationsOverTypeClosure =
                    StreamSupport.stream(abd.getAnnotatedTypes(type).spliterator(), false)
                            .flatMap(at -> at.getTypeClosure()
                                    .stream()) // The CDI-provided type closure includes the type itself.
                            .filter(t -> t instanceof Class<?>)
                            .map(t -> (Class<?>) t)
                            .filter(candidateRestClientTypes::contains)
                            .flatMap(t -> StreamSupport.stream(abd.getAnnotatedTypes(t).spliterator(), false))
                            .flatMap(at -> Stream.of(Timed.class, Counted.class)
                                    .map(at::getAnnotation)
                                    .filter(Objects::nonNull))
                            .collect(Collectors.toSet());

            Map<Method, Set<Registration>> registrationsByMethodForType = new HashMap<>();

            // We need to get the AnnotatedType for the type of interest, but without knowing the ID with which it was added
            // to CDI we cannot retrieve just that one directly. Instead retrieve all annotated types for the type (most likely
            // there will be just one).
            StreamSupport.stream(abd.getAnnotatedTypes(type).spliterator(), false)
                    .flatMap(at -> at.getTypeClosure().stream()) // The CDI-provided type closure includes the type itself.
                    .filter(t -> t instanceof Class<?>)
                    .map(t -> (Class<?>) t)
                    .filter(candidateRestClientTypes::contains)
                    .forEach(typeInClosure -> StreamSupport.stream(abd.getAnnotatedTypes(typeInClosure).spliterator(),
                                                                   false)
                            .forEach(annotatedTypeInClosure -> {
                                         LOGGER.log(DEBUG,
                                                    "Examining type " + annotatedTypeInClosure.getJavaClass()
                                                            .getCanonicalName());

                                          annotatedTypeInClosure.getMethods().stream()
                                                 .filter(RestClientMetricsCdiExtension::hasRestAnnotation)
                                                 .forEach(annotatedMethod -> {
                                                     // Record registrations needed for this method based on
                                                     // annotations on it or the types in this type's closure.
                                                     Set<Registration> registrationsForMethod =
                                                             registrationsByMethodForType.computeIfAbsent(
                                                                     annotatedMethod.getJavaMember(),
                                                                     k -> new HashSet<>());
                                                     Set<Registration> registrationsFromMethod =
                                                             Stream.of(Timed.class,
                                                                       Counted.class)
                                                                     .map(annotatedMethod::getAnnotation)
                                                                     .filter(Objects::nonNull)
                                                                     .map(anno -> Registration.create(
                                                                             annotatedTypeInClosure.getJavaClass(),
                                                                             annotatedMethod,
                                                                             anno,
                                                                             false))
                                                                     .collect(Collectors.toSet());

                                                     registrationsForMethod.addAll(registrationsFromMethod);

                                                     LOGGER.log(DEBUG,
                                                                "Adding metric registrations for annotations "
                                                                        + "on method " + annotatedMethod.getJavaMember()
                                                                        .getDeclaringClass().getCanonicalName()
                                                                        + "." + annotatedMethod.getJavaMember()
                                                                        .getName() + ":" + registrationsFromMethod);

                                                     // Record registrations needed for this method based on
                                                     // type-level annotations.

                                                     var registrationsFromTypeLevelAnnotations =
                                                             typeLevelMetricAnnotationsOverTypeClosure.stream()
                                                                     .map(anno -> Registration.create(
                                                                             annotatedTypeInClosure.getJavaClass(),
                                                                             annotatedMethod,
                                                                             anno,
                                                                             true))
                                                                     .collect(Collectors.toSet());

                                                     registrationsForMethod.addAll(registrationsFromTypeLevelAnnotations);
                                                     LOGGER.log(DEBUG,
                                                                "Adding metric registrations for type-level "
                                                                        + "annotations " + registrationsFromTypeLevelAnnotations);
                                                 });
                                         registrations.put(type, registrationsByMethodForType);
                                     }
                            ));
        });
    }

    void ready(@Observes @Initialized(ApplicationScoped.class) Object event,
               MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    void registerMetricsForRestClient(Class<?> restClient) {
        registrations.get(restClient).forEach((method, regs) -> {
            List<Metric> metricsRegisteredForRestClient = LOGGER.isLoggable(DEBUG) ? new ArrayList<>() : null;
            regs.forEach(registration -> {
                Metric metric = registration.registrationOp.apply(metricRegistry);
                if (LOGGER.isLoggable(DEBUG)) {
                    metricsRegisteredForRestClient.add(metric);
                    LOGGER.log(DEBUG, String.format("For REST client method %s#%s registered metric %s",
                                                    restClient.getCanonicalName(),
                                                    method.getName(),
                                                    metric));
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

    private static boolean hasRestAnnotation(AnnotatedMethod<?> am) {
        return am.getAnnotations().stream()
                .anyMatch(anno -> REST_METHOD_ANNOTATIONS.contains(anno.annotationType()));
    }

    private static String chooseMetricName(Class<?> type,
                                           AnnotatedMethod<?> method,
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
        AnnotatedType<?> declaringType = method.getDeclaringType();

        // The following code mimics the structure of the Annotated Naming Convention tables in the MP Metrics spec document.
        return !isTypeLevel
                ? // Annotation is at the method level.
                isAbsolute
                        ? (
                        specifiedName.isEmpty()
                                ? method.getJavaMember().getName()
                                : specifiedName)
                        : // Non-absolute name at the method level always has the canonical class name as its prefix.
                                declaringType.getJavaClass().getCanonicalName()
                                        + "."
                                        + (
                                        specifiedName.isEmpty()
                                                ? method.getJavaMember().getName()
                                                : specifiedName)

                : // Annotation is at the type level. Choose the prefix; the metric name always ends with the method name.
                        (
                                isAbsolute
                                        ? (
                                        specifiedName.isEmpty() ? type.getSimpleName()
                                                : specifiedName)
                                        : (
                                                specifiedName.isEmpty() ? declaringType.getJavaClass().getCanonicalName()
                                                        : declaringType.getJavaClass().getPackageName() + "." + specifiedName))
                                + "." + method.getJavaMember().getName();
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
     * A future group of metric registrations to be performed if and when the corresponding REST client interface is reated.
     *
     * @param metricName       metric name
     * @param metricAnnotation metric annotation which gave rise to this registration
     * @param registrationOp   function to register the new metric in a metric registry
     */
    private record Registration(String metricName,
                                Annotation metricAnnotation,
                                Function<MetricRegistry, ? extends Metric> registrationOp) {

        static Registration create(Class<?> declaringType,
                                   AnnotatedMethod<?> method,
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
                                             + method.getJavaMember().getName())
                    .build();

            return switch (metricAnnotation) {
                case Timed timed ->
                        new Registration(metadata.getName(), metricAnnotation, mr -> mr.timer(metadata, tags(timed.tags())));
                case Counted counted ->
                        new Registration(metadata.getName(), metricAnnotation, mr -> mr.counter(metadata, tags(counted.tags())));
                default -> null;
            };
        }
    }
}
