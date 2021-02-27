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

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessManagedBean;

public class GrpcMetricsCoverageTestCdiExtension implements Extension {

    private final Map<AnnotatedMethod, Set<Class<? extends Annotation>>> remainingTestBeanMethodAnnotations =
            new HashMap<>();

    void addGeneratedBeans(@Observes BeforeBeanDiscovery bbd) {

        remainingTestBeanMethodAnnotations.clear();

        AtomicReference<TestMetricsCoverage.GeneratedBeanCatalog> catalogRef = new AtomicReference<>();

        /*
         * Make the generated beans known to CDI. The catalog of generated beans was itself generated, so load it as a service
         * and retrieve its list of generated bean classes.
         */
        ServiceLoader.load(TestMetricsCoverage.GeneratedBeanCatalog.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(catalogRef::set)
                .map(TestMetricsCoverage.GeneratedBeanCatalog::generatedBeanClasses)
                .flatMap(List::stream)
                .forEach(beanClass -> bbd.addAnnotatedType(beanClass, beanClass.getSimpleName()));

        // Tell CDI about the catalog itself in case a test class wants to inject it.
        bbd.addAnnotatedType(catalogRef.get().getClass(), catalogRef.get().getClass().getSimpleName());
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

    Map<AnnotatedMethod, Set<Class<? extends Annotation>>> remainingTestBeanMethodAnnotations() {
        return remainingTestBeanMethodAnnotations;
    }
}
