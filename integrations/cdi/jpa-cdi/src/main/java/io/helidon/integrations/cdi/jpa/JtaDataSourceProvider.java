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

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.TransactionSynchronizationRegistry;

import io.helidon.integrations.jta.jdbc.JtaDataSource;
import io.helidon.integrations.jta.jdbc.XADataSourceWrappingDataSource;

@ApplicationScoped
class JtaDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Static fields.
     */


    /**
     * A token to use as a key in the {@link #dataSourcesByName} field
     * value for a data source name when the real data source name is
     * {@code null}.
     *
     * <p>Real data source names can be {@code null} and the empty
     * string ({@code ""}), so a different value is used here.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final String NULL_DATASOURCE_NAME = "\u0000";


    /*
     * Instance fields.
     */


    /**
     * An {@link Instance} providing access to CDI contextual
     * references.
     *
     * <p>This field may be {@code null} if the {@linkplain
     * #JtaDataSourceProvider() deprecated zero-argument constructor
     * of this class} is used.</p>
     */
    private final Instance<Object> objects;

    /**
     * The {@link TransactionManager} for the system.
     *
     * <p>This field may be {@code null} if the {@linkplain
     * #JtaDataSourceProvider() deprecated zero-argument constructor
     * of this class} is used.</p>
     */
    private final TransactionManager transactionManager;

    private final TransactionSynchronizationRegistry tsr;

    /**
     * A thread-safe {@link Map} (usually a {@link ConcurrentHashMap})
     * that stores {@link JtaDataSource} instances under their names.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <h2>Design Notes</h2>
     *
     * <p>{@link DataSource} instances used by instances of this class
     * are normally CDI contextual references, so are client proxies.
     * Per the specification, a client proxy's {@link
     * Object#equals(Object)} and {@link Object#hashCode()} methods do
     * not behave in such a way that their underlying contextual
     * instances can be tested for equality.  When these {@link
     * DataSource}s are wrapped by {@link JtaDataSource} instances, we
     * need to ensure that the same {@link JtaDataSource} is handed
     * out each time a given data source name is supplied.  This
     * {@link Map} provides those semantics.</p>
     *
     * @see JtaDataSource
     *
     * @see <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#client_proxy_invocation">section
     * 5.4.1 of the CDI 2.0 specification</a>
     */
    private final Map<String, DataSource> dataSourcesByName;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaDataSourceProvider}.
     *
     * <p>This constructor exists only to conform to <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#unproxyable">section
     * 3.15 of the CDI specification</a> and for no other purpose.</p>
     *
     * @deprecated Please use the {@link
     * #JtaDataSourceProvider(Instance, TransactionManager,
     * TransactionSynchronizationRegistry)} constructor instead.
     */
    @Deprecated
    JtaDataSourceProvider() {
        super();
        this.objects = null;
        this.transactionManager = null;
        this.tsr = null;
        this.dataSourcesByName = null;
    }

    /**
     * Creates a new {@link JtaDataSourceProvider}.
     *
     * @param objects an {@link Instance} providing access to CDI
     * beans; must not be {@code null}
     *
     * @param transactionManager a {@link TransactionManager}; must
     * not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must
     * not be {@code null}
     *
     * @exception NullPointerException if either {@code objects} or
     * {@code transactionManager} or {@code tsr} is {@code null}
     */
    @Inject
    JtaDataSourceProvider(final Instance<Object> objects,
                          final TransactionManager transactionManager,
                          final TransactionSynchronizationRegistry tsr) {
        super();
        this.objects = Objects.requireNonNull(objects);
        this.transactionManager = Objects.requireNonNull(transactionManager);
        this.tsr = Objects.requireNonNull(tsr);
        this.dataSourcesByName = new ConcurrentHashMap<>();
    }


    /*
     * Instance methods.
     */


    /**
     * Supplies a {@link DataSource}.
     *
     * <p>Implementations of this method are permitted to return
     * {@code null}.</p>
     *
     * @param jta if {@code true}, the {@link DataSource} that is
     * returned may be enrolled in JTA-compliant transactions
     *
     * @param useDefaultJta if {@code true}, and if the {@code
     * jta} parameter value is {@code true}, the supplied {@code
     * dataSourceName} may be ignored and a default {@link
     * DataSource} eligible for enrolling in JTA-compliant
     * transactions will be returned if possible
     *
     * @param dataSourceName the name of the {@link DataSource} to
     * return; may be {@code null}; ignored if both {@code jta}
     * and {@code useDefaultJta} are {@code true}
     *
     * @return an appropriate {@link DataSource}, or {@code null} in
     * edge cases
     *
     * @exception IllegalStateException if a {@link SQLException}
     * occurs
     *
     * @see PersistenceUnitInfoBean#getJtaDataSource()
     *
     * @see PersistenceUnitInfoBean#getNonJtaDataSource()
     */
    @Override
    public DataSource getDataSource(final boolean jta,
                                    final boolean useDefaultJta,
                                    final String dataSourceName) {
        final DataSource returnValue;
        if (jta) {
            try {
                if (dataSourceName == null) {
                    if (useDefaultJta) {
                        final Instance<XADataSource> xaDataSources = this.objects.select(XADataSource.class);
                        if (xaDataSources.isUnsatisfied()) {
                            returnValue = this.convert(this.objects.select(DataSource.class).get(), jta, null);
                        } else {
                            returnValue = this.convert(xaDataSources.get(), jta, null);
                        }
                    } else {
                        returnValue = null;
                    }
                } else {
                    final Named named = NamedLiteral.of(dataSourceName);
                    final Instance<XADataSource> xaDataSources = this.objects.select(XADataSource.class, named);
                    if (xaDataSources.isUnsatisfied()) {
                        returnValue = this.convert(this.objects.select(DataSource.class, named).get(), jta, dataSourceName);
                    } else {
                        returnValue = this.convert(xaDataSources.get(), jta, dataSourceName);
                    }
                }
            } catch (final SQLException sqlException) {
                throw new IllegalStateException(sqlException.getMessage(), sqlException);
            }
        } else if (dataSourceName == null) {
            returnValue = null;
        } else {
            returnValue = this.objects.select(DataSource.class, NamedLiteral.of(dataSourceName)).get();
        }
        return returnValue;
    }

    /**
     * Converts the supplied {@link XADataSource} to a {@link
     * DataSource} and returns it.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param xaDataSource the {@link XADataSource} to convert; must
     * not be {@code null}
     *
     * @param jta whether JTA semantics are in effect
     *
     * @param dataSourceName the name of the data source; may be (and
     * often is) {@code null}
     *
     * @return a non-{@code null} {@link DataSource} representing the
     * supplied {@link XADataSource}
     *
     * @exception NullPointerException if {@code xaDataSource} is
     * {@code null}
     */
    private DataSource convert(final XADataSource xaDataSource, final boolean jta, final String dataSourceName)
        throws SQLException {
        Objects.requireNonNull(xaDataSource);
        final DataSource returnValue =
            this.dataSourcesByName
            .computeIfAbsent(dataSourceName == null ? NULL_DATASOURCE_NAME : dataSourceName,
                             ignoredKey -> new XADataSourceWrappingDataSource(xaDataSource,
                                                                              this::getStatus,
                                                                              this::getTransaction));
        return returnValue;
    }

    private int getStatus() {
        try {
            return this.transactionManager.getStatus();
        } catch (final SystemException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Transaction getTransaction() {
        try {
            return this.transactionManager.getTransaction();
        } catch (final SystemException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Converts the supplied {@link DataSource} to a {@link
     * DataSource} and returns it.
     *
     * <p>In many cases this method simply returns the supplied {@link
     * DataSource} unmodified.</p>
     *
     * <p>In many other cases, a new {@link JtaDataSource} wrapping
     * the supplied {@link DataSource} and providing emulated JTA
     * semantics is returned instead.</p>
     *
     * <p>This method only returns {@code null} if the supplied {@link
     * DataSource} is {@code null}.</p>
     *
     * @param dataSource the {@link DataSource} to convert (or
     * return); may be {@code null}
     *
     * @param jta whether JTA semantics are in effect
     *
     * @param dataSourceName the name of the data source; may be (and
     * often is) {@code null}
     *
     * @return a {@link DataSource} representing the supplied {@link
     * DataSource}, or {@code null}
     */
    private DataSource convert(final DataSource dataSource, final boolean jta, final String dataSourceName)
        throws SQLException {
        final DataSource returnValue;
        if (!jta || dataSource == null || (dataSource instanceof JtaDataSource)) {
            returnValue = dataSource;
        } else if (dataSource instanceof XADataSource) {
            // Edge case
            returnValue = this.convert((XADataSource) dataSource, jta, dataSourceName);
        } else {
            returnValue =
                this.dataSourcesByName.computeIfAbsent(dataSourceName == null ? NULL_DATASOURCE_NAME : dataSourceName,
                                                       k -> new JtaDataSource(dataSource, this::getStatus));
            this.registerSynchronizationIfTransactionIsActive(returnValue);
        }
        return returnValue;
    }

    private void registerSynchronizationIfTransactionIsActive(final Object dataSource) {
        if (dataSource instanceof Synchronization && this.tsr.getTransactionStatus() == Status.STATUS_ACTIVE) {
            this.tsr.registerInterposedSynchronization((Synchronization) dataSource);
        }
    }

    /*
     * CDI Observer methods.
     */

    /**
     * Invoked by CDI when the {@linkplain TransactionScoped
     * transaction scope} becomes active, which definitionally happens
     * when a new JTA transaction begins.
     *
     * <p>This implementation ensures that any {@link DataSource} that
     * is also a {@link Synchronization} that is stored in the {@link
     * #dataSourcesByName} field is {@linkplain
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     * registered} with the new tranaction.</p>
     *
     * @param event ignored by this method
     *
     * @exception NullPointerException if {@code tsr} is {@code null}
     * for any reason
     *
     * @see #dataSourcesByName
     *
     * @see TransactionScoped
     *
     * @see
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     */
    private void whenTransactionStarts(@Observes @Initialized(TransactionScoped.class) final Object event) {
        this.dataSourcesByName.forEach((ignoredKey, dataSource) -> this.registerSynchronizationIfTransactionIsActive(dataSource));
    }

    /**
     * Invoked by CDI when the application scope is about to be
     * destroyed, signalling the end of the program.
     *
     * <p>This implementation calls {@link Map#clear()} on the {@link
     * #dataSourcesByName} field value.</p>
     *
     * @param event ignored by this method
     *
     * @see BeforeDestroyed
     */
    private void whenApplicationTerminates(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        this.dataSourcesByName.clear();
    }

}
