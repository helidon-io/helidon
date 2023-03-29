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

import io.helidon.pico.Module;
import io.helidon.pico.ServiceBinder;

import jakarta.annotation.Generated;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Generated(value = "example", comments = "API Version: n")
@Singleton
@Named(HelloPicoModule.NAME)
public class HelloPicoModule implements Module {

    public static final String NAME = "example";

    public HelloPicoModule() {
        int dummy = 1;
    }

    @Override
    public Optional<String> name() {
        return Optional.of(NAME);
    }

    @Override
    public void configure(ServiceBinder binder) {
        binder.bind(HelloPicoImpl$$picoActivator.INSTANCE);
        binder.bind(PicoWorldImpl$$picoActivator.INSTANCE);
    }

}
