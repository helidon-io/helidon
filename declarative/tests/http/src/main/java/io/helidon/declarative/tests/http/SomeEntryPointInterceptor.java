/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import io.helidon.common.Weight;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Singleton
@Weight(1000)
class SomeEntryPointInterceptor implements Interception.EntryPointInterceptor {
    private static final Queue<String> EXECUTIONS = new ArrayBlockingQueue<>(100);

    @Override
    public <T> T proceed(InterceptionContext invocationContext, Interception.Interceptor.Chain<T> chain, Object... args)
            throws Exception {
        String definition = invocationContext.serviceInfo().serviceType().fqName()
                + "." + invocationContext.elementInfo().signature().text();

        EXECUTIONS.add(definition);

        return chain.proceed(args);
    }

    static void reset() {
        EXECUTIONS.clear();
    }

    static List<String> executions() {
       return new ArrayList<>(EXECUTIONS);
    }
}
