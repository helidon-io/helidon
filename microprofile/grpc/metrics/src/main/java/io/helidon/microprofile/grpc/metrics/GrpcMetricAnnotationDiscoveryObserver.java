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

import io.helidon.common.LazyValue;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.metrics.MetricAnnotationDiscovery;
import io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver;

import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;

/**
 * The gRPC implementation of {@link io.helidon.microprofile.metrics.spi.MetricAnnotationDiscoveryObserver}.
 */
class GrpcMetricAnnotationDiscoveryObserver implements MetricAnnotationDiscoveryObserver {

    private static final LazyValue<GrpcMetricAnnotationDiscoveryObserver> INSTANCE =
            LazyValue.create(GrpcMetricAnnotationDiscoveryObserver::new);

    private final Map<Method,
            Map<Class<? extends Annotation>, MetricAnnotationDiscovery.OfMethod>> discoveriesByMethod =
            new HashMap<>();


    private GrpcMetricAnnotationDiscoveryObserver() {
    }


    static GrpcMetricAnnotationDiscoveryObserver instance() {
        return INSTANCE.get();
    }

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

    static Map<Class<? extends Annotation>, MetricAnnotationDiscovery.OfMethod> discoveries(Method method) {
        return INSTANCE.get().discoveriesByMethod.get(method);
    }

    static boolean isDiscovered(Method method) {
        return INSTANCE.get().discoveriesByMethod.containsKey(method);
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
