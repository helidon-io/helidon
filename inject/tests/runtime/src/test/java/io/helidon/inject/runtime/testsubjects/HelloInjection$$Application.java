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

import java.util.Optional;

import io.helidon.common.Generated;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.ServiceInjectionPlanBinder;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * For testing.
 */
@Generated(value = "example", comments = "API Version: n", trigger = "io.helidon.inject.tools.ApplicationCreatorDefault")
@Singleton
@Named(HelloInjection$$Application.NAME)
public class HelloInjection$$Application implements Application {
    public static boolean ENABLED = true;

    static final String NAME = "HelloInjectionApplication";

    public HelloInjection$$Application() {
        assert(true); // for setting breakpoints in debug
    }

    @Override
    public Optional<String> named() {
        return Optional.of(NAME);
    }

    @Override
    public void configure(ServiceInjectionPlanBinder binder) {
        if (!ENABLED) {
            return;
        }

        binder.bindTo(HelloInjectionImpl$$injectionActivator.INSTANCE)
                .bind(HelloInjectionWorld.class.getPackageName() + ".world", InjectionWorldImpl$$injectionActivator.INSTANCE)
                .bind(HelloInjectionWorld.class.getPackageName() + ".worldRef", InjectionWorldImpl$$injectionActivator.INSTANCE)
                .bindMany(HelloInjectionWorld.class.getPackageName() + ".listOfWorldRefs", InjectionWorldImpl$$injectionActivator.INSTANCE)
                .bindMany(HelloInjectionWorld.class.getPackageName() + ".listOfWorlds", InjectionWorldImpl$$injectionActivator.INSTANCE)
                .bindVoid(HelloInjectionWorld.class.getPackageName() + ".redWorld")
                .bind(HelloInjectionWorld.class.getPackageName() + ".world|1(1)", InjectionWorldImpl$$injectionActivator.INSTANCE)
                .commit();

        binder.bindTo(InjectionWorldImpl$$injectionActivator.INSTANCE)
                .commit();
    }

}
