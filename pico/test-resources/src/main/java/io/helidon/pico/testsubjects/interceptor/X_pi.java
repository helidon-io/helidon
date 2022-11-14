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

package io.helidon.pico.testsubjects.interceptor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Intercepted;
import io.helidon.pico.Interceptor;
import io.helidon.pico.InvocationException;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.types.TypedElementName;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import static io.helidon.pico.types.DefaultTypeName.create;
import static io.helidon.pico.spi.ext.Invocation.createAndInvoke;
import static io.helidon.pico.spi.ext.Invocation.createInvokeAndSupply;
import static io.helidon.pico.spi.ext.Invocation.mergeAndCollapse;

@Intercepted(X.class)
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 0.1)
@SuppressWarnings({"ALL", "unchecked"})
public class X_pi extends X {
    private static final List<AnnotationAndValue> classAnnotations = List.of(
            DefaultAnnotationAndValue.create(Named.class, "ClassX")
    );

    private static final TypedElementName methodIA1 = DefaultTypedElementName.builder()
            .typeName(create(Void.class))
            .elementName("methodIA1")
            .build();
    private static final TypedElementName methodIA2 = DefaultTypedElementName.builder()
            .typeName(create(Void.class))
            .elementName("methodIA2")
            .annotation(DefaultAnnotationAndValue.create(InterceptorBasedAnno.class, "IA2"))
            .build();
    private static final TypedElementName methodIB = DefaultTypedElementName.builder()
            .typeName(create(int.class))
            .elementName("methodIB")
            .annotation(DefaultAnnotationAndValue.create(Named.class, "methodIB"))
            .annotation(DefaultAnnotationAndValue.create(InterceptorBasedAnno.class, "IBSubAnno"))
            // this next one was inherited from the class level on IB...
            .annotation(DefaultAnnotationAndValue.create(InterceptorBasedAnno.class, "IBAnno"))
            .build();
    private static final TypedElementName methodIB_1 = DefaultTypedElementName.builder()
            .typeName(create(String.class))
            .annotation(DefaultAnnotationAndValue.create(Named.class, "arg1"))
            .build();
    private static final TypedElementName close = DefaultTypedElementName.builder()
            .typeName(create(Void.class))
            .elementName("close")
            .annotation(DefaultAnnotationAndValue.create(InterceptorBasedAnno.class))
            .build();
    private static final TypedElementName methodX = DefaultTypedElementName.builder()
            .typeName(create(long.class))
            .elementName("methodX")
            .build();

    private final Provider<X> provider;
    private final ServiceProvider<X> sp;
    private final X impl;
    private final TypeName serviceTypeName;
    private final List<Provider<Interceptor>> methodIA1_interceptors;
    private final List<Provider<Interceptor>> methodIA2_interceptors;
    private final List<Provider<Interceptor>> methodIB_interceptors;
    private final List<Provider<Interceptor>> close_interceptors;
    private final List<Provider<Interceptor>> methodX_interceptors;

    @Inject
    public X_pi(Optional<IA> c1,
                Provider<X> provider,
                @Named("jakarta.inject.Named") List<Provider<Interceptor>> namedInterceptors,
                @Named("io.helidon.pico.testsubjects.interceptor.InterceptorBasedAnno") List<Provider<Interceptor>> interceptorBasedAnnoInterceptors) {
        // must have a super no-arg ctor.
//        super(c1);
        this.provider = (Provider<X>) Objects.requireNonNull(provider);
        this.sp = (provider instanceof ServiceProvider) ? (ServiceProvider<X>) provider : null;
        this.serviceTypeName = create(X.class);
        List<Provider<Interceptor>> classInterceptors = mergeAndCollapse(namedInterceptors);
        this.methodIA1_interceptors = mergeAndCollapse(classInterceptors);
        this.methodIA2_interceptors = mergeAndCollapse(interceptorBasedAnnoInterceptors,
                                                       namedInterceptors,
                                                       classInterceptors);
        this.methodIB_interceptors = mergeAndCollapse(interceptorBasedAnnoInterceptors,
                                                      namedInterceptors,
                                                      classInterceptors);
        this.close_interceptors = mergeAndCollapse(classInterceptors);
        this.methodX_interceptors = mergeAndCollapse(classInterceptors);

        TypedElementName ctor = DefaultTypedElementName.builder()
                .typeName(create(X.class))
                .elementName(InjectionPointInfo.CTOR)
                .build();

        Supplier<X> call = () -> {
            try {
                return provider.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };
        X result = createInvokeAndSupply(call,
                                         classInterceptors,
                                         sp,
                                         serviceTypeName,
                                         classAnnotations,
                                         ctor,
                                         null);
        this.impl = Objects.requireNonNull(result);
    }

    @Override
    public void methodIA1() {
        Runnable call = () -> {
            try {
                impl.methodIA1();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };
        createAndInvoke(call,
                        methodIA1_interceptors,
                        sp,
                        serviceTypeName,
                        classAnnotations,
                        methodIA1,
                        null);
    }

    @Override
    public void methodIA2() {
        Runnable call = () -> {
            try {
                impl.methodIA2();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };
        createAndInvoke(call,
                        methodIA2_interceptors,
                        sp,
                        serviceTypeName,
                        classAnnotations,
                        methodIA2,
                        null);
    }

    @Override
    public void methodIB(String p1) {
        Object[] args = new Object[] {p1};
        Runnable call = () -> {
            try {
                impl.methodIB((String) args[0]);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };
        createAndInvoke(call,
                       methodIB_interceptors,
                       sp,
                       serviceTypeName,
                       classAnnotations,
                       methodIB,
                       args,
                       methodIB_1);
    }

    @Override
    public void close() {
        Runnable call = () -> {
            try {
                impl.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };

        createAndInvoke(call,
                        close_interceptors,
                        sp,
                        serviceTypeName,
                        classAnnotations,
                        close,
                        null);
    }

    @Override
    public long methodX(String arg1, int arg2, boolean arg3) {
        Object[] args = new Object[] {arg1, arg2, arg3};
        Supplier<Long> call = () -> {
            try {
                return impl.methodX((String) args[0], (int) args[1], (boolean) args[2]);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new InvocationException(t.getMessage(), t, sp);
            }
        };

        Long result = createInvokeAndSupply(call,
                                            methodX_interceptors,
                                            sp,
                                            serviceTypeName,
                                            classAnnotations,
                                            methodX,
                                            args);
        return result;
    }

}
