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

import java.lang.reflect.Method;
import java.sql.Wrapper;

final class UnwrapHandler extends DelegatingHandler<Wrapper> {

    UnwrapHandler(Wrapper delegate) {
        this(null, delegate);
    }
    
    UnwrapHandler(Handler handler, Wrapper delegate) {
        super(handler, delegate);
    }

    @Override // DelegatingHandler<Wrapper>
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object returnValue = UNHANDLED;
        if (method.getDeclaringClass() == Wrapper.class
            && method.getParameterCount() == 1
            && method.getParameterTypes()[0] == Class.class) {
            Class<?> iface = (Class<?>) arguments[0];
            switch (method.getName()) {
            case "isWrapperFor":
                if (method.getReturnType() == boolean.class) {
                    returnValue = iface.isInstance(proxy) || this.delegate().isWrapperFor(iface);
                }
                break;
            case "unwrap":
                if (method.getReturnType() == Object.class) {
                    if (iface.isInstance(proxy)) {
                        returnValue = iface.cast(proxy);
                    } else {
                        returnValue = this.delegate().unwrap(iface);
                    }
                }
                break;
            default:
                break;
            }
        }
        if (returnValue == UNHANDLED) {
            returnValue = super.invoke(proxy, method, arguments);
        }
        return returnValue;
        
    }

}
