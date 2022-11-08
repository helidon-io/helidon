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
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.transaction.xa.Xid;
import javax.transaction.xa.XAResource;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

final class JTAConnection {


    /*
     * Constructors.
     */


    private JTAConnection() {
        super();
    }


    /*
     * Static methods.
     */


    public static Connection munge(TransactionManager tm, Connection canonicalConnection) {
        return (Connection) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                                   new Class<?>[] { Connection.class,
                                                           Enlisted.class, LocalXAResource2.Enlistable.class },
                                                   new Handler(tm, canonicalConnection));
    }


    /*
     * Inner and nested classes.
     */


    private static final class Handler extends CompositeInvocationHandler<Connection> {


        /*
         * Static fields.
         */


        private static final Semaphore CONNECTION_SEMAPHORE = new Semaphore(1);

        // Deliberately not volatile.
        // @GuardedBy("CONNECTION_SEMAPHORE") // in a manner of speaking
        private static Connection CONNECTION;


        /*
         * Instance fields.
         */


        private final TransactionManager tm;


        /*
         * Constructors.
         */


        private Handler(TransactionManager tm, Connection c) {
            this(tm, new LocalXAResource2(Handler::connection), () -> c, Handler::sink);
        }

        private Handler(TransactionManager tm,
                        LocalXAResource2 xaResource,
                        Supplier<? extends Connection> cs,
                        Consumer<? super Connection> closedNotifier) {
            super(cs, createList(tm, xaResource, cs, closedNotifier));
            this.tm = tm;
        }


        /*
         * Static methods.
         */


        // (Method reference.)
        private static Connection connection(Xid ignored) {
            // Ensure someone, namely the enlist() method, already
            // acquired the 1-count Semaphore.
            assert CONNECTION_SEMAPHORE.availablePermits() == 0;
            return CONNECTION;
        }

        // (Method reference.)
        @SuppressWarnings("unchecked")
        private static List<ConditionalInvocationHandler<Connection>> createList(final TransactionManager tm,
                                                                                 final XAResource xaResource,
                                                                                 Supplier<? extends Connection> cs,
                                                                                 Consumer<? super Connection> closedNotifier) {
            ArrayList<ConditionalInvocationHandler<Connection>> returnValue = new ArrayList<>(6);

            // Handle equals(), hashCode(), wait(), notify(), toString(), etc.
            returnValue.add(new ObjectMethods<>(cs));

            // Handle methods that return things that have
            // getConnection() methods that "point back" at our
            // proxied connection.  As of JDBC 4.3, those are methods
            // that return Statement objects and those that return
            // DatabaseMetaData objects.  (Statement objects of course
            // can create ResultSet objects, so the same sort of
            // handling has to propagate there as well.)  Some of
            // those methods (the Statement-returning ones) also need
            // to check the transaction status.
            returnValue.add(new CreateChildProxyHandler<Connection, Wrapper>(cs,
                                                                             Handler::test,
                                                                             m -> (Class<? extends Wrapper>)m.getReturnType(),
                                                                             Handler::createChildProxyHandler) {
                    protected Object invoke(Object proxy, Connection delegate, Method method, Object[] arguments)
                        throws Throwable {
                        // String name = method.getName();
                        // if (name.equals("createStatement") || name.startsWith("prepare")) {
                        enlist(tm, xaResource, delegate);
                        // }
                        return super.invoke(proxy, delegate, method, arguments);
                    }
                });

            // Handle close() and isClosed().
            returnValue.add(new UncloseableHandler<>(cs, Handler::connectionIsClosed, closedNotifier));

            // TODO: handle abort(Executor)? setAutoCommit(),
            // commit(), rollback(), setSavepoint(),
            // releaseSavepoint() etc.

            // Handle getId() and enlist().
            returnValue.add(new EnlistableHandler<>(cs));

            // Handle unwrap() and isWrapperFor() so that the proxied
            // connection, which also implements some of our
            // interfaces, can be unwrapped "into" those interfaces.
            returnValue.add(new Unwrapper<>(cs));

            // For all other Connection-defined methods, enroll in the
            // JTA transaction if it is active, then forward to the
            // delegate implementation.
            returnValue.add(new ConditionalInvocationHandler<>(cs) {
                    @Override
                    protected final Object invoke(Object proxy, Connection delegate, Method method, Object[] arguments)
                        throws Throwable {
                        enlist(tm, xaResource, delegate);
                        return super.invoke(proxy, delegate, method, arguments);
                    }
                });

            return returnValue;
        }

        private static void enlist(TransactionManager tm, XAResource xaResource, Connection delegate) throws SQLException {
            if (tm != null) {
                try {
                    Transaction t = tm.getTransaction();
                    if (t != null && t.getStatus() == Status.STATUS_ACTIVE) {
                        CONNECTION_SEMAPHORE.acquire(1);
                        try {
                            // (See the finally block below.)
                            assert CONNECTION == null;
                            CONNECTION = delegate;
                            if (t.enlistResource(xaResource)) {
                                // TODO: any synchronizations?
                            }
                        } finally {
                            // We successfully handed off the
                            // Connection to the XAResource's start()
                            // method, indirectly, by way of
                            // Transaction#enlistResource(XAResource),
                            // which is obligated to call
                            // XAResource#start(Xid, int) on the same
                            // thread.
                            CONNECTION = null;
                            CONNECTION_SEMAPHORE.release(1);
                        }
                    }
                } catch (InterruptedException e) {
                    // The CONNECTION_SEMAPHORE could not be acquired
                    // for some absolutely unfathomable reason. Make
                    // sure the current thread keeps its interrupted
                    // status.
                    //
                    // (Given the finally block above (which is the
                    // only place where the CONNECTION_SEMAPHORE is
                    // ever released, but, by the same token, it is a
                    // *finally block*, so it will *always happen*)
                    // and the connection() method which simply reads
                    // CONNECTION while the CONNECTION_SEMAPHORE is
                    // acquired, it's hard to see how this state could
                    // ever obtain. If, crazily, it did obtain, it's
                    // not immediately clear to me whether we should
                    // try to release the CONNECTION_SEMAPHORE here or
                    // not. I opt not to do so to preserve semantics
                    // that might be useful for truly wild debugging.)
                    Thread.currentThread().interrupt();
                    throw new SQLException(e.getMessage(),
                                           "25000" /* invalid transaction state, no subclass */,
                                           e);
                } catch (RollbackException e) {
                    throw new SQLException(e.getMessage(),
                                           "40000" /* transaction rollback, no subclass */,
                                           e);
                } catch (SystemException e) {
                    // Hard to know what SQLState to use here. Either
                    // 25000 or 35000.
                    throw new SQLException(e.getMessage(),
                                           "25000" /* invalid transaction state, no subclass */,
                                           e);
                }
            }
        }

        // (Method reference.)
        private static void sink(Object ignored) {}

        // (Method reference.)
        private static boolean connectionIsClosed(Connection c) {
            try {
                return c.isClosed();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        }

        // (Method reference.)
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

        // (Method reference.)
        private static InvocationHandler createChildProxyHandler(Connection proxiedCreator,
                                                                 Object childDelegate,
                                                                 BiConsumer<? super Wrapper, ? super Throwable> errorNotifier) {
            if (!(proxiedCreator instanceof Proxy)) {
                throw new IllegalArgumentException("proxiedCreator: " + proxiedCreator);
            }
            if (childDelegate instanceof Statement statement) {
                return new StatementHandler(proxiedCreator, statement, errorNotifier);
            } else if (childDelegate instanceof DatabaseMetaData dmd) {
                return new DatabaseMetaDataHandler(proxiedCreator, dmd, errorNotifier);
            }
            throw new IllegalArgumentException("childDelegate: " + childDelegate);
        }


        /*
         * Inner and nested classes.
         */


        private static final class Unwrapper<D extends Wrapper> extends ConditionalInvocationHandler<D> {


            /*
             * Constructors.
             */


            private Unwrapper(Supplier<? extends D> delegateSupplier) {
                super(delegateSupplier, Unwrapper::test);
            }


            /*
             * Instance methods.
             */


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


            /*
             * Static methods.
             */


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


            /*
             * Instance fields.
             */


            private volatile Xid id;


            /*
             * Constructors.
             */


            private EnlistableHandler(Supplier<? extends D> delegateSupplier) {
                super(delegateSupplier, EnlistableHandler::test);
            }


            /*
             * Instance methods.
             */


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


            /*
             * Static methods.
             */


            // (Method reference.)
            private static boolean test(Object proxy, Object delegate, Method m, Object arguments) {
                return
                    (m.getDeclaringClass() == Enlisted.class && m.getName().equals("getId"))
                    || (m.getDeclaringClass() == LocalXAResource2.Enlistable.class && m.getName().equals("enlist"));
            }

        }

    }

    private static interface Enlisted<T> {


        /*
         * Instance methods.
         */


        T getId();

    }

}
