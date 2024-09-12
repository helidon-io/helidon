/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.interception;

import java.util.HashSet;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InvocationContext;

@Injection.Singleton
@Injection.NamedByClass(Construct.class)
class ConstructorInterceptor implements Interception.Interceptor {
    static final Set<TypeName> CONSTRUCTED = new HashSet<>();

    @Override
    public <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) throws Exception {
        CONSTRUCTED.add(ctx.serviceInfo().serviceType());
        return chain.proceed(args);
    }
}
