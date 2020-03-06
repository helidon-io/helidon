/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.TransactionSynchronizationRegistry;

@ApplicationScoped
class JtaDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Static fields.
     */


    /**
     * A {@link ThreadLocal} holding a {@link Map} of {@link
     * DataSource}s, indexed by their (possibly {@code null}) name or
     * other application-wide identifier, that may also be {@linkplain
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     * registered as <code>Synchronization</code> implementations} at
     * the start of a JTA transaction.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The {@link Map} {@linkplain ThreadLocal#get() contained by
     * this <code>ThreadLocal</code>} is not {@code null} until the
     * {@linkplain ApplicationScoped application scope} is {@linkplain
     * BeforeDestroyed destroyed}.</p>
     *
     * <p>The {@link Map} {@linkplain ThreadLocal#get() contained by
     * this <code>ThreadLocal</code>} may be {@linkplain Map#isEmpty()
     * empty} at any point.</p>
     *
     * @see #whenTransactionStarts(Object,
     * TransactionSynchronizationRegistry)
     *
     * @see #whenApplicationTerminates(Object)
     */
    private static final ThreadLocal<? extends Map<Object, DataSource>> THREAD_LOCAL_DATASOURCES_BY_NAME =
        ThreadLocal.withInitial(() -> new HashMap<>());


    /*
     * Instance fields.
     */


    private final Instance<Object> objects;

    private final TransactionManager transactionManager;


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
     * #JtaDataSourceProvider(Instance, TransactionManager)}
     * constructor instead.
     */
    @Deprecated
    JtaDataSourceProvider() {
        super();
        this.objects = null;
        this.transactionManager = null;
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
     * @exception NullPointerException if either {@code objects} or
     * {@code transactionManager} is {@code null}
     */
    @Inject
    JtaDataSourceProvider(final Instance<Object> objects,
                          final TransactionManager transactionManager) {
        super();
        this.objects = Objects.requireNonNull(objects);
        this.transactionManager = Objects.requireNonNull(transactionManager);
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

    private DataSource convert(final XADataSource xaDataSource, final boolean jta, final String dataSourceName)
        throws SQLException {
        Objects.requireNonNull(xaDataSource);
        final Map<Object, DataSource> threadLocalDataSourcesByName = THREAD_LOCAL_DATASOURCES_BY_NAME.get();
        assert threadLocalDataSourcesByName != null;
        DataSource dataSource = threadLocalDataSourcesByName.get(dataSourceName);
        if (dataSource == null) {
            dataSource = new XADataSourceWrappingDataSource(xaDataSource, dataSourceName, this.transactionManager);
            threadLocalDataSourcesByName.put(dataSourceName, dataSource);
        }
        return dataSource;
    }

    private DataSource convert(final DataSource dataSource, final boolean jta, final String dataSourceName)
        throws SQLException {
        final DataSource returnValue;
        if (!jta || dataSource == null || (dataSource instanceof JtaDataSource)) {
            returnValue = dataSource;
        } else if (dataSource instanceof XADataSource) {
            // Edge case
            returnValue = this.convert((XADataSource) dataSource, jta, dataSourceName);
        } else {
            final Map<Object, DataSource> threadLocalDataSourcesByName = THREAD_LOCAL_DATASOURCES_BY_NAME.get();
            assert threadLocalDataSourcesByName != null;
            DataSource jtaDataSource = threadLocalDataSourcesByName.get(dataSourceName);
            if (jtaDataSource == null) {
                jtaDataSource = new JtaDataSource(dataSource, dataSourceName, this.transactionManager);
                threadLocalDataSourcesByName.put(dataSourceName, jtaDataSource);
            }
            returnValue = jtaDataSource;
        }
        return returnValue;
    }

    private static void whenApplicationTerminates(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        THREAD_LOCAL_DATASOURCES_BY_NAME.get().clear();
        THREAD_LOCAL_DATASOURCES_BY_NAME.remove();
    }

    private static void whenTransactionStarts(@Observes @Initialized(TransactionScoped.class) final Object event,
                                              final TransactionSynchronizationRegistry tsr) {
        assert tsr != null;
        assert tsr.getTransactionStatus() == Status.STATUS_ACTIVE;
        final Map<?, ?> threadLocalSynchronizations = THREAD_LOCAL_DATASOURCES_BY_NAME.get();
        assert threadLocalSynchronizations != null;
        for (final Object synchronization : threadLocalSynchronizations.values()) {
            if (synchronization instanceof Synchronization) {
                tsr.registerInterposedSynchronization((Synchronization) synchronization);
            }
        }
    }

}
