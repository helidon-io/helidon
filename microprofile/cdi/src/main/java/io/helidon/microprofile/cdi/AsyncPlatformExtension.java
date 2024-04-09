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
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.LazyValue;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;

/**
 * CDI extension to support the {@link AsyncPlatform} annotation.
 */
public class AsyncPlatformExtension implements Extension {

    private final LazyValue<Map<Method, AnnotatedMethod<?>>> methodMap = LazyValue.create(ConcurrentHashMap::new);

    void registerMethods(BeanManager bm, @Observes ProcessSyntheticBean<?> event) {
        registerMethods(bm.createAnnotatedType(event.getBean().getBeanClass()));
    }

    void registerMethods(@Observes ProcessManagedBean<?> event) {
        registerMethods(event.getAnnotatedBeanClass());
    }

    private void registerMethods(AnnotatedType<?> type) {
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (method.isAnnotationPresent(AsyncPlatform.class)) {
                methodMap.get().put(method.getJavaMember(), method);
            }
        }
    }

    AsyncPlatform getAnnotation(Method method) {
        AnnotatedMethod<?> annotatedMethod = methodMap.get().get(method);
        if (annotatedMethod != null) {
            return annotatedMethod.getAnnotation(AsyncPlatform.class);
        }
        throw new IllegalArgumentException("Unable to map method " + method);
    }

    void registerInterceptors(@Observes BeforeBeanDiscovery discovery, BeanManager bm) {
        discovery.addAnnotatedType(bm.createAnnotatedType(AsyncPlatformInterceptor.class),
                AsyncPlatformInterceptor.class.getName());
    }

    void clearMethodMap() {
        methodMap.get().clear();
    }
}
