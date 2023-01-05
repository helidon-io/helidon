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

package io.helidon.pico.services.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.services.Dependencies;

import jakarta.annotation.Generated;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import static io.helidon.pico.ElementInfo.Access;
import static io.helidon.pico.ElementInfo.ElementKind;

/**
 * Serves as an exemplar of what will is normally code generated.
 */
@Generated(value = "example", comments = "API Version: N")
@Singleton
@Weight(DefaultServiceInfo.DEFAULT_WEIGHT)
@SuppressWarnings({"unchecked", "checkstyle:TypeName"})
public class HelloPicoImpl$$picoActivator extends AbstractServiceProvider<HelloPicoWorldImpl> {

    private static final DefaultServiceInfo serviceInfo =
            DefaultServiceInfo.builder()
                    .serviceTypeName(getServiceTypeName())
                    .addContractImplemented(HelloPicoWorld.class.getName())
                    .activatorTypeName(HelloPicoImpl$$picoActivator.class.getName())
                    .scopeTypeName(Singleton.class.getName())
                    .weight(null)
                    .runLevel(0)
                    .build();

    public static final HelloPicoImpl$$picoActivator INSTANCE = new HelloPicoImpl$$picoActivator();

    HelloPicoImpl$$picoActivator() {
        serviceInfo(serviceInfo);
    }

    public static String getServiceTypeName() {
        return HelloPicoWorldImpl.class.getName();
    }

    @Override
    public DependenciesInfo dependencies() {
        DependenciesInfo deps = Dependencies.builder(getServiceTypeName())
                .add("world", PicoWorld.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                .add("worldRef", PicoWorld.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                                .providerWrapped()
                .add("listOfWorlds", PicoWorld.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                                .listWrapped()
                .add("listOfWorldRefs", PicoWorld.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                                .listWrapped().providerWrapped()
                .add("redWorld", PicoWorld.class, ElementKind.FIELD, Access.PACKAGE_PRIVATE)
                                .named("red").optionalWrapped()
                .add("world", PicoWorld.class, ElementKind.METHOD, 1, Access.PACKAGE_PRIVATE)
                                .elemOffset(1)
                .build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected HelloPicoWorldImpl createServiceProvider(Map<String, Object> deps) {
        return new HelloPicoWorldImpl();
    }

    @Override
    protected void doInjectingFields(Object t, Map<String, Object> deps, Set<String> injections, String forServiceType) {
        super.doInjectingFields(t, deps, injections, forServiceType);
        HelloPicoWorldImpl target = (HelloPicoWorldImpl) t;
        target.world = Objects.requireNonNull(
                (PicoWorld) deps.get("io.helidon.pico.example.world"), "world");
        target.worldRef = Objects.requireNonNull(
                (Provider<PicoWorld>) deps.get("io.helidon.pico.example.worldRef"), "worldRef");
        target.listOfWorldRefs = Objects.requireNonNull(
                (List<Provider<PicoWorld>>) deps.get("io.helidon.pico.example.listOfWorldRefs"), "listOfWorldRefs");
        target.listOfWorlds = Objects.requireNonNull(
                (List<PicoWorld>) deps.get("io.helidon.pico.example.listOfWorlds"), "listOfWorlds");
        target.redWorld = Objects.requireNonNull(
                (Optional<PicoWorld>) deps.get("io.helidon.pico.example.redWorld"), "redWorld");
    }

    @Override
    protected void doInjectingMethods(Object t, Map<String, Object> deps, Set<String> injections, String forServiceType) {
        super.doInjectingMethods(t, deps, injections, forServiceType);
        HelloPicoWorldImpl target = (HelloPicoWorldImpl)t;
        target.world(Objects.requireNonNull(
                (PicoWorld) deps.get("io.helidon.pico.example.world|1(1)")));
    }

    @Override
    public Optional<PostConstructMethod> postConstructMethod() {
        HelloPicoWorldImpl impl = serviceRef().get();
        return Optional.of(impl::postConstruct);
    }

    @Override
    public Optional<PreDestroyMethod> preDestroyMethod() {
        HelloPicoWorldImpl impl = serviceRef().get();
        return Optional.of(impl::preDestroy);
    }

}
