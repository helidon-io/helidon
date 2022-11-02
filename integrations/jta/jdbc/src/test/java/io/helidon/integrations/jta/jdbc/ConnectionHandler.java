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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class ConnectionHandler extends CompositeInvocationHandler<Connection> {


    /*
     * Constructors.
     */


    ConnectionHandler(Connection delegate,
                      BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        this(() -> delegate, List.of(), errorNotifier);
    }

    ConnectionHandler(Connection delegate,
                      List<? extends ConditionalInvocationHandler<Connection>> list,
                      BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        this(() -> delegate, list, errorNotifier);
    }

    ConnectionHandler(Supplier<? extends Connection> delegateSupplier,
                      BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        this(delegateSupplier, List.of(), errorNotifier);
    }

    ConnectionHandler(Supplier<? extends Connection> delegateSupplier,
                      List<? extends ConditionalInvocationHandler<Connection>> list,
                      BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        super(delegateSupplier, createList(delegateSupplier, list, errorNotifier));
    }


    /*
     * Static methods.
     */


    @SuppressWarnings("unchecked")
    private static List<ConditionalInvocationHandler<Connection>>
        createList(Supplier<? extends Connection> delegateSupplier,
                   List<? extends ConditionalInvocationHandler<Connection>> list,
                   BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        List<ConditionalInvocationHandler<Connection>> returnValue = new ArrayList<>(list.size() + 2);
        returnValue.add(new ObjectMethods<>(delegateSupplier, errorNotifier));
        returnValue.add(new CreateChildProxyHandler<Connection, Wrapper>(delegateSupplier,
                                                                         ConnectionHandler::test,
                                                                         m -> (Class<? extends Wrapper>) m.getReturnType(),
                                                                         ConnectionHandler::createChildProxyHandler,
                                                                         errorNotifier));
        returnValue.addAll(list);
        return returnValue;
    }

    private static boolean test(Object proxy, Object delegate, Method method, Object arguments) {
        if (method.getDeclaringClass() == Connection.class) {
            Class<?> returnType = method.getReturnType();
            String name = method.getName();
            if (Statement.class.isAssignableFrom(returnType)) {
                return name.equals("createStatement") || name.startsWith("prepare");
            } else if (DatabaseMetaData.class == returnType) {
                return name.equals("getMetaData");
            }
        }
        return false;
    }

    private static InvocationHandler createChildProxyHandler(Connection proxiedCreator,
                                                             Object childDelegate,
                                                             BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
        if (childDelegate instanceof Statement statement) {
            return new StatementHandler(proxiedCreator, statement, errorNotifier);
        } else if (childDelegate instanceof DatabaseMetaData dmd) {
            return new DatabaseMetaDataHandler(proxiedCreator, dmd, errorNotifier);
        }
        throw new IllegalArgumentException("childDelegate: " + childDelegate);
    }

}
