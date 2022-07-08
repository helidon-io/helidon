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
package io.helidon.microprofile.grpc.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver;

import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;

/**
 * The gRPC implementation of {@link io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver}.
 *
 * <p>
 *     This implementation
 * </p>
 */
class GrpcMetricAnnotationDiscoveryObserverImpl implements MetricAnnotationDiscoveryObserver {

    static GrpcMetricAnnotationDiscoveryObserverImpl instance() {
        return GrpcMetricAnnotationDiscoveryObserverImplFactory.instance();
    }

    private final Map<Method,
            Map<Class<? extends Annotation>, MetricAnnotationDiscovery.OfMethod>> discoveriesByMethod =
            new HashMap<>();

    @Override
    public void onDiscovery(MetricAnnotationDiscovery discovery) {
        if (discovery instanceof MetricAnnotationDiscovery.OfMethod methodDiscovery
            && isRpcMethod(methodDiscovery.configurator(), discovery.annotation().annotationType())) {

            if (!MetricsConfigurer.isServiceAnnotated(methodDiscovery.annotatedTypeConfigurator().getAnnotated().getJavaClass(),
                                                      methodDiscovery.configurator().getAnnotated().getJavaMember(),
                                                      discovery.annotation().annotationType())) {
                // This endpoint should not give rise to a gRPC-inspired metric.
                discovery.deactivate();
            } else {
                // This endpoint SHOULD give rise to a gRPC-inspired metric.
                discovery.disableDefaultInterceptor();
                discoveriesByMethod.computeIfAbsent(methodDiscovery.configurator().getAnnotated().getJavaMember(),
                                                    key -> new HashMap<>())
                        .putIfAbsent(discovery.annotation().annotationType(), methodDiscovery);
            }
        }
    }

    Map<Class<? extends Annotation>, MetricAnnotationDiscovery.OfMethod> discoveries(Method method) {
        return discoveriesByMethod.get(method);
    }

    boolean isDiscovered(Method method) {
        return discoveriesByMethod.containsKey(method);
    }

    /**
     * Determine whether a method is annotated with both a metrics annotation
     * and an annotation of type {@link io.helidon.microprofile.grpc.core.GrpcMethod}.
     *
     * @param configurator  the {@link jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator} representing
     *                      the annotated method
     *
     * @return {@code true} if the method is a timed gRPC method
     */
    private static boolean isRpcMethod(AnnotatedMethodConfigurator<?> configurator, Class<? extends Annotation> type) {
        AnnotatedMethod method = AnnotatedMethod.create(configurator.getAnnotated().getJavaMember());
        GrpcMethod rpcMethod = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        if (rpcMethod != null) {
            Annotation annotation = method.firstAnnotationOrMetaAnnotation(type);
            return annotation != null;
        }
        return false;
    }

}
