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

import io.helidon.inject.Application;
import io.helidon.inject.ServiceInjectionPlanBinder;

/**
 * For testing.
 */
public class HelloInjection__Application implements Application {
    public static boolean ENABLED = true;

    public HelloInjection__Application() {
    }

    @Override
    public String name() {
        return "HelloInjectionApplication";
    }

    @Override
    public void configure(ServiceInjectionPlanBinder binder) {
        if (!ENABLED) {
            return;
        }

        binder.bindTo(HelloInjectionImpl__ServiceDescriptor.INSTANCE)
                .bind(HelloInjectionImpl__ServiceDescriptor.IP_0, false, InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .bind(HelloInjectionImpl__ServiceDescriptor.IP_1, true, InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .bindMany(HelloInjectionImpl__ServiceDescriptor.IP_2, true, InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .bindMany(HelloInjectionImpl__ServiceDescriptor.IP_3, false, InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .bindOptional(HelloInjectionImpl__ServiceDescriptor.IP_4, false)
                .bind(HelloInjectionImpl__ServiceDescriptor.IP_5, false, InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .commit();

        binder.bindTo(InjectionWorldImpl__ServiceDescriptor.INSTANCE)
                .commit();
    }

}
