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

package io.helidon.inject.runtime.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Generated;
import io.helidon.common.Weight;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.ElementKind;
import io.helidon.inject.api.PostConstructMethod;
import io.helidon.inject.api.PreDestroyMethod;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.runtime.AbstractServiceProvider;
import io.helidon.inject.runtime.Dependencies;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import static io.helidon.inject.api.ServiceInfoBasics.DEFAULT_INJECT_WEIGHT;

/**
 * Serves as an exemplar of what will is normally code generated.
 */
@Generated(value = "example", comments = "API Version: N", trigger = "io.helidon.inject.runtime.testsubjects.HelloInjectionImpl")
@Singleton
@Weight(DEFAULT_INJECT_WEIGHT)
@SuppressWarnings({"unchecked", "checkstyle:TypeName"})
public class HelloInjectionImpl$$injectionActivator extends AbstractServiceProvider<HelloInjectionWorldImpl> {

    private static final ServiceInfo serviceInfo =
            ServiceInfo.builder()
                    .serviceTypeName(HelloInjectionWorldImpl.class)
                    .activatorTypeName(HelloInjectionImpl$$injectionActivator.class)
                    .addContractImplemented(HelloInjectionWorld.class)
                    .addScopeTypeName(Singleton.class)
                    .declaredRunLevel(0)
                    .build();

    public static final HelloInjectionImpl$$injectionActivator INSTANCE = new HelloInjectionImpl$$injectionActivator();

    public HelloInjectionImpl$$injectionActivator() {
        serviceInfo(serviceInfo);
    }

    @Override
    public Class<HelloInjectionWorldImpl> serviceType() {
        return HelloInjectionWorldImpl.class;
    }

    @Override
    public DependenciesInfo dependencies() {
        DependenciesInfo deps = Dependencies.builder(HelloInjectionWorldImpl.class)
                .add("world", InjectionWorld.class, ElementKind.FIELD, AccessModifier.PACKAGE_PRIVATE).ipName("world").ipType(TypeName.create(
                        InjectionWorld.class))
                .add("worldRef", InjectionWorld.class, ElementKind.FIELD, AccessModifier.PACKAGE_PRIVATE)
                                .providerWrapped().ipName("worldRef").ipType(TypeName.create(Provider.class))
                .add("listOfWorlds", InjectionWorld.class, ElementKind.FIELD, AccessModifier.PACKAGE_PRIVATE)
                                .listWrapped().ipName("listOfWorlds").ipType(TypeName.create(List.class))
                .add("listOfWorldRefs", InjectionWorld.class, ElementKind.FIELD, AccessModifier.PACKAGE_PRIVATE)
                                .listWrapped().providerWrapped().ipName("listOfWorldRefs").ipType(TypeName.create(List.class))
                .add("redWorld", InjectionWorld.class, ElementKind.FIELD, AccessModifier.PACKAGE_PRIVATE)
                                .named("red").optionalWrapped().ipName("redWorld").ipType(TypeName.create(Optional.class))
                .add("world", InjectionWorld.class, ElementKind.METHOD, 1, AccessModifier.PACKAGE_PRIVATE)
                                .elemOffset(1).ipName("world").ipType(TypeName.create(InjectionWorld.class))
                .build();
        return Dependencies.combine(super.dependencies(), deps);
    }

    @Override
    protected HelloInjectionWorldImpl createServiceProvider(Map<String, Object> deps) {
        return new HelloInjectionWorldImpl();
    }

    @Override
    protected void doInjectingFields(Object t, Map<String, Object> deps, Set<String> injections, TypeName forServiceType) {
        super.doInjectingFields(t, deps, injections, forServiceType);
        HelloInjectionWorldImpl target = (HelloInjectionWorldImpl) t;
        target.world = Objects.requireNonNull(
                (InjectionWorld) deps.get(InjectionWorld.class.getPackageName() + ".world"), "world");
        target.worldRef = Objects.requireNonNull(
                (Provider<InjectionWorld>) deps.get(InjectionWorld.class.getPackageName() + ".worldRef"), "worldRef");
        target.listOfWorldRefs = Objects.requireNonNull(
                (List<Provider<InjectionWorld>>) deps.get(InjectionWorld.class.getPackageName() + ".listOfWorldRefs"), "listOfWorldRefs");
        target.listOfWorlds = Objects.requireNonNull(
                (List<InjectionWorld>) deps.get(InjectionWorld.class.getPackageName() + ".listOfWorlds"), "listOfWorlds");
        target.redWorld = Objects.requireNonNull(
                (Optional<InjectionWorld>) deps.get(InjectionWorld.class.getPackageName() + ".redWorld"), "redWorld");
    }

    @Override
    protected void doInjectingMethods(Object t, Map<String, Object> deps, Set<String> injections, TypeName forServiceType) {
        super.doInjectingMethods(t, deps, injections, forServiceType);
        HelloInjectionWorldImpl target = (HelloInjectionWorldImpl)t;
        target.world(Objects.requireNonNull(
                (InjectionWorld) deps.get(InjectionWorld.class.getPackageName() + ".world|1(1)")));
    }

    @Override
    public Optional<PostConstructMethod> postConstructMethod() {
        HelloInjectionWorldImpl impl = serviceRef().get();
        return Optional.of(impl::postConstruct);
    }

    @Override
    public Optional<PreDestroyMethod> preDestroyMethod() {
        HelloInjectionWorldImpl impl = serviceRef().get();
        return Optional.of(impl::preDestroy);
    }

}
