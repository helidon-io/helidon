/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.InterceptionException;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;

@SuppressWarnings({"unchecked", "rawtypes"})
abstract class InterceptorBase<T> implements Interception.Interceptor {
    private final Map<CacheRecord, T> methodHandlerCache = new ConcurrentHashMap<>();
    private final Map<String, NamedResult<T>> namedHandlerCache = new ConcurrentHashMap<>();

    private final ServiceRegistry services;
    private final Class<T> ftType;
    private final TypeName annotationTypeName;

    InterceptorBase(ServiceRegistry services, Class<T> ftType, Class<? extends Annotation> annotationType) {
        this.services = services;
        this.ftType = ftType;
        this.annotationTypeName = TypeName.create(annotationType);
    }

    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        // these are our cache keys
        TypeName typeName = ctx.serviceInfo().serviceType();
        TypedElementInfo elementInfo = ctx.elementInfo();
        List<TypedElementInfo> params = ctx.elementInfo().parameterArguments();

        CacheRecord cacheRecord = new CacheRecord(typeName, elementInfo.elementName(), params);
        T ftHandler = cachedHandler(elementInfo, cacheRecord);

        return invokeHandler(ftHandler, chain, args);
    }

    <V> V invokeHandler(T ftHandler, Chain<V> chain, Object[] args) throws Exception {
        if (ftHandler instanceof FtHandler handler) {
            return handler.invoke(new WrappingSupplier<>(() -> chain.proceed(args)));
        } else if (ftHandler instanceof FtHandlerTyped typed) {
            return (V) typed.invoke(new WrappingSupplier<>(() -> chain.proceed(args)));
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
                .orElseThrow(() -> new ServiceRegistryException("Interceptor triggered for a method not annotated with "
                                                                        + annotationTypeName));

        String name = ftAnnotation.getValue("name")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);

        if (name == null) {
            // not named, use annotation
            return fromAnnotation.apply(ftAnnotation);
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

        return services.first(Lookup.builder()
                                      .addQualifier(qualifier)
                                      .addContract(type)
                                      .build());
    }

    <L> Optional<L> lookup(Class<L> type) {
        return services.first(type);
    }

    <M extends FaultToleranceGenerated.FtMethod> Optional<M> generatedMethod(Class<M> type, CacheRecord cacheRecord) {
        var qualifier = Qualifier.createNamed(cacheRecord.namedValue());
        return services.first(Lookup.builder()
                                      .addContract(type)
                                      .addQualifier(qualifier)
                                      .build());
    }

    <M extends FaultToleranceGenerated.FtMethod> Optional<M> generatedMethod(TypeName type, CacheRecord cacheRecord) {
        var qualifier = Qualifier.createNamed(cacheRecord.namedValue());
        return services.first(Lookup.builder()
                                      .addContract(type)
                                      .addQualifier(qualifier)
                                      .build());
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    record CacheRecord(TypeName typeName, String methodName, List<TypedElementInfo> params) {
        String namedValue() {
            return typeName().fqName()
                    + "."
                    + methodName()
                    + "(" + paramsTypes() + ")";
        }

        String paramsTypes() {
            return params.stream()
                    .map(TypedElementInfo::typeName)
                    .map(TypeName::fqName)
                    .collect(Collectors.joining(","));
        }
    }

    record NamedResult<T>(Optional<T> instance) {
    }

    private static class WrappingSupplier<T> implements Supplier<T> {
        private final ThrowingSupplier<T> throwing;

        private WrappingSupplier(ThrowingSupplier<T> throwing) {
            this.throwing = throwing;
        }

        @Override
        public T get() {
            try {
                return throwing.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new InterceptionException("Failed to invoke supplier", e, false);
            }
        }
    }
}
