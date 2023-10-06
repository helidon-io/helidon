/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger;
import java.util.Objects;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.hibernate.engine.jndi.spi.JndiService;
import org.hibernate.engine.transaction.jta.platform.internal.AbstractJtaPlatform;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hibernate.cfg.AvailableSettings.CONNECTION_HANDLING;
import static org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION;

/**
 * An {@link AbstractJtaPlatform} that is an {@link ApplicationScoped}
 * CDI managed bean that supplies {@link TransactionManager} and
 * {@link UserTransaction} instances that are supplied to it at
 * {@linkplain #CDISEJtaPlatform(TransactionManager, UserTransaction)
 * construction time}.
 *
 * @see AbstractJtaPlatform
 */
@ApplicationScoped
public class CDISEJtaPlatform extends AbstractJtaPlatform {

    private static final Logger LOGGER = System.getLogger(CDISEJtaPlatform.class.getName());

    private static final long serialVersionUID = 1L;

    private transient TransactionManager transactionManager;

    private transient UserTransaction userTransaction;

    /**
     * Creates a new {@link CDISEJtaPlatform}.
     *
     * @deprecated <a href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0#unproxyable">Required by the
     * CDI specification</a> and not intended for end-user use.
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
     * @param transactionManager the {@link TransactionManager} to use;
     * must not be {@code null}
     *
     * @param userTransaction the {@link UserTransaction} to use; must
     * not be {@code null}
     *
     * @exception NullPointerException if either {@code
     * transactionManager} or {@code userTransaction} is {@code null}
     */
    @Inject
    public CDISEJtaPlatform(TransactionManager transactionManager,
                            UserTransaction userTransaction) {
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
     * @see #CDISEJtaPlatform(TransactionManager, UserTransaction)
     */
    @Override
    protected UserTransaction locateUserTransaction() {
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
     * @see #CDISEJtaPlatform(TransactionManager, UserTransaction)
     */
    @Override
    protected TransactionManager locateTransactionManager() {
        return this.transactionManager;
    }

    /**
     * Customizes the supplied {@link PersistenceUnitInfo}, when it is fired as a CDI event by, for example, the {@code
     * io.helidon.integrations.cdi.jpa.PersistenceExtension} portable extension, by ensuring that certain important
     * Hibernate properties are always set on the persistence unit.
     *
     * @param pui the {@link PersistenceUnitInfo} to customize; must not be {@code null}
     *
     * @exception NullPointerException if {@code pui} is {@code null}
     *
     * @see
     * org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode#DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION
     */
    private static void customizePersistenceUnitInfo(@Observes PersistenceUnitInfo pui) {
        Properties p = pui.getProperties();
        if (p != null && p.getProperty(CONNECTION_HANDLING) == null && p.get(CONNECTION_HANDLING) == null) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Setting " + CONNECTION_HANDLING + " property to "
                           + DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION
                           + " on persistence unit " + pui.getPersistenceUnitName());
            }
            p.setProperty(CONNECTION_HANDLING, DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION.toString());
        }
    }

}
