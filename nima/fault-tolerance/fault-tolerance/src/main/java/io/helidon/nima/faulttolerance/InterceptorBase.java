/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.InjectionException;
import io.helidon.inject.api.Interceptor;
import io.helidon.inject.api.InvocationContext;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
abstract class InterceptorBase<T> implements Interceptor {
    private final Map<CacheRecord, T> methodHandlerCache = new ConcurrentHashMap<>();
    private final Map<String, NamedResult<T>> namedHandlerCache = new ConcurrentHashMap<>();

    private final Services services;
    private final Class<T> ftType;
    private final TypeName annotationTypeName;

    InterceptorBase(Services services, Class<T> ftType, Class<? extends Annotation> annotationType) {
        this.services = services;
        this.ftType = ftType;
        this.annotationTypeName = TypeName.create(annotationType);
    }

    @Override
    public <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) {
        // these are our cache keys
        TypeName typeName = ctx.serviceTypeName();
        TypedElementInfo elementInfo = ctx.elementInfo();
        List<TypedElementInfo> params = ctx.elementArgInfo();

        CacheRecord cacheRecord = new CacheRecord(typeName, elementInfo.elementName(), params);
        T ftHandler = cachedHandler(elementInfo, cacheRecord);

        return invokeHandler(ftHandler, chain, args);
    }

    <V> V invokeHandler(T ftHandler, Chain<V> chain, Object[] args) {
        if (ftHandler instanceof FtHandler handler) {
            return handler.invoke(() -> chain.proceed(args));
        } else if (ftHandler instanceof FtHandlerTyped typed) {
            return (V) typed.invoke(() -> chain.proceed(args));
        }
        throw new IllegalStateException("Invalid use of " + getClass().getSimpleName()
                                                + ", handler type can only be " + FtHandler.class.getName()
                                                + ", or " + FtHandlerTyped.class.getName());
    }

    // caching is done by this abstract class
    T obtainHandler(TypedElementInfo elementInfo, CacheRecord cacheRecord) {
        throw new IllegalStateException("Interceptor implementation must either override proceed, or implement obtainHandler");
    }

    T cachedHandler(TypedElementInfo elementInfo, CacheRecord cacheRecord) {
        return methodHandlerCache.computeIfAbsent(cacheRecord, record -> obtainHandler(elementInfo, cacheRecord));
    }

    /**
     * Lookup named handler from registry. The annotation MUST have a {@code name} property.
     * If name is not empty, it will be used for lookup.
     *
     * @param elementInfo current element info
     * @return an instance from registry, or null if none discovered
     */
    T namedHandler(TypedElementInfo elementInfo, Function<io.helidon.common.types.Annotation, T> fromAnnotation) {
        io.helidon.common.types.Annotation ftAnnotation = elementInfo.annotations()
                .stream()
                .filter(it -> annotationTypeName.equals(it.typeName()))
                .findFirst()
                .orElseThrow(() -> new InjectionException("Interceptor triggered for a method not annotated with "
                                                             + annotationTypeName));

        String name = ftAnnotation.getValue("name")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);

        if (name == null) {
            // not named, use annotation
            fromAnnotation.apply(ftAnnotation);
        }

        NamedResult<T> result = namedHandlerCache.get(name);

        if (result == null) {
            // not cached yet
            result = new NamedResult<>(lookupNamed(ftType, name));
            namedHandlerCache.put(name, result);
        }

        // cached
        return result.instance()
                .orElseGet(() -> fromAnnotation.apply(ftAnnotation));
    }

    <L> Optional<L> lookupNamed(Class<L> type, String name) {
        // not cached yet
        Qualifier qualifier = Qualifier.createNamed(name);

        Optional<ServiceProvider<L>> lServiceProvider = services.lookupFirst(type,
                                                                             ServiceInfoCriteria.builder()
                                                                                     .addQualifier(qualifier)
                                                                                     .build(),
                                                                             false);
        return lServiceProvider.map(ServiceProvider::get);
    }

    <M extends FtMethod> Optional<M> generatedMethod(Class<M> type, CacheRecord cacheRecord) {
        var qualifier = Qualifier.createNamed(cacheRecord.typeName().name()
                                                      + "."
                                                      + cacheRecord.methodName());
        List<ServiceProvider<M>> methods = services().lookupAll(type,
                                                                ServiceInfoCriteria.builder()
                                                                        .addQualifier(qualifier)
                                                                        .build());
        return methods.stream()
                .map(ServiceProvider::get)
                .filter(filterIt -> {
                    // only find methods that match the parameter types
                    List<GenericType<?>> supportedTypes = filterIt.parameterTypes();
                    List<TypedElementInfo> expectedTypes = cacheRecord.params();
                    if (supportedTypes.isEmpty() && expectedTypes.isEmpty()) {
                        // we have a match - no parameters
                        return true;
                    }
                    if (expectedTypes.isEmpty()) {
                        // supported types is not empty
                        return false;
                    }

                    if (supportedTypes.size() != expectedTypes.size()) {
                        // different number of parameters
                        return false;
                    }

                    // same number of parameters, let's see if the same types
                    for (int i = 0; i < expectedTypes.size(); i++) {
                        TypedElementInfo expectedType = expectedTypes.get(i);
                        GenericType<?> supportedType = supportedTypes.get(i);
                        if (!supportedType.type().getTypeName().equals(expectedType.typeName().fqName())) {
                            return false;
                        }
                    }
                    return true;
                })
                .findAny();
    }

    Services services() {
        return services;
    }

    record CacheRecord(TypeName typeName, String methodName, List<TypedElementInfo> params) {
    }

    record NamedResult<T>(Optional<T> instance) {
    }
}
