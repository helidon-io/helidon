/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import javax.annotation.Priority;

import org.glassfish.jersey.inject.hk2.Hk2InjectionManagerFactory;
import org.glassfish.jersey.internal.inject.InjectionManager;

/**
 * Overrides injection manager factory from Jersey to avoid creating a parent
 * injection manager and support cross-injection between JAX-RS applications.
 */
@Priority(11)   // overrides Jersey's
public class HelidonHK2InjectionManagerFactory extends Hk2InjectionManagerFactory {

    @Override
    public InjectionManager create(Object parent) {
        return parent instanceof InjectionManager ? (InjectionManager) parent : super.create(parent);
    }
}
