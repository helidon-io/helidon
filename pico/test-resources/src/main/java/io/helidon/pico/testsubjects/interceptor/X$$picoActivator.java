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
import java.util.Map;
import java.util.Optional;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.ElementInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.spi.ext.Dependencies;

import jakarta.annotation.Generated;

@Generated({"provider=oracle", "generator=io.helidon.pico.tools.creator.impl.DefaultActivatorCreator", "ver=1.0-SNAPSHOT"})
// @Singleton
@SuppressWarnings({"ALL", "unchecked"})
public class X$$picoActivator extends AbstractServiceProvider<X> {
    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .contractTypeImplemented(IA.class)
                    .contractTypeImplemented(IB.class)
                    .contractTypeImplemented(Closeable.class)
                    .activatorType(X$$picoActivator.class)
                    .build();

    public static final X$$picoActivator INSTANCE = new X$$picoActivator();

    protected X$$picoActivator() {
        setServiceInfo(serviceInfo);
    }

    public static Class<?> getServiceType() {
        return X.class;
    }

    public static String getServiceTypeName() {
        return getServiceType().getName();
    }

    @Override
    public Dependencies dependencies() {
        Dependencies deps = Dependencies.builder()
                .forServiceTypeName(getServiceTypeName())
                .add(InjectionPointInfo.CTOR, IA.class, ElementInfo.ElementKind.CTOR, 1, ElementInfo.Access.PUBLIC).elemOffset(1).setIsOptionalWrapped()
                .build().build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected X createServiceProvider(Map<String, Object> deps) {
        Optional<IA> arg1 = (Optional<IA>) get(deps, getClass().getPackageName() + ".<init>|1(1)");
        return new X(arg1);
    }
}
