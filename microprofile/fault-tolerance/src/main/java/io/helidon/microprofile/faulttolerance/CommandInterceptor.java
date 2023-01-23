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

import java.lang.System.Logger.Level;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Intercepts calls to FT methods and implements annotation semantics.
 */
@Interceptor
@CommandBinding
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class CommandInterceptor {

    private static final System.Logger LOGGER = System.getLogger(CommandInterceptor.class.getName());

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
            LOGGER.log(Level.DEBUG, "Interceptor called for '" + context.getTarget().getClass()
                        + "::" + context.getMethod().getName() + "'");

            // Create method introspector and executer retrier
            MethodIntrospector introspector = new MethodIntrospector(context.getTarget().getClass(),
                    context.getMethod());
            MethodInvoker runner = new MethodInvoker(context, introspector);
            return runner.get();
        } catch (Throwable t) {
            LOGGER.log(Level.DEBUG, "Throwable caught by interceptor '" + t.getMessage() + "'");
            throw t;
        }
    }
}
