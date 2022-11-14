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

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.Intercepted;
import io.helidon.pico.Interceptor;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.impl.DefaultInjectionPointInfo;

import jakarta.annotation.Generated;
import jakarta.inject.Named;
import jakarta.inject.Provider;

@Generated({"provider=oracle", "generator=io.helidon.pico.tools.creator.impl.DefaultActivatorCreator", "ver=1.0-SNAPSHOT"})
// @Singleton
@SuppressWarnings({"ALL", "unchecked"})
public class X_pi$$picoActivator extends X$$picoActivator {
    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .weight(DEFAULT_WEIGHT + 0.01)
                    .qualifier(DefaultQualifierAndValue.create(Intercepted.class, "io.helidon.pico.testsubjects.interceptor.X"))
                    .contractTypeImplemented(X.class)
                    .contractTypeImplemented(Closeable.class)
                    .contractTypeImplemented(IA.class)
                    .contractTypeImplemented(IB.class)
                    .contractTypeImplemented(Closeable.class)
                    .activatorType(X_pi$$picoActivator.class)
                    .build();

    public static final X$$picoActivator INSTANCE = new X_pi$$picoActivator();

    protected X_pi$$picoActivator() {
        setServiceInfo(serviceInfo);
    }

    public static Class<?> getServiceType() {
        return X_pi.class;
    }

    public static String getServiceTypeName() {
        return getServiceType().getName();
    }

    @Override
    public Dependencies dependencies() {
        Dependencies deps = Dependencies.builder()
                .forServiceTypeName(getServiceTypeName())
                .add(DefaultInjectionPointInfo.CTOR, IA.class, InjectionPointInfo.ElementKind.CTOR, 4, InjectionPointInfo.Access.PUBLIC).elemOffset(1).setIsOptionalWrapped()
                .add(DefaultInjectionPointInfo.CTOR, X.class, InjectionPointInfo.ElementKind.CTOR, 4, InjectionPointInfo.Access.PUBLIC).elemOffset(2).setIsProviderWrapped()
                .add(DefaultInjectionPointInfo.CTOR, Interceptor.class, InjectionPointInfo.ElementKind.CTOR, 4, InjectionPointInfo.Access.PUBLIC).elemOffset(3).named(Named.class.getName()).setIsListWrapped().setIsProviderWrapped()
                .add(DefaultInjectionPointInfo.CTOR, Interceptor.class, InjectionPointInfo.ElementKind.CTOR, 4, InjectionPointInfo.Access.PUBLIC).elemOffset(4).named(InterceptorBasedAnno.class.getName()).setIsListWrapped().setIsProviderWrapped()
                .build().build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected X_pi createServiceProvider(Map<String, Object> deps) {
        Optional<IA> arg1 = (Optional<IA>) get(deps, "io.helidon.pico.testsubjects.interceptor.<init>|4(1)");
        Provider<X> arg2 = (Provider<X>) get(deps, "io.helidon.pico.testsubjects.interceptor.<init>|4(2)");
        List<Provider<Interceptor>> arg3 = (List<Provider<Interceptor>>) get(deps, "io.helidon.pico.testsubjects.interceptor.<init>|4(3)");
        List<Provider<Interceptor>> arg4 = (List<Provider<Interceptor>>) get(deps, "io.helidon.pico.testsubjects.interceptor.<init>|4(4)");
        return new X_pi(arg1, arg2, arg3, arg4);
    }

}
