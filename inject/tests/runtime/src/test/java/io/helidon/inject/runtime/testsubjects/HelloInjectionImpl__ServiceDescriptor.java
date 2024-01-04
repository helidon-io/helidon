/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Serves as an exemplar of what will is normally code generated.
 */
public class HelloInjectionImpl__ServiceDescriptor implements ServiceDescriptor<HelloInjectionWorldImpl> {
    public static final HelloInjectionImpl__ServiceDescriptor INSTANCE = new HelloInjectionImpl__ServiceDescriptor();

    private static final TypeName DESCRIPTOR = TypeName.create(HelloInjectionImpl__ServiceDescriptor.class);
    private static final TypeName TYPE_NAME = TypeName.create(HelloInjectionWorldImpl.class);

    private static final Set<TypeName> CONTRACTS = Set.of(TypeName.create(HelloInjectionWorld.class));
    private static final TypeName TYPE_0 = TypeName.create("io.helidon.inject.runtime.testsubjects.InjectionWorld");
    public static final Ip IP_0 = Ip.<InjectionWorld>builder()
            .elementKind(ElementKind.FIELD)
            .name("world")
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .field("IP_0")
            .contract(TYPE_0)
            .typeName(TYPE_0)
            .build();
    public static final Ip IP_5 = Ip.builder()
            .elementKind(ElementKind.METHOD)
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .name("world_1_world")
            .field("IP_5")
            .contract(TYPE_0)
            .typeName(TYPE_0)
            .build();
    private static final TypeName TYPE_1 = TypeName.create(
            "java.util.function.Supplier<io.helidon.inject.runtime.testsubjects.InjectionWorld>");
    public static final Ip IP_1 = Ip.builder()
            .elementKind(ElementKind.FIELD)
            .name("worldRef")
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .field("IP_1")
            .typeName(TYPE_1)
            .contract(TYPE_0)
            .build();
    private static final TypeName TYPE_2 = TypeName.create(
            "java.util.List<java.util.function.Supplier<io.helidon.inject.runtime.testsubjects.InjectionWorld>>");
    public static final Ip IP_2 = Ip.builder()
            .elementKind(ElementKind.FIELD)
            .name("listOfWorldRefs")
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .field("IP_2")
            .contract(TYPE_0)
            .typeName(TYPE_2)
            .build();
    private static final TypeName TYPE_3 = TypeName.create("java.util.List<io.helidon.inject.runtime.testsubjects"
                                                                   + ".InjectionWorld>");
    public static final Ip IP_3 = Ip.<List<InjectionWorld>>builder()
            .elementKind(ElementKind.FIELD)
            .name("listOfWorlds")
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .field("IP_3")
            .contract(TYPE_0)
            .typeName(TYPE_3)
            .build();
    private static final TypeName TYPE_4 = TypeName.create(
            "java.util.Optional<io.helidon.inject.runtime.testsubjects.InjectionWorld>");
    public static final Ip IP_4 = Ip.<Optional<InjectionWorld>>builder()
            .elementKind(ElementKind.FIELD)
            .name("redWorld")
            .service(TYPE_NAME)
            .descriptor(DESCRIPTOR)
            .field("IP_4")
            .contract(TYPE_0)
            .typeName(TYPE_4)
            .qualifiers(Set.of(Qualifier.createNamed("red")))
            .build();
    private static final List<Ip> DEPENDENCIES = List.of(IP_0,
                                                         IP_1,
                                                         IP_2,
                                                         IP_3,
                                                         IP_4,
                                                         IP_5);

    public HelloInjectionImpl__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return TYPE_NAME;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public double weight() {
        return Services.INJECT_WEIGHT;
    }

    @Override
    public List<Ip> dependencies() {
        return DEPENDENCIES;
    }

    @Override
    public Object instantiate(InjectionContext ctx, InterceptionMetadata interceptionMetadata) {
        return new HelloInjectionWorldImpl();
    }

    @Override
    public void inject(InjectionContext ctx,
                       InterceptionMetadata interceptionMetadata,
                       Set<String> injected,
                       HelloInjectionWorldImpl instance) {
        instance.world = ctx.param(IP_0);
        instance.worldRef = ctx.param(IP_1);
        instance.listOfWorldRefs = ctx.param(IP_2);
        instance.listOfWorlds = ctx.param(IP_3);
        instance.redWorld = ctx.param(IP_4);

        instance.world(ctx.param(IP_5));
    }

    @Override
    public void postConstruct(HelloInjectionWorldImpl instance) {
        instance.postConstruct();
    }

    @Override
    public void preDestroy(HelloInjectionWorldImpl instance) {
        instance.preDestroy();
    }

    @Override
    public TypeName scope() {
        return Injection.Singleton.TYPE_NAME;
    }
}
