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

final class JakartaTypes {
    static final TypeName ANNOT_MANAGED_BEAN = TypeName.create("jakarta.annotation.ManagedBean");
    static final TypeName ANNOT_RESOURCE = TypeName.create("jakarta.annotation.Resource");
    static final TypeName ANNOT_RESOURCES = TypeName.create("jakarta.annotation.Resources");
    static final TypeName INJECT_SINGLETON = TypeName.create("jakarta.inject.Singleton");
    static final TypeName INJECT_PRE_DESTROY = TypeName.create("jakarta.annotation.PreDestroy");
    static final TypeName INJECT_POST_CONSTRUCT = TypeName.create("jakarta.annotation.PostConstruct");
    static final TypeName INJECT_INJECT = TypeName.create("jakarta.inject.Inject");
    static final TypeName INJECT_SCOPE = TypeName.create("jakarta.inject.Scope");
    static final TypeName INJECT_QUALIFIER = TypeName.create("jakarta.inject.Qualifier");
    static final TypeName INJECT_NAMED = TypeName.create("jakarta.inject.Named");
    static final TypeName INJECT_PROVIDER = TypeName.create("jakarta.inject.Provider");

    private JakartaTypes() {
    }
}
