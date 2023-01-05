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

import java.util.Optional;

import io.helidon.pico.Application;
import io.helidon.pico.ServiceInjectionPlanBinder;

import jakarta.annotation.Generated;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * For testing.
 */
@Generated(value = "example", comments = "API Version: n")
@Singleton
@Named(HelloPicoApplication.NAME)
public class HelloPicoApplication implements Application {
    public static boolean ENABLED = true;

    static final String NAME = "HelloPicoApplication";

    public HelloPicoApplication() {
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

        binder.bindTo(HelloPicoImpl$$picoActivator.INSTANCE)
                .bind("io.helidon.pico.example.world", PicoWorldImpl$$picoActivator.INSTANCE)
                .bind("io.helidon.pico.example.worldRef", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindMany("io.helidon.pico.example.listOfWorldRefs", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindMany("io.helidon.pico.example.listOfWorlds", PicoWorldImpl$$picoActivator.INSTANCE)
                .bindVoid("io.helidon.pico.example.redWorld")
                .bind("io.helidon.pico.example.world|1(1)", PicoWorldImpl$$picoActivator.INSTANCE)
                .commit();

        binder.bindTo(PicoWorldImpl$$picoActivator.INSTANCE)
                .commit();
    }

}
