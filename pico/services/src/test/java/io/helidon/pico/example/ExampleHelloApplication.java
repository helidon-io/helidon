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

import java.util.Optional;

import io.helidon.pico.Application;
import io.helidon.pico.ServiceInjectionPlanBinder;

import jakarta.annotation.Generated;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Generated(value = "TODO: Generate these for real", comments = "API Version: 1")
@Singleton
@Named(ExampleHelloApplication.NAME)
public class ExampleHelloApplication implements Application {
    static final String NAME = "ExampleHelloApplication";
    static boolean ENABLED = true;

    public ExampleHelloApplication() {
        boolean debugMe = true;
    }

    @Override
    public Optional<String> name() {
        return Optional.of(NAME);
    }

    @Override
    public void configure(ServiceInjectionPlanBinder binder) {
        if (!ENABLED) {
            return;
        }

        /**
         * See {@link io.helidon.pico.example.HelloImpl}
         */
        binder.bindTo(HelloImpl$$picodiActivator.INSTANCE)
                .bind("io.helidon.pico.example.world", WorldImpl$$picodiActivator.INSTANCE)
                .bind("io.helidon.pico.example.worldRef", WorldImpl$$picodiActivator.INSTANCE)
                .bindMany("io.helidon.pico.example.listOfWorldRefs", WorldImpl$$picodiActivator.INSTANCE)
                .bindMany("io.helidon.pico.example.listOfWorlds", WorldImpl$$picodiActivator.INSTANCE)
                .bindVoid("io.helidon.pico.example.redWorld")
                .bind("io.helidon.pico.example.world|1(1)", WorldImpl$$picodiActivator.INSTANCE)
                .commit();

        /**
         * See {@link io.helidon.pico.example.WorldImpl}
         */
        binder.bindTo(WorldImpl$$picodiActivator.INSTANCE)
                .commit();
    }

}
