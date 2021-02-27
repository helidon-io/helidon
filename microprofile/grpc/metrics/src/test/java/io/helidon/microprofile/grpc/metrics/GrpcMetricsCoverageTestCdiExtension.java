/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.grpc.metrics;

import io.helidon.microprofile.grpc.core.GrpcMethod;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

public class GrpcMetricsCoverageTestCdiExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(GrpcMetricsCoverageTestCdiExtension.class.getName());

    private final Set<Class<? extends Annotation>> metricsAnnotationsUsed = new HashSet<>();

    private final Map<AnnotatedMethod<?>, Set<Class<? extends Annotation>>> remainingTestBeanMethodAnnotations =
            new HashMap<>();

    private final Set<Class<? extends Annotation>> annotationClassesNotCoveredByTestBeans = new HashSet<>();

    void before(@Observes BeforeBeanDiscovery bbd) {

        metricsAnnotationsUsed.clear();
        remainingTestBeanMethodAnnotations.clear();
        annotationClassesNotCoveredByTestBeans.clear();

        String testBeanClassBaseName = CoverageTestBeanBase.class.getName();
        String testBeanClassNamePrefix = testBeanClassBaseName.substring(0, testBeanClassBaseName.indexOf("Base"));

        GrpcMetricsCdiExtension.METRICS_ANNOTATIONS_TO_CHECK.forEach(annotationClass -> {
            String testBeanClassName = testBeanClassNamePrefix + annotationClass.getSimpleName();
            try {
                Class<?> testBeanClass = Class.forName(testBeanClassName);
                bbd.addAnnotatedType(testBeanClass, testBeanClassName);
            } catch (ClassNotFoundException e) {
                annotationClassesNotCoveredByTestBeans.add(annotationClass);
            }
        });
    }

    /**
     * Collects all the metrics annotations that are used on methods in the {@code CoverageTestBeanXXX} classes.
     * <p>
     *     A test retrieves this collection and makes sure that all the metric annotations known to the main Helidon metrics
     *     module are accounted for on methods on the test beans. That's because another test makes sure that the gRPC
     *     extension, which removes metrics annotations from methods that also have {@code @GrpcMethod}, correctly removes all
     *     of those metrics annotations. We want to make sure that all known annotations are covered by the test beans so we
     *     can check that the gRPC extension is properly handling all of them.
     * </p>
     * <p>
     *     And, because the main gRPC extension removes metrics annotations that way, this observer needs to run before that
     *     one so we get an accurate collection of metrics annotations actually used in the test bean class.
     * </p>
     * @param pat
     */
    void recordMetricsAnnotationsOnTestBean(@Observes @Priority(GrpcMetricsCdiExtension.OBSERVER_PRIORITY - 10)
                @WithAnnotations(GrpcMethod.class) ProcessAnnotatedType<? extends CoverageTestBeanBase> pat) {

        pat.getAnnotatedType()
            .getMethods()
            .forEach(m -> {
                    Set<Class<? extends Annotation>> metricsAnnotationClassesForThisMethod =
                            m.getAnnotations().stream()
                                    .map(Annotation::annotationType)
                                    .collect(Collectors.toSet());

                    metricsAnnotationClassesForThisMethod.retainAll(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS_TO_CHECK);
                    LOGGER.log(Level.FINE, () -> String.format("Recording annotation(s) %s on %s",
                            metricsAnnotationClassesForThisMethod, m.getJavaMember().toString()));
                    metricsAnnotationsUsed.addAll(metricsAnnotationClassesForThisMethod);
                });
    }

    /**
     * Tracks which methods on the test bean still have any metrics annotations, given that the gRPC extension should have
     * removed them.
     *
     * @param pmb the ProcessManagedBean event
     */
    void checkForMetricsAnnotations(@Observes ProcessManagedBean<? extends CoverageTestBeanBase> pmb) {

        pmb.getAnnotatedBeanClass().getMethods().forEach(m -> {
                    Set<Class<? extends Annotation>> remainingMetricsAnnotationsForThisMethod =
                            m.getAnnotations().stream()
                                    .map(Annotation::annotationType)
                                    .collect(Collectors.toSet());

                    remainingMetricsAnnotationsForThisMethod.retainAll(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS_TO_CHECK);
                    if (!remainingMetricsAnnotationsForThisMethod.isEmpty()) {
                        remainingTestBeanMethodAnnotations.put(m, remainingMetricsAnnotationsForThisMethod);
                    }
                });
    }

    Map<AnnotatedMethod<?>, Set<Class<? extends Annotation>>> remainingTestBeanMethodAnnotations() {
        return remainingTestBeanMethodAnnotations;
    }

    Set<Class<? extends Annotation>> metricsAnnotationsUsed() {
        return metricsAnnotationsUsed;
    }

    Set<Class<? extends Annotation>> annotationClassesNotCoveredByTestBeans() {
        return annotationClassesNotCoveredByTestBeans;
    }
}
