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

package io.helidon.inject.codegen.javax;

import io.helidon.common.types.TypeName;

final class JavaxTypes {
    static final TypeName ANNOT_MANAGED_BEAN = TypeName.create("javax.annotation.ManagedBean");
    static final TypeName ANNOT_RESOURCE = TypeName.create("javax.annotation.Resource");
    static final TypeName ANNOT_RESOURCES = TypeName.create("javax.annotation.Resources");
    static final TypeName INJECT_SINGLETON = TypeName.create("javax.inject.Singleton");
    static final TypeName INJECT_PRE_DESTROY = TypeName.create("javax.annotation.PreDestroy");
    static final TypeName INJECT_POST_CONSTRUCT = TypeName.create("javax.annotation.PostConstruct");
    static final TypeName INJECT_INJECT = TypeName.create("javax.inject.Inject");
    static final TypeName INJECT_SCOPE = TypeName.create("javax.inject.Scope");
    static final TypeName INJECT_QUALIFIER = TypeName.create("javax.inject.Qualifier");
    static final TypeName INJECT_PROVIDER = TypeName.create("javax.inject.Provider");
    static final TypeName INJECT_NAMED = TypeName.create("javax.inject.Named");

    private JavaxTypes() {
    }
}
