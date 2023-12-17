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
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.inject.service.Injection;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Injection.Singleton
@Injection.RunLevel(0)
public class HelloInjectionWorldImpl implements HelloInjectionWorld {

    @Injection.Inject
    InjectionWorld world;

    @Injection.Inject
    Supplier<InjectionWorld> worldRef;

    @Injection.Inject
    List<Supplier<InjectionWorld>> listOfWorldRefs;

    @Injection.Inject
    List<InjectionWorld> listOfWorlds;

    @Injection.Inject @Injection.Named("red")
    Optional<InjectionWorld> redWorld;

    private InjectionWorld setWorld;

    int postConstructCallCount;
    int preDestroyCallCount;

    @Override
    public String sayHello() {
        assert(postConstructCallCount == 1);
        assert(preDestroyCallCount == 0);
        assert(world == worldRef.get());
        assert(world == setWorld);
        assert(redWorld.isEmpty());

        return "Hello " + world.name();
    }

    @Injection.Inject
    void world(InjectionWorld world) {
        this.setWorld = world;
    }

    @Injection.PostConstruct
    public void postConstruct() {
        postConstructCallCount++;
    }

    @Injection.PreDestroy
    public void preDestroy() {
        preDestroyCallCount++;
    }

    public int postConstructCallCount() {
        return postConstructCallCount;
    }

    public int preDestroyCallCount() {
        return preDestroyCallCount;
    }

}
