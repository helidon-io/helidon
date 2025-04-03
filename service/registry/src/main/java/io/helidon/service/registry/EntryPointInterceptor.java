/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

/**
 * Interceptor around initial entry into the Helidon system.
 * <p>
 * The following should be considered entry points:
 * <ul>
 *     <li>HTTP Request on the WebServer (includes grpc, initial web-socket request, graphQL)</li>
 *     <li>Messaging inbound message handled by Helidon component</li>
 *     <li>WebSocket message</li>
 *     <li>Scheduling trigger</li>
 * </ul>
 * The exceptional behavior of this interceptor is that it is invoked exactly once for each entry
 * point. This can be used for resource management, such as when there is a requirement to close
 * resources after the request finishes.
 * <p>
 * Important note: entry point interceptors only trigger for entry points fully managed by Helidon, such as when
 * using Helidon Declarative. This is not triggered when using injection "just" to set up the application.
 */
@Service.Contract
public interface EntryPointInterceptor {
    /*
    Development note:
    This cannot be handled using regular interceptors on the generated methods, as we would lose the annotations
    defined on the endpoint type and method
     */

    /**
     * Handle entry point interception.
     *
     * @param invocationContext invocation context to access type and method information
     * @param chain             interceptor chain, you must call
     *                          {@link io.helidon.service.registry.Interception.Interceptor.Chain#proceed(Object[])} to
     *                          continue
     * @param args              original parameters of the intercepted method, to be passed to proceed method
     * @param <T>               type of the returned value
     * @return result of the proceed method
     * @throws java.lang.Exception in case the proceed method throws an exception, or the interceptor encounters a problem
     *                             that should finish regular processing of the request
     */
    <T> T proceed(InterceptionContext invocationContext,
                  Interception.Interceptor.Chain<T> chain,
                  Object... args) throws Exception;
}
