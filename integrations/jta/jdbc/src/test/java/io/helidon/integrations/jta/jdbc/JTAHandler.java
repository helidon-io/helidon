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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;

@Deprecated // replacing with non-proxy implementation
class JTAHandler extends ConnectionHandler {


    /*
     * Static fields.
     */


    private static final LocalXAResource XA_RESOURCE = new LocalXAResource(JTAHandler::connection);

    private static final ReentrantLock HANDOFF_LOCK = new ReentrantLock();

    // Deliberately not volatile.
    // Null most of the time on purpose.
    // When not null, will contain either a Connection or a Xid.
    // @GuardedBy("HANDOFF_LOCK")
    private static Object HANDOFF;


    /*
     * Instance fields.
     */


    private final TransactionSupplier tm;

    private volatile Xid xid;


    /*
     * Constructors.
     */


    JTAHandler(Connection delegate, TransactionSupplier tm, BiConsumer<? super Enableable, ? super Object> closedNotifier) {
        this(null, delegate, tm, closedNotifier);
    }

    JTAHandler(Handler handler,
               Connection delegate,
               TransactionSupplier tm,
               BiConsumer<? super Enableable, ? super Object> closedNotifier) {
        super(new UnclosableHandler(handler, delegate, closedNotifier), delegate);
        this.tm = tm;
    }


    /*
     * Instance methods.
     */


    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        Object returnValue = UNHANDLED;
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Connection.class) {
            this.enlist();
        } else if (method.getName().equals("xid")) {
            returnValue = this.xid;
        }
        if (returnValue == UNHANDLED) {
            returnValue = super.invoke(proxy, method, arguments);
        }
        return returnValue;
    }

    private void enlist() throws SQLException {
        if (this.tm != null && this.xid == null) {
            try {
                XAResourceEnlister activeEnlister = this.activeEnlister();
                if (activeEnlister != null) {
                    HANDOFF_LOCK.lock();
                    try {
                        HANDOFF = this.delegate();
                        if (activeEnlister.enlist(XA_RESOURCE)) {
                            this.xid = (Xid) HANDOFF;
                        }
                    } finally {
                        HANDOFF = null;
                        HANDOFF_LOCK.unlock();
                    }
                }
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

    private XAResourceEnlister activeEnlister() throws SystemException {
        Transaction t = this.tm.getTransaction();
        return t == null || t.getStatus() != Status.STATUS_ACTIVE ? null : t::enlistResource;
    }


    /*
     * Static methods.
     */


    // (Method reference.)
    private static Connection connection(Xid xid) {
        assert HANDOFF_LOCK.isHeldByCurrentThread();
        try {
            return (Connection) HANDOFF;
        } finally {
            HANDOFF = xid;
        }
    }


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    static interface TransactionSupplier {

        Transaction getTransaction() throws SystemException;

    }

    @FunctionalInterface
    static interface XAResourceEnlister {

        boolean enlist(XAResource resource) throws RollbackException, SystemException;

    }

}
