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
package io.helidon.microprofile.cdi;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import io.helidon.common.LazyValue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;

/**
 * CDI extension to support the {@link OnNewThread} annotation.
 */
public class OnNewThreadExtension implements Extension {

    private final LazyValue<Map<Method, AnnotatedMethod<?>>> methodMap = LazyValue.create(ConcurrentHashMap::new);

    void registerMethods(BeanManager bm, @Observes ProcessSyntheticBean<?> event) {
        registerMethods(bm.createAnnotatedType(event.getBean().getBeanClass()));
    }

    void registerMethods(@Observes ProcessManagedBean<?> event) {
        registerMethods(event.getAnnotatedBeanClass());
    }

    private void registerMethods(AnnotatedType<?> type) {
        for (AnnotatedMethod<?> annotatedMethod : type.getMethods()) {
            if (annotatedMethod.isAnnotationPresent(OnNewThread.class)) {
                methodMap.get().put(annotatedMethod.getJavaMember(), annotatedMethod);
            }
        }
    }

    void validateAnnotations(BeanManager bm, @Observes @Initialized(ApplicationScoped.class) Object event) {
        methodMap.get().forEach((method, annotatedMethod) -> validateExecutor(bm, annotatedMethod));
    }

    private static void validateExecutor(BeanManager bm, AnnotatedMethod<?> method) {
        OnNewThread onNewThread = method.getAnnotation(OnNewThread.class);
        if (onNewThread.value() == OnNewThread.ThreadType.EXECUTOR) {
            String executorName = onNewThread.executorName();
            Set<Bean<?>> beans = bm.getBeans(ExecutorService.class, NamedLiteral.of(executorName));
            if (beans.isEmpty()) {
                throw new IllegalArgumentException("Unable to resolve named executor service '"
                        + onNewThread.value() + "' at "
                        + method.getJavaMember().getDeclaringClass().getName() + "::"
                        + method.getJavaMember().getName());
            }
        }
    }

    OnNewThread getAnnotation(Method method) {
        AnnotatedMethod<?> annotatedMethod = methodMap.get().get(method);
        if (annotatedMethod != null) {
            return annotatedMethod.getAnnotation(OnNewThread.class);
        }
        throw new IllegalArgumentException("Unable to map method " + method);
    }

    void registerInterceptors(@Observes BeforeBeanDiscovery discovery, BeanManager bm) {
        discovery.addAnnotatedType(bm.createAnnotatedType(OnNewThreadInterceptor.class),
                OnNewThreadInterceptor.class.getName());
    }

    void clearMethodMap() {
        methodMap.get().clear();
    }
}
