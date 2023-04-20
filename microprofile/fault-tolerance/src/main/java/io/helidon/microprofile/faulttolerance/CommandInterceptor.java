/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.util.logging.Logger;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * <p>Intercepts calls to FT methods and implements annotation semantics.</p>
 *
 * <p>There is special code to handle async calls coming from the RestClient
 * implementation in Jersey due to an issue with the default invocation context
 * in Weld. See issue https://github.com/helidon-io/helidon/issues/6580.</p>
 */
@Interceptor
@CommandBinding
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class CommandInterceptor {

    private static final Logger LOGGER = Logger.getLogger(CommandInterceptor.class.getName());
    private static final String INVOCATION_CONTEXT_KEY =
            "org.glassfish.jersey.microprofile.restclient.InterceptorInvocationContext";

    /**
     * Intercepts a call to bean method annotated by any of the fault tolerance
     * annotations.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    public Object interceptCommand(InvocationContext context) throws Throwable {
        try {
            LOGGER.fine(() -> "Interceptor called for '" + context.getTarget().getClass()
                        + "::" + context.getMethod().getName() + "'");

            // Create method introspector and executer retrier
            MethodIntrospector introspector = new MethodIntrospector(context.getTarget().getClass(),
                    context.getMethod());

            // If async call triggered by RestClient, use its invocation context instead
            if (introspector.isAsynchronous() && context.getContextData().containsKey(INVOCATION_CONTEXT_KEY)) {
                InvocationContext override = (InvocationContext) context.getContextData().get(INVOCATION_CONTEXT_KEY);
                LOGGER.fine(() -> "Overriding invocation context from map " + override);
                MethodInvoker runner = new MethodInvoker(override, introspector);
                return runner.get();
            } else {
                MethodInvoker runner = new MethodInvoker(context, introspector);
                return runner.get();
            }
        } catch (Throwable t) {
            LOGGER.fine(() -> "Throwable caught by interceptor '" + t.getMessage() + "'");
            throw t;
        }
    }
}
