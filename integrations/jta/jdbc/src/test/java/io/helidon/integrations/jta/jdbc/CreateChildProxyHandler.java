/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.reflect.Proxy.newProxyInstance;

final class CreateChildProxyHandler extends Handler {

    private final ChildInvocationHandlerCreator childInvocationHandlerCreator;

    CreateChildProxyHandler(Handler handler, ChildInvocationHandlerCreator childInvocationHandlerCreator) {
        super(handler);
        this.childInvocationHandlerCreator = childInvocationHandlerCreator;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object returnValue = super.invoke(proxy, method, arguments);
        if (returnValue == UNHANDLED && method.getDeclaringClass() != Object.class) { // easy optimization
            InvocationHandler childHandler = this.childInvocationHandlerCreator.create(proxy, method, arguments);
            if (childHandler != null) {
                returnValue = newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                               this.childInvocationHandlerCreator.interfaces(method),
                                               childHandler);
            }
        }
        return returnValue;
    }

    @FunctionalInterface
    static interface ChildInvocationHandlerCreator {

        InvocationHandler create(Object proxy, Method method, Object[] arguments) throws IllegalAccessException, InvocationTargetException;

        default Class<?>[] interfaces(Method method) {
            return new Class<?>[] {method.getReturnType()};
        }
        
    }
  
}
