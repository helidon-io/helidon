/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen.jakarta;

import io.helidon.common.types.TypeName;

final class CdiTypes {
    static final TypeName APPLICATION_SCOPED = TypeName.create("jakarta.enterprise.context.ApplicationScoped");

    static final TypeName ACTIVATE_REQUEST_CONTEXT = TypeName.create("jakarta.enterprise.context.control.ActivateRequestContext");

    static final TypeName ALTERNATIVE = TypeName.create("jakarta.enterprise.inject.Alternative");

    static final TypeName BEFORE_DESTROYED = TypeName.create("jakarta.enterprise.context.BeforeDestroyed");

    static final TypeName CONVERSATION_SCOPED = TypeName.create("jakarta.enterprise.context.ConversationScoped");

    static final TypeName DEPENDENT = TypeName.create("jakarta.enterprise.context.Dependent");

    static final TypeName DESTROYED = TypeName.create("jakarta.enterprise.context.Destroyed");

    static final TypeName DISPOSES = TypeName.create("jakarta.enterprise.inject.Disposes");

    static final TypeName INITIALIZED = TypeName.create("jakarta.enterprise.context.Initialized");

    static final TypeName INTERCEPTED = TypeName.create("jakarta.enterprise.inject.Intercepted");

    static final TypeName MODEL = TypeName.create("jakarta.enterprise.inject.Model");

    static final TypeName NONBINDING = TypeName.create("jakarta.enterprise.util.Nonbinding");

    static final TypeName NORMAL_SCOPE = TypeName.create("jakarta.enterprise.context.NormalScope");

    static final TypeName OBSERVES = TypeName.create("jakarta.enterprise.event.Observes");

    static final TypeName OBSERVES_ASYNC = TypeName.create("jakarta.enterprise.event.ObservesAsync");

    static final TypeName PRODUCES = TypeName.create("jakarta.enterprise.inject.Produces");

    static final TypeName REQUEST_SCOPED = TypeName.create("jakarta.enterprise.context.RequestScoped");

    static final TypeName SESSION_SCOPED = TypeName.create("jakarta.enterprise.context.SessionScoped");

    static final TypeName SPECIALIZES = TypeName.create("jakarta.enterprise.inject.Specializes");

    static final TypeName STEREOTYPE = TypeName.create("jakarta.enterprise.inject.Stereotype");

    static final TypeName TRANSIENT_REFERENCE = TypeName.create("jakarta.enterprise.inject.TransientReference");

    static final TypeName TYPED = TypeName.create("jakarta.enterprise.inject.Typed");

    static final TypeName VETOED = TypeName.create("jakarta.enterprise.inject.Vetoed");

    private CdiTypes() {
    }
}
