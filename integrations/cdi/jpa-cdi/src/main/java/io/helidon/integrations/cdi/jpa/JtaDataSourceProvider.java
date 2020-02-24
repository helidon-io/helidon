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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
     * A {@link ThreadLocal} {@link Set} of {@link Synchronization}s
     * that will be {@linkplain
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     * registered} at the start of a JTA transaction.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The {@link Set} {@linkplain ThreadLocal#get() contained by
     * this <code>ThreadLocal</code>} is not {@code null} until the
     * application scope is destroyed.</p>
     *
     * <p>The {@link Set} {@linkplain ThreadLocal#get() contained by
     * this <code>ThreadLocal</code>} may be empty at any point.</p>
     */
    private static final ThreadLocal<? extends Set<Synchronization>> SYNCHRONIZATIONS_TO_REGISTER =
        ThreadLocal.withInitial(() -> new HashSet<>());


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
     * @return an appropriate {@link DataSource}, or {@code null}
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
                            returnValue = this.convert(this.objects.select(DataSource.class).get(), jta);
                        } else {
                            returnValue = this.convert(xaDataSources.get(), jta);
                        }
                    } else {
                        returnValue = null;
                    }
                } else {
                    final Named named = NamedLiteral.of(dataSourceName);
                    final Instance<XADataSource> xaDataSources = this.objects.select(XADataSource.class, named);
                    if (xaDataSources.isUnsatisfied()) {
                        returnValue = this.convert(this.objects.select(DataSource.class, named).get(), jta);
                    } else {
                        returnValue = this.convert(xaDataSources.get(), jta);
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

    private DataSource convert(final XADataSource xaDataSource, final boolean jta)
        throws SQLException {
        Objects.requireNonNull(xaDataSource);
        return new XADataSourceWrappingDataSource(xaDataSource, this.transactionManager);
    }

    private DataSource convert(final DataSource dataSource, final boolean jta)
        throws SQLException {
        final DataSource returnValue;
        if (!jta || dataSource == null || (dataSource instanceof JtaDataSource)) {
            returnValue = dataSource;
        } else if (dataSource instanceof XADataSource) {
            // Edge case
            returnValue = this.convert((XADataSource) dataSource, jta);
        } else {
            final JtaDataSource jtaDataSource = new JtaDataSource(dataSource, this.transactionManager);
            SYNCHRONIZATIONS_TO_REGISTER.get().add(jtaDataSource);
            returnValue = jtaDataSource;
        }
        return returnValue;
    }

    private static void whenApplicationTerminates(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        SYNCHRONIZATIONS_TO_REGISTER.get().clear();
        SYNCHRONIZATIONS_TO_REGISTER.remove();
    }

    private static void whenTransactionStarts(@Observes @Initialized(TransactionScoped.class) final Object event,
                                              final TransactionSynchronizationRegistry tsr) {
        if (tsr != null) {
            assert tsr.getTransactionStatus() == Status.STATUS_ACTIVE;
            for (final Synchronization synchronization : SYNCHRONIZATIONS_TO_REGISTER.get()) {
                if (synchronization != null) {
                    tsr.registerInterposedSynchronization(synchronization);
                }
            }
        }
    }

}
