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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.pico.spi.RunLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@RunLevel(0)
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

    private World setWorld;

    int postConstructCallCount;
    int preDestroyCallCount;

    @Override
    public void sayHello() {
        assert(postConstructCallCount == 1);
        assert(preDestroyCallCount == 0);
        Logger.getLogger(getClass().getName()).log(Level.INFO, "hello {0}", worldRef.get());
        assert(world == worldRef.get()) : "world != worldRef";
        assert(world == setWorld) : "world != setWorld";
    }

    @Inject
    public void world(World world) {
        this.setWorld = world;
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
