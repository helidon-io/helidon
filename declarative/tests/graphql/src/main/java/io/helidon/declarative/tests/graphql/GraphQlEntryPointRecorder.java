/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import io.helidon.common.Weight;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@SuppressWarnings("helidon:api:internal")
@Service.Singleton
@Weight(1000)
class GraphQlEntryPointRecorder implements Interception.EntryPointInterceptor, HttpEntryPoint.AuthorizationInterceptor {
    private static final Queue<String> EXECUTIONS = new ArrayBlockingQueue<>(100);
    private static final Queue<String> AUTHORIZATIONS = new ArrayBlockingQueue<>(100);

    @Override
    public <T> T proceed(InterceptionContext invocationContext,
                         Interception.Interceptor.Chain<T> chain,
                         Object... args) throws Exception {
        EXECUTIONS.add(definition(invocationContext));

        return chain.proceed(args);
    }

    @Override
    public void authorize(InterceptionContext invocationContext,
                          HttpEntryPoint.Interceptor.Chain chain,
                          ServerRequest request,
                          ServerResponse response) throws Exception {
        AUTHORIZATIONS.add(definition(invocationContext));
        chain.proceed(request, response);
    }

    static void reset() {
        EXECUTIONS.clear();
        AUTHORIZATIONS.clear();
    }

    static List<String> executions() {
        return new ArrayList<>(EXECUTIONS);
    }

    static List<String> authorizations() {
        return new ArrayList<>(AUTHORIZATIONS);
    }

    private static String definition(InterceptionContext invocationContext) {
        return invocationContext.serviceInfo().serviceType().fqName()
                + "." + invocationContext.elementInfo().signature().text();
    }
}
