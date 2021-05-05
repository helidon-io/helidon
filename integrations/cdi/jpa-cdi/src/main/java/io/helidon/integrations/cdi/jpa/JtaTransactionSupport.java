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
package io.helidon.integrations.cdi.jpa;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;

/**
 * A {@link TransactionSupport} implementation that is loaded only if
 * JTA is available.
 *
 * <p>See the exclusion stanzas in {@code META-INF/beans.xml} for more
 * details.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are not safe for concurrent use by
 * multiple threads.</p>
 *
 * @see TransactionSupport
 *
 * @see NoTransactionSupport
 */
@ApplicationScoped
class JtaTransactionSupport implements TransactionSupport {


    /*
     * Instance fields.
     */


    private final BeanManager beanManager;

    private Context transactionScopedContext;

    private final TransactionManager transactionManager;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaTransactionSupport}.
     *
     * <p>This constructor exists only to conform to <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#unproxyable">section
     * 3.15 of the CDI specification</a> and for no other purpose.</p>
     *
     * @deprecated Please use the {@link
     * #JtaTransactionSupport(BeanManager, TransactionManager)}
     * constructor instead.
     */
    @Deprecated
    JtaTransactionSupport() {
        super();
        this.beanManager = null;
        this.transactionManager = null;
    }

    /**
     * Creates a new {@link JtaTransactionSupport}.
     *
     * @param beanManager a {@link BeanManager}; must not be {@code
     * null}
     *
     * @param transactionManager a {@link TransactionManager}; must
     * not be {@code null}
     *
     * @exception NullPointerException if either {@code beanManager}
     * or {@code transactionManager} is {@code null}
     */
    @Inject
    private JtaTransactionSupport(final BeanManager beanManager,
                                  final TransactionManager transactionManager) {
        super();
        this.beanManager = Objects.requireNonNull(beanManager);
        this.transactionManager = Objects.requireNonNull(transactionManager);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns {@code true} when invoked.
     *
     * @return {@code true} when invoked
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Returns the {@link Context} that supports JTA transactions, if
     * there is one and it is active at the moment of invocation.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the active {@link Context} that supports JTA
     * transactions, if there is one and it is active; {@code null} in
     * all other cases
     */
    @Override
    public Context getContext() {
        final Context returnValue;
        if (this.transactionScopedContext == null) {
            try {
                this.transactionScopedContext = this.beanManager.getContext(TransactionScoped.class);
            } catch (final ContextNotActiveException contextNotActiveException) {
                this.transactionScopedContext = null;
            } finally {
                returnValue = this.transactionScopedContext;
            }
        } else if (this.transactionScopedContext.isActive()) {
            returnValue = this.transactionScopedContext;
        } else {
            returnValue = null;
        }
        return returnValue;
    }

    /**
     * Returns the {@linkplain TransactionManager#getStatus() current
     * status} of the current transaction, if available.
     *
     * @return the {@linkplain TransactionManager#getStatus() current
     * status} of the current transaction, if available
     *
     * @exception IllegalStateException if there was a problem
     * acquiring status
     */
    @Override
    public int getStatus() {
        try {
            return this.transactionManager.getStatus();
        } catch (final SystemException systemException) {
            throw new IllegalStateException(systemException.getMessage(), systemException);
        }
    }

}
