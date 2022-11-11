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
import java.util.function.BiConsumer;

final class UncloseableHandler extends DelegatingHandler<Object> implements Enableable {

    private final BiConsumer<? super Enableable, ? super Object> closedNotifier;

    private volatile boolean enabled;

    private volatile boolean closed;

    UncloseableHandler(Handler handler, Object delegate, BiConsumer<? super Enableable, ? super Object> closedNotifier) {
        super(handler,
              delegate,
              m -> {
                  if (m.getParameterCount() == 0) {
                      switch (m.getName()) {
                      case "isClosed":
                          return m.getReturnType() == boolean.class;
                      case "close":
                          return m.getReturnType() == void.class;
                      default:
                          break;
                      }
                  }
                  return false;
              });
        this.closedNotifier = closedNotifier;
        this.enabled = true;
    }

    @Override // DelegatingHandler<Object>
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object returnValue = UNHANDLED;
        if (method.getParameterCount() == 0 && this.enabled) {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class && method.getName().equals("isClosed") && this.closed) {
                returnValue = true;
            } else if (returnType == void.class && method.getName().equals("close")) {
                this.closed = true;
                this.closedNotifier.accept(this, this.delegate());
                returnValue = null;
            }
        }
        if (returnValue == UNHANDLED) {
            returnValue = super.invoke(proxy, method, arguments);
        }
        return returnValue;
    }

    @Override // Enableable
    public void enable(boolean enable) {
        this.enabled = enable;
        if (!enable) {
            this.closed = false;
        }
    }

}
