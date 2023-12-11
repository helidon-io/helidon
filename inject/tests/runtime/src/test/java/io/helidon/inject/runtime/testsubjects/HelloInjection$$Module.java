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
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.ServiceBinder;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Generated(value = "example", comments = "API Version: n", trigger = "io.helidon.inject.tools.ActivatorCreatorDefault")
@Singleton
@Named(HelloInjection$$Module.NAME)
public final class HelloInjection$$Module implements ModuleComponent {

    public static final String NAME = "example";

    public HelloInjection$$Module() {
    }

    @Override
    public Optional<String> named() {
        return Optional.of(NAME);
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(HelloInjectionImpl$$injectionActivator.INSTANCE);
        binder.bind(InjectionWorldImpl$$injectionActivator.INSTANCE);
    }

}
