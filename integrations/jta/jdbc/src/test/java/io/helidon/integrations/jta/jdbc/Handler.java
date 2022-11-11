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
import java.lang.reflect.Method;

class Handler implements InvocationHandler {

    protected static final Object UNHANDLED = new Object();

    private final Handler handler;

    Handler() {
        this(null);
    }

    Handler(Handler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object returnValue = this.handler == null ? UNHANDLED : this.handler.invoke(proxy, method, arguments);
        if (returnValue == UNHANDLED && method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
            case "hashCode":
                returnValue = System.identityHashCode(proxy);
                break;
            case "equals":
                returnValue = proxy == arguments[0];
                break;
            case "toString":
                returnValue = this.toString(proxy);
                break;
            default:
                break;
            }
        }
        return returnValue;
    }

    protected String toString(Object proxy) {
        return proxy == null ? "null" : proxy.getClass().getName() + "@" + System.identityHashCode(proxy);
    }
  
}
