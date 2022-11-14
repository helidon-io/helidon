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

package io.helidon.pico.example;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.spi.ext.Dependencies;

import jakarta.annotation.Generated;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import static io.helidon.pico.InjectionPointInfo.Access;
import static io.helidon.pico.InjectionPointInfo.ElementKind;

/**
 * Example.
 */
@Generated(value = "TODO: Generate these for real", comments = "API Version: 1")
@Singleton
@Weight(DefaultServiceInfo.DEFAULT_WEIGHT)
@SuppressWarnings({"unchecked", "checkstyle:TypeName"})
public class HelloImpl$$picodiActivator extends AbstractServiceProvider<HelloImpl> {

    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .contractImplemented(Hello.class.getName())
                    .activatorTypeName(HelloImpl$$picodiActivator.class.getName())
                    .scopeTypeName(Singleton.class.getName())
                    .weight(null)
                    .runLevel(0)
                    .build();

    public static final HelloImpl$$picodiActivator INSTANCE = new HelloImpl$$picodiActivator();

    /**
     * The default constructor.
     */
    HelloImpl$$picodiActivator() {
        setServiceInfo(serviceInfo);
    }

    public static String getServiceTypeName() {
        return HelloImpl.class.getName();
    }

    @Override
    public Dependencies dependencies() {
        Dependencies deps = Dependencies.builder()
                .forServiceTypeName(getServiceTypeName())
                .add("world", World.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                .add("worldRef", World.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE).setIsProviderWrapped()
                .add("listOfWorlds", World.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE).setIsListWrapped()
                .add("listOfWorldRefs", World.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                                .setIsListWrapped().setIsProviderWrapped()
                .add("redWorld", World.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE).named("red").setIsOptionalWrapped()
                .add("world", World.class, ElementKind.METHOD, 1, Access.PACKAGE_PRIVATE).elemOffset(1)
                .build().build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected HelloImpl createServiceProvider(Map<String, Object> deps) {
        return new HelloImpl();
    }

    @Override
    protected void doInjectingFields(Object t, Map<String, Object> deps, Set<String> injections, String forServiceType) {
        super.doInjectingFields(t, deps, injections, forServiceType);
        HelloImpl target = (HelloImpl) t;
        target.world = Objects.requireNonNull((World) deps.get("io.helidon.pico.example.world"), "world");
        target.worldRef = Objects.requireNonNull((Provider<World>) deps.get("io.helidon.pico.example.worldRef"), "worldRef");
        target.listOfWorldRefs = Objects.requireNonNull((List<Provider<World>>) deps.get("io.helidon.pico.example.listOfWorldRefs"), "listOfWorldRefs");
        target.listOfWorlds = Objects.requireNonNull((List<World>) deps.get("io.helidon.pico.example.listOfWorlds"), "listOfWorlds");
        target.redWorld = Objects.requireNonNull((Optional<World>) deps.get("io.helidon.pico.example.redWorld"), "redWorld");
    }

    @Override
    protected void doInjectingMethods(Object t, Map<String, Object> deps, Set<String> injections, String forServiceType) {
        super.doInjectingMethods(t, deps, injections, forServiceType);
        HelloImpl target = (HelloImpl)t;
        target.world(Objects.requireNonNull((World) deps.get("io.helidon.pico.example.world|1(1)")));
    }

    @Override
    public PostConstructMethod getPostConstructMethod() {
        HelloImpl impl = getServiceRef();
        return impl::postConstruct;
    }

    @Override
    public PreDestroyMethod preDestroyMethod() {
        HelloImpl impl = getServiceRef();
        return impl::preDestroy;
    }

}
