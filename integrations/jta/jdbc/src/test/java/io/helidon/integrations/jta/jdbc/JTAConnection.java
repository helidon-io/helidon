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
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.transaction.xa.Xid;
import javax.transaction.xa.XAResource;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

final class JTAConnection {

    private JTAConnection() {
        super();
    }

    public static Connection munge(TransactionManager tm, Connection canonicalConnection) {
        return (Connection) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                                   new Class<?>[] { Connection.class, Enlisted.class, LocalXAResource2.Enlistable.class },
                                                   new Handler(tm, canonicalConnection, Handler::sink));
    }

    private static final class Handler extends CompositeInvocationHandler<Connection> {

        private static final ThreadLocal<Connection> TL = new ThreadLocal<>();

        private final TransactionManager tm;

        private Handler(TransactionManager tm,
                        Connection c,
                        Consumer<? super Connection> closedNotifier) {
            this(tm, new LocalXAResource2(xid -> TL.get()), () -> c, closedNotifier);
        }
        
        private Handler(TransactionManager tm,
                        Supplier<? extends Connection> cs,
                        Consumer<? super Connection> closedNotifier) {
            this(tm, new LocalXAResource2(xid -> TL.get()), cs, closedNotifier);
        }
        
        private Handler(TransactionManager tm,
                        LocalXAResource2 xaResource,
                        Supplier<? extends Connection> cs,
                        Consumer<? super Connection> closedNotifier) {
            super(cs, createList(tm, xaResource, cs, closedNotifier));
            this.tm = tm;
        }

        @SuppressWarnings("unchecked")
        private static List<ConditionalInvocationHandler<Connection>> createList(final TransactionManager tm,
                                                                                 final XAResource xaResource,
                                                                                 Supplier<? extends Connection> cs,
                                                                                 Consumer<? super Connection> closedNotifier) {
            Objects.requireNonNull(tm, "tm");
            Objects.requireNonNull(xaResource, "cs");
            List<ConditionalInvocationHandler<Connection>> returnValue = new ArrayList<>();

            // Handle equals(), hashCode(), etc.
            returnValue.add(new ObjectMethods<>(cs));

            // Handle methods that return things that have
            // getConnection() methods that "point back" at our
            // proxied connection.
            returnValue.add(new CreateChildProxyHandler<Connection, Wrapper>(cs,
                                                                             Handler::test,
                                                                             m -> (Class<? extends Wrapper>)m.getReturnType(),
                                                                             Handler::createChildProxyHandler,
                                                                             Handler::sink) {
                    protected Object invoke(Object proxy, Connection delegate, Method method, Object[] arguments)
                        throws Throwable {
                        String name = method.getName();
                        if (name.equals("createStatement") || name.startsWith("prepare")) {
                            // TODO: prepareStatement() et al. are
                            // "write" operations so should cause
                            // transaction enlistment
                            try {
                                Transaction t = tm.getTransaction();
                                if (t != null && t.getStatus() == Status.STATUS_ACTIVE) {
                                    try {
                                        TL.set((Connection) proxy);
                                        t.enlistResource(xaResource);
                                    } finally {
                                        TL.set(null);
                                    }
                                }
                            } catch (SystemException e) {
                                throw new SQLException(e.getMessage(), "25000" /* invalid transaction state, no subclass */, e);
                            }
                        }
                        return super.invoke(proxy, delegate, method, arguments);
                    }
                });

            // Handle close() and isClosed().
            returnValue.add(new UncloseableHandler<>(cs, Handler::connectionIsClosed, closedNotifier, Handler::sink));

            // Handle getId() and enlist().
            returnValue.add(new EnlistableHandler<>(cs, Handler::sink));
            
            // Handle unwrap() and isWrapperFor() so that the proxied
            // connection, which also implements some of our
            // interfaces, can be unwrapped "into" those interfaces.
            returnValue.add(new Unwrapper<>(cs, Handler::sink));

            return returnValue;
        }

        private static final void sink(Object o1) {}
        
        private static final void sink(Object o1, Object o2) {}

        private static final boolean connectionIsClosed(Connection c) {
            try {
                return c.isClosed();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
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

        private static final class Unwrapper<D extends Wrapper> extends ConditionalInvocationHandler<D> {
            
            private Unwrapper(Supplier<? extends D> delegateSupplier,
                              BiConsumer<? super D, ? super Throwable> errorNotifier) {
                super(delegateSupplier, Unwrapper::test, errorNotifier);
            }

            @Override // ConditionalInvocationHandler
            protected Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
                String name = method.getName();
                if (name.equals("isWrapperFor")) {
                    return isWrapperFor(proxy, delegate, (Class<?>) arguments[0]);
                } else if (name.equals("unwrap")) {
                    return unwrap(proxy, delegate, (Class<?>) arguments[0]);
                } else {
                    return super.invoke(proxy, delegate, method, arguments);
                }
            }

            private static boolean test(Object proxy, Object delegate, Method m, Object arguments) {
                if (m.getDeclaringClass() == Wrapper.class
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == Class.class) {
                    String name = m.getName();
                    return name.equals("isWrapperFor") || name.equals("unwrap");
                }
                return false;
            }

            private static boolean isWrapperFor(Object proxy, Wrapper delegate, Class<?> iface) throws SQLException {
                return iface.isInstance(proxy) || delegate.isWrapperFor(iface);                
            }

            private static <T> T unwrap(Object proxy, Wrapper delegate, Class<T> iface) throws SQLException {
                if (iface.isInstance(proxy)) {
                    return iface.cast(proxy);
                }
                return delegate.unwrap(iface);
            }
            
        }

        private static final class EnlistableHandler<D> extends ConditionalInvocationHandler<D> {

            private volatile Xid id;
            
            private EnlistableHandler(Supplier<? extends D> delegateSupplier,
                                      BiConsumer<? super Object, ? super Throwable> errorNotifier) {
                super(delegateSupplier, EnlistableHandler::test, errorNotifier);
            }

            private static boolean test(Object proxy, Object delegate, Method m, Object arguments) {
                return
                    (m.getDeclaringClass() == Enlisted.class && m.getName().equals("getId"))
                    || (m.getDeclaringClass() == LocalXAResource2.Enlistable.class && m.getName().equals("enlist"));
            }

            @Override // ConditionalInvocationHandler
            @SuppressWarnings("unchecked")
            protected Object invoke(Object proxy, D delegate, Method m, Object[] arguments) throws Throwable {
                if (m.getName().equals("getId")) {
                    return delegate instanceof Enlisted<?> e ? e.getId() : this.id;
                } else if (m.getName().equals("enlist")) {
                    if (delegate instanceof LocalXAResource2.Enlistable e) {
                        e.enlist((Xid) arguments[0]);
                    } else {
                        this.id = (Xid) arguments[0];
                    }
                    return null; // (void return type)
                } else {
                    return super.invoke(proxy, delegate, m, arguments);
                }                
            }
            
        }
        
    }

    private static interface Enlisted<T> {

        T getId();
        
    }
    
}
