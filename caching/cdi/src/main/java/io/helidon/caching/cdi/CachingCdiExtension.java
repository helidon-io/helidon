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
 */

package io.helidon.caching.cdi;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedCallable;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.enterprise.util.AnnotationLiteral;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.CacheManager;
import io.helidon.caching.annotation.CacheGet;
import io.helidon.caching.annotation.CacheKey;
import io.helidon.caching.annotation.CacheName;
import io.helidon.caching.annotation.CacheValue;
import io.helidon.common.GenericType;
import io.helidon.config.Config;

public class CachingCdiExtension implements Extension {
    private final Map<String, Class<? extends CacheConfig<?, ?>>> cacheConfigs = new HashMap<>();
    private final Map<String, MethodInterceptorInfo> interceptorCache = new HashMap<>();
    private final Set<TypeAndName> types = new HashSet<>();

    MethodInterceptorInfo interceptorInfo(Method method) {
        return interceptorCache.get(methodCacheKey(method));
    }

    @SuppressWarnings("unchecked")
    void processConfigurators(@Observes ProcessBean<?> beanEvent) {
        CacheName cacheNameAnnot = beanEvent.getAnnotated().getAnnotation(CacheName.class);

        if (cacheNameAnnot == null) {
            return;
        }

        Type type = beanEvent.getAnnotated().getBaseType();
        if (type instanceof Class<?>) {
            if (CacheConfig.class.isAssignableFrom((Class<?>) type)) {
                String cacheName = cacheNameAnnot.value();
                Class<? extends CacheConfig<?, ?>> existingConfigurator = cacheConfigs.get(cacheName);
                if (existingConfigurator != null) {
                    throw new DeploymentException("There are two CacheConfigs defined for cache named \"" + cacheName + "\"."
                                                          + " " + existingConfigurator.getName() + ", and " + type);
                }
                this.cacheConfigs.put(cacheName, (Class<? extends CacheConfig<?, ?>>) type);
            }
        }
    }

    void bbd(@Observes BeforeBeanDiscovery bbd) {
        bbd.addInterceptorBinding(CacheGet.class);
        bbd.addAnnotatedType(InterceptCacheGet.class, InterceptCacheGet.class.getName())
                .add(CacheGetLiteral.INSTANCE)
                .add(Dependent.Literal.INSTANCE);
    }

    void abd(@Observes AfterBeanDiscovery abd) {
        abd.addBean()
                .addType(Caches.class)
                .id(Caches.class.getName())
                .scope(ApplicationScoped.class)
                .produceWith(instance -> {
                    Map<String, CacheConfig<?, ?>> cacheConfigInstances = new HashMap<>();

                    for (Map.Entry<String, Class<? extends CacheConfig<?, ?>>> entry : cacheConfigs.entrySet()) {
                        cacheConfigInstances.put(entry.getKey(), instance.select(entry.getValue()).get());
                    }
                    Config config = instance.select(Config.class).get();
                    CacheManager cacheManager = CacheManager.create(config.get("caches"));
                    return new Caches(cacheManager, cacheConfigInstances);
                });

        for (TypeAndName typeAndName : types) {
            Type type = typeAndName.type;
            String name = typeAndName.name;

            abd.addBean()
                    .addType(type)
                    .addQualifier(CacheNameLiteral.create(name))
                    .id("cache-" + name + "-" + type)
                    .scope(Dependent.class)
                    .produceWith(instance -> instance.select(Caches.class)
                            .get()
                            .cache(name));
        }
    }

    <T> void pat(@Observes @WithAnnotations(CacheGet.class) ProcessAnnotatedType<T> pat) {
        AnnotatedTypeConfigurator<T> annotatedTypeConfigurator = pat.configureAnnotatedType();
        AnnotatedType<T> annotatedType = annotatedTypeConfigurator.getAnnotated();
        Set<AnnotatedMethodConfigurator<? super T>> methods = annotatedTypeConfigurator.methods();

        for (AnnotatedMethodConfigurator<? super T> methodConfigurator : methods) {
            AnnotatedMethod<? super T> method = methodConfigurator.getAnnotated();
            if (method.getAnnotation(CacheGet.class) != null) {
                // find everything then cache it in extension, inject to interceptor, use cached value
                // so we do not need to look it up on every execution
                String cacheNameDef = cacheName(annotatedType, method, null);
                Function<Object[], Object> cacheKeyFunction = cacheKeyFunction(method);
                this.interceptorCache.put(methodCacheKey(method.getJavaMember()),
                                          new MethodInterceptorInfo(cacheNameDef, cacheKeyFunction));
            }
        }
    }

    void pip(@Observes ProcessInjectionPoint<?, ?> pip) {
        InjectionPoint injectionPoint = pip.getInjectionPoint();
        Type type = injectionPoint.getType();
        GenericType<?> gt = GenericType.create(type);
        Class<?> aClass = gt.rawType();
        if (aClass.equals(Cache.class)) {
            Annotated annotated = injectionPoint.getAnnotated();
            String name;

            if (annotated instanceof AnnotatedField) {
                // field
                AnnotatedField<?> annotatedField = (AnnotatedField<?>) annotated;
                AnnotatedType<?> annotatedType = annotatedField.getDeclaringType();
                name = cacheName(annotatedType, null, annotatedField);

            } else {
                // parameter
                AnnotatedParameter<?> annotatedParameter = (AnnotatedParameter<?>) annotated;
                AnnotatedCallable<?> callable = annotatedParameter.getDeclaringCallable();
                AnnotatedType<?> annotatedType = callable.getDeclaringType();
                name = cacheName(annotatedType, callable, annotatedParameter);
            }
            types.add(new TypeAndName(name, type));

            pip.configureInjectionPoint().addQualifier(CacheNameLiteral.create(name));
        }
    }

    private String methodCacheKey(Method method) {
        return method.getDeclaringClass().getName()
                + "." + method.getName()
                + "(" + Arrays.toString(method.getParameterTypes()) + ")";
    }

    private Function<Object[], Object> cacheKeyFunction(AnnotatedMethod<?> method) {
        List<? extends AnnotatedParameter<?>> parameters = method.getParameters();
        List<Integer> keyIndices = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            AnnotatedParameter<?> annotatedParameter = parameters.get(i);
            if (annotatedParameter.getAnnotation(CacheKey.class) != null) {
                keyIndices.add(i);
            }
            if (annotatedParameter.getAnnotation(CacheValue.class) == null) {
                allIndices.add(i);
            }
        }
        if (keyIndices.isEmpty()) {
            // all parameters except for @CacheValue are keys
            keyIndices = allIndices;
        }
        if (keyIndices.size() == 1) {
            int index = keyIndices.iterator().next();
            return paramValues -> paramValues[index];
        }

        Integer[] indices = keyIndices.toArray(new Integer[0]);
        return paramValues -> new CompositeKey(paramValues, indices);
    }

    private String cacheName(Annotated annotatedType, Annotated callable, Annotated fieldOrParam) {
        CacheName found = annotatedType == null ? null : annotatedType.getAnnotation(CacheName.class);

        if (callable != null) {
            CacheName cacheName = callable.getAnnotation(CacheName.class);
            if (cacheName != null) {
                found = cacheName;
            }
        }
        if (fieldOrParam != null) {
            CacheName cacheName = fieldOrParam.getAnnotation(CacheName.class);
            if (cacheName != null) {
                found = cacheName;
            }
        }

        if (found == null) {
            throw new DeploymentException("CacheName must be defined either on type, method/constructor, or field/parameter."
                                                  + " Type " + annotatedType
                                                  + ", callable: " + callable
                                                  + ", field/param: " + fieldOrParam);
        }
        return found.value();
    }

    static class MethodInterceptorInfo {
        private final String cacheName;
        private final Function<Object[], Object> cacheKeyFunction;

        private MethodInterceptorInfo(String cacheName, Function<Object[], Object> cacheKeyFunction) {
            this.cacheName = cacheName;
            this.cacheKeyFunction = cacheKeyFunction;
        }

        String cacheName() {
            return cacheName;
        }

        Function<Object[], Object> cacheKeyFunction() {
            return cacheKeyFunction;
        }
    }

    private static final class CacheNameLiteral extends AnnotationLiteral<CacheNameQualifier> implements CacheNameQualifier {
        private final String value;

        private CacheNameLiteral(String value) {
            this.value = value;
        }

        static CacheNameLiteral create(String name) {
            return new CacheNameLiteral(name);
        }

        @Override
        public String value() {
            return value;
        }
    }

    private static final class CacheGetLiteral extends AnnotationLiteral<CacheGet> implements CacheGet {
        private static final CacheGetLiteral INSTANCE = new CacheGetLiteral();
    }

    private static final class CompositeKey {
        private final Object[] keys;

        private CompositeKey(Object[] paramValues, Integer[] indices) {
            this.keys = new Object[indices.length];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = paramValues[indices[i]];
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CompositeKey that = (CompositeKey) o;
            return Arrays.deepEquals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(keys);
        }
    }

    private static final class TypeAndName {
        private final String name;
        private final Type type;

        private TypeAndName(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeAndName that = (TypeAndName) o;
            return name.equals(that.name) && type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }
    }
}
