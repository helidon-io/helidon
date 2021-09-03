/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.weld;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import org.jboss.weld.transaction.spi.TransactionServices;

/**
 * A {@link TransactionServices} implementation that uses the <a
 * href="http://narayana.io/" target="_parent">Narayana transaction
 * engine</a> and does not use JNDI.
 *
 * <p>{@link TransactionServices} implementations are used by <a
 * href="https://docs.jboss.org/weld/reference/latest/en-US/html/index.html"
 * target="_parent">Weld</a> for <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#transactional_observer_methods"
 * target="_parent">transactional observer notification</a> as well as
 * for providing the implementation backing the <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#additional_builtin_beans"
 * target="_parent">built-in {@code UserTransaction} CDI bean</a>.</p>
 *
 * @see TransactionServices
 */
public final class NarayanaTransactionServices implements TransactionServices {


    /*
     * Static fields.
     */


    /**
     * The {@link Logger} used by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(NarayanaTransactionServices.class.getName(),
                                                          NarayanaTransactionServices.class.getPackage().getName() + ".Messages");


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link NarayanaTransactionServices}.
     * @deprecated Only intended for service loader, do not instantiate
     */
    @Deprecated
    public NarayanaTransactionServices() {
        super();
        final String cn = NarayanaTransactionServices.class.getName();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, "<init>");
            LOGGER.exiting(cn, "<init>");
        }
    }


    /*
     * Instance methods.
     */


    /**
     * Returns the {@link UserTransaction} present in this environment
     * by invoking the {@link
     * com.arjuna.ats.jta.UserTransaction#userTransaction()} method
     * and returning its result.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The return value of this method is used as the backing
     * implementation of the <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#additional_builtin_beans"
     * target="_parent">built-in {@code UserTransaction} CDI
     * bean</a>.</p>
     *
     * @return the non-{@code null} {@link UserTransaction} present in
     * this environment
     *
     * @see com.arjuna.ats.jta.UserTransaction#userTransaction()
     */
    @Override
    public UserTransaction getUserTransaction() {
        // We don't want to use, e.g.,
        // CDI.current().select(UserTransaction.class).get() here
        // because CDI containers like Weld are obliged per the
        // specification to automatically provide a bean for
        // UserTransaction.  Weld uses the return value of this method
        // to create such a bean and we obviously need to avoid the
        // infinite loop.
        final String cn = NarayanaTransactionServices.class.getName();
        final String mn = "getUserTransaction";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }

        final Instance<JTAEnvironmentBean> jtaEnvironmentBeans = CDI.current().select(JTAEnvironmentBean.class);
        assert jtaEnvironmentBeans != null;
        final JTAEnvironmentBean jtaEnvironmentBean;
        if (jtaEnvironmentBeans.isUnsatisfied()) {
            jtaEnvironmentBean = com.arjuna.ats.jta.common.jtaPropertyManager.getJTAEnvironmentBean();
        } else {
            jtaEnvironmentBean = jtaEnvironmentBeans.get();
        }
        assert jtaEnvironmentBean != null;
        final UserTransaction returnValue = jtaEnvironmentBean.getUserTransaction();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Returns {@code true} if the current {@link Transaction}
     * {@linkplain Transaction#getStatus() has a status} indicating
     * that it is active.
     *
     * <p>This method returns {@code true} if the current {@link
     * Transaction} {@linkplain Transaction#getStatus() has a status}
     * equal to one of the following values:</p>
     *
     * <ul>
     *
     * <li>{@link Status#STATUS_ACTIVE}</li>
     *
     * <li>{@link Status#STATUS_COMMITTING}</li>
     *
     * <li>{@link Status#STATUS_MARKED_ROLLBACK}</li>
     *
     * <li>{@link Status#STATUS_PREPARED}</li>
     *
     * <li>{@link Status#STATUS_PREPARING}</li>
     *
     * <li>{@link Status#STATUS_ROLLING_BACK}</li>
     *
     * </ul>
     *
     * @return {@code true} if the current {@link Transaction}
     * {@linkplain Transaction#getStatus() has a status} indicating
     * that it is active; {@code false} otherwise
     *
     * @exception RuntimeException if an invocation of the {@link
     * Transaction#getStatus()} method resulted in a {@link
     * SystemException}
     *
     * @see Status
     */
    @Override
    public boolean isTransactionActive() {
        final String cn = NarayanaTransactionServices.class.getName();
        final String mn = "isTransactionActive";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }

        final boolean returnValue;
        final Instance<Transaction> transactions = CDI.current().select(Transaction.class);
        assert transactions != null;
        if (!transactions.isUnsatisfied()) {
            final Transaction transaction = transactions.get();
            assert transaction != null;
            boolean temp = false;
            try {
                final int status = transaction.getStatus();
                temp =
                    status == Status.STATUS_ACTIVE
                    || status == Status.STATUS_COMMITTING
                    || status == Status.STATUS_MARKED_ROLLBACK
                    || status == Status.STATUS_PREPARED
                    || status == Status.STATUS_PREPARING
                    || status == Status.STATUS_ROLLING_BACK;
            } catch (final SystemException e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                returnValue = temp;
            }
        } else {
            returnValue = false;
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, Boolean.valueOf(returnValue));
        }
        return returnValue;
    }

    /**
     * Registers the supplied {@link Synchronization} with the current
     * {@link Transaction}.
     *
     * @exception RuntimeException if an invocation of the {@link
     * TransactionManager#getTransaction()} method resulted in a
     * {@link SystemException}, or if an invocation of the {@link
     * Transaction#registerSynchronization(Synchronization)} method
     * resulted in either a {@link SystemException} or a {@link
     * RollbackException}
     *
     * @see Transaction#registerSynchronization(Synchronization)
     */
    @Override
    public void registerSynchronization(final Synchronization synchronization) {
        final String cn = NarayanaTransactionServices.class.getName();
        final String mn = "registerSynchronization";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, synchronization);
        }

        final CDI<Object> cdi = CDI.current();
        final Instance<Transaction> transactionInstance = cdi.select(Transaction.class);
        Transaction transaction = null;
        if (transactionInstance.isUnsatisfied()) {
            Instance<TransactionManager> transactionManagerInstance = cdi.select(TransactionManager.class);
            assert transactionManagerInstance != null;
            final TransactionManager transactionManager;
            if (transactionManagerInstance.isUnsatisfied()) {
                transactionManager = com.arjuna.ats.jta.TransactionManager.transactionManager();
            } else {
                transactionManager = transactionManagerInstance.get();
            }
            if (transactionManager != null) {
                try {
                    transaction = transactionManager.getTransaction();
                } catch (final SystemException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        } else {
            transaction = transactionInstance.get();
        }
        if (transaction != null) {
            try {
                transaction.registerSynchronization(synchronization);
            } catch (final SystemException | RollbackException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Releases any internal resources acquired during the lifespan of
     * this object.
     */
    @Override
    public synchronized void cleanup() {
        final String cn = NarayanaTransactionServices.class.getName();
        final String mn = "cleanup";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
            LOGGER.exiting(cn, mn);
        }
    }

}
