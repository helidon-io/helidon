/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.hibernate;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.hibernate.engine.jndi.spi.JndiService;
import org.hibernate.engine.transaction.jta.platform.internal.AbstractJtaPlatform;

/**
 * An {@link AbstractJtaPlatform} that is an {@link ApplicationScoped}
 * CDI managed bean that supplies {@link TransactionManager} and
 * {@link UserTransaction} instances that are supplied to it at
 * {@linkplain #CDISEJtaPlatform(TransactionManager, UserTransaction)
 * construction time}.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see AbstractJtaPlatform
 */
@ApplicationScoped
public class CDISEJtaPlatform extends AbstractJtaPlatform {

    private final TransactionManager transactionManager;

    private final UserTransaction userTransaction;

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new, <strong>nonfunctional</strong> {@link
     * CDISEJtaPlatform}.
     *
     * <p>This constructor exists only to satisfy <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#unproxyable"
     * target="_parent">section 3.15 of the CDI specification</a> and
     * for no other purpose.</p>
     *
     * @see #CDISEJtaPlatform(TransactionManager, UserTransaction)
     *
     * @deprecated Please use the {@link
     * #CDISEJtaPlatform(TransactionManager, UserTransaction)}
     * constructor instead.
     */
    @Deprecated
    CDISEJtaPlatform() {
        super();
        this.transactionManager = null;
        this.userTransaction = null;
    }

    /**
     * Creates a new {@link CDISEJtaPlatform}.
     *
     * @param transactionManager the {@link TransactionManager} to
     * use; must not be {@code null}
     *
     * @param userTransaction the {@link UserTransaction} to use; must
     * not be {@code null}
     *
     * @exception NullPointerException if either {@code
     * transactionManager} or {@code userTransaction} is {@code null}
     */
    @Inject
    public CDISEJtaPlatform(final TransactionManager transactionManager,
                            final UserTransaction userTransaction) {
        super();
        this.transactionManager = Objects.requireNonNull(transactionManager);
        this.userTransaction = Objects.requireNonNull(userTransaction);
    }

    /**
     * Throws an {@link UnsupportedOperationException} when invoked.
     *
     * @return (not applicable)
     *
     * @exception UnsupportedOperationException when invoked
     */
    @Override
    protected JndiService jndiService() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link UserTransaction} instance supplied at
     * {@linkplain #CDISEJtaPlatform(TransactionManager,
     * UserTransaction) construction time}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link UserTransaction}
     *
     * @exception IllegalStateException if this {@link
     * CDISEJtaPlatform} was constructed using its deprecated
     * package-level zero-argument constructor
     *
     * @see #CDISEJtaPlatform(TransactionManager, UserTransaction)
     */
    @Override
    protected UserTransaction locateUserTransaction() {
        if (this.userTransaction == null) {
            throw new IllegalStateException();
        }
        return this.userTransaction;
    }

    /**
     * Returns the {@link TransactionManager} instance supplied at
     * {@linkplain #CDISEJtaPlatform(TransactionManager,
     * UserTransaction) construction time}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link TransactionManager}
     *
     * @exception IllegalStateException if this {@link
     * CDISEJtaPlatform} was constructed using its deprecated
     * package-level zero-argument constructor
     *
     * @see #CDISEJtaPlatform(TransactionManager, UserTransaction)
     */
    @Override
    protected TransactionManager locateTransactionManager() {
        if (this.transactionManager == null) {
            throw new IllegalStateException();
        }
        return this.transactionManager;
    }

}
