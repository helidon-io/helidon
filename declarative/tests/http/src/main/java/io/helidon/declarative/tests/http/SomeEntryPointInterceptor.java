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

package io.helidon.declarative.tests.http;

import io.helidon.common.Weight;
import io.helidon.service.registry.EntryPointInterceptor;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(1000)
class SomeEntryPointInterceptor implements EntryPointInterceptor {
    @Override
    public <T> T proceed(InterceptionContext invocationContext, Interception.Interceptor.Chain<T> chain, Object... args)
            throws Exception {
        String definition = invocationContext.serviceInfo().serviceType().fqName()
                + "." + invocationContext.elementInfo().signature().text();
        System.out.println("Pre entry-point: " + definition);
        try {
            return chain.proceed(args);
        } finally {
            System.out.println("Post entry-point: " + definition);
        }
    }
}
