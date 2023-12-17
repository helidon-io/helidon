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

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.ServiceBinder;

@Injection.Singleton
@Injection.Named(HelloInjection__Module.NAME)
public final class HelloInjection__Module implements ModuleComponent {

    public static final String NAME = "example";

    public HelloInjection__Module() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(HelloInjectionImpl__ServiceDescriptor.INSTANCE);
        binder.bind(InjectionWorldImpl__ServiceDescriptor.INSTANCE);
    }

}
