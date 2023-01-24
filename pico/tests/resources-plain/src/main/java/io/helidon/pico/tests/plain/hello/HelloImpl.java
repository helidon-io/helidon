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

package io.helidon.pico.tests.plain.hello;

import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@Weight(Weighted.DEFAULT_WEIGHT)
public class HelloImpl implements Hello {

    @Inject
    World world;

    @Inject
    Provider<World> worldRef;

    @Inject
    List<Provider<World>> listOfWorldRefs;

    @Inject
    List<World> listOfWorlds;

    @Inject @Named("red")
    Optional<World> redWorld;

    @Inject
    private Optional<World> privateWorld;

    private World setWorld;
    private Optional<World> setRedWorld;
    private World ctorWorld;

    int postConstructCallCount;
    int preDestroyCallCount;

    HelloImpl() {
    }

    @Inject
    public HelloImpl(
            World ctorWorld) {
        this();
        this.ctorWorld = ctorWorld;
    }

    @Override
    public void sayHello() {
        assert (postConstructCallCount == 1);
        assert (preDestroyCallCount == 0);
        System.getLogger(getClass().getName()).log(System.Logger.Level.INFO, "hello {0}", worldRef.get());
        assert (world == worldRef.get()) : "world != worldRef";
        assert (world == setWorld) : "world != setWorld";
        assert (ctorWorld == world) : "world != ctorWorld";
    }

    @Inject
    public void world(
            World world) {
        this.setWorld = world;
        assert (world == ctorWorld);
    }

    @Inject
    public void setRedWorld(
            @Named("red") Optional<World> redWorld) {
        this.setRedWorld = redWorld;
    }

    @PostConstruct
    public void postConstruct() {
        postConstructCallCount++;
    }

    @PreDestroy
    public void preDestroy() {
        preDestroyCallCount++;
    }

}
