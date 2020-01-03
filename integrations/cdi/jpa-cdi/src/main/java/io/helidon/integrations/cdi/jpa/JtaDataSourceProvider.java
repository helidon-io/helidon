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
package io.helidon.integrations.cdi.jpa;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.inject.Inject;
import javax.sql.CommonDataSource;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.TransactionSynchronizationRegistry;

@ApplicationScoped
class JtaDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Static fields.
     */
    
    
    private static final Set<ConnectionPinningDataSource> notificationTargets = new CopyOnWriteArraySet<>();


    /*
     * Instance fields.
     */
    
    
    private final Instance<Object> instance;
    
    private final Instance<CommonDataSource> commonDataSources;


    /*
     * Constructors.
     */

    
    /**
     * <p>This constructor exists only to conform to <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#unproxyable">section
     * 3.15 of the CDI specification</a> and for no other purpose.</p>
     *
     * @deprecated Please use the {@link
     * #BeanManagerBackedDataSourceProvider(BeanManager)} constructor
     * instead.
     */
    @Deprecated
    JtaDataSourceProvider() {
        super();
        this.instance = null;
        this.commonDataSources = null;
    }

    @Inject
    JtaDataSourceProvider(final Instance<Object> instance) {
        super();
        this.instance = Objects.requireNonNull(instance);
        this.commonDataSources = Objects.requireNonNull(instance.select(CommonDataSource.class));
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
    public DataSource getDataSource(final boolean jta, final boolean useDefaultJta, final String dataSourceName) {
        final Instance<CommonDataSource> commonDataSources;
        if (dataSourceName == null) {
            if (useDefaultJta) {
                commonDataSources = this.commonDataSources;
            } else {
                commonDataSources = null;
            }
        } else {
            commonDataSources = this.commonDataSources.select(NamedLiteral.of(dataSourceName));
        }
        final ConnectionPinningDataSource returnValue;
        if (commonDataSources == null || commonDataSources.isUnsatisfied()) {
            returnValue = null;
        } else {
            XADataSource xaDataSource = null;
            ConnectionPoolDataSource connectionPoolDataSource = null;
            DataSource dataSource = null;
            for (final CommonDataSource commonDataSource : commonDataSources) {
                assert commonDataSource != null;
                if (commonDataSource instanceof XADataSource) {
                    if (xaDataSource != null) {
                        throw new AmbiguousResolutionException();
                    }
                    xaDataSource = (XADataSource) commonDataSource;
                } else if (commonDataSource instanceof ConnectionPoolDataSource) {
                    if (connectionPoolDataSource != null) {
                        throw new AmbiguousResolutionException();
                    }
                    connectionPoolDataSource = (ConnectionPoolDataSource) commonDataSource;
                } else if (commonDataSource instanceof DataSource) {
                    if (dataSource != null) {
                        throw new AmbiguousResolutionException();
                    }
                    dataSource = (DataSource) commonDataSource;
                } else {
                    throw new IllegalStateException("Unexpected commonDataSource: " + commonDataSource);
                }
            }
            try {
                if (xaDataSource == null) {
                    if (connectionPoolDataSource == null) {
                        if (dataSource == null) {
                            // Technically speaking this should be
                            // impossible given that we already called
                            // isUnsatisfied() above.
                            throw new UnsatisfiedResolutionException();
                        } else {
                            returnValue = this.convert(dataSource);
                        }
                    } else {
                        returnValue = this.convert(connectionPoolDataSource);
                    }
                } else {
                    returnValue = this.convert(xaDataSource);
                }
            } catch (final SQLException sqlException) {
                throw new IllegalStateException(sqlException.getMessage(), sqlException);
            }
        }
        if (returnValue != null) {
            notificationTargets.add(returnValue);
        }
        return returnValue;
    }

    private ConnectionPinningDataSource convert(final XADataSource xaDataSource) throws SQLException {
        Objects.requireNonNull(xaDataSource);
        final XAConnection xaConnection = xaDataSource.getXAConnection();
        assert xaConnection != null;
        final Connection connection = xaConnection.getConnection();
        assert connection != null;
        // 
        return null;
    }

    private ConnectionPinningDataSource convert(final ConnectionPoolDataSource connectionPoolDataSource) throws SQLException {
        Objects.requireNonNull(connectionPoolDataSource);
        return null;
    }
    
    private TransactionalConnectionPinningDataSource convert(final DataSource dataSource) throws SQLException {
        return new TransactionalConnectionPinningDataSource(dataSource::getConnection);
    }


    /*
     * Static methods.
     */


    private static void frob(@Observes
                             @Initialized(TransactionScoped.class)
                             final Object event,
                             final Transaction transaction,
                             final TransactionSynchronizationRegistry tsr)
        throws SystemException {
        assert transaction != null;
        assert transaction.getStatus() == Status.STATUS_ACTIVE;
        assert tsr != null;
        tsr.registerInterposedSynchronization(new Synchronization() {
                @Override
                public final void beforeCompletion() {

                }

                @Override
                public final void afterCompletion(final int status) {
                    RuntimeException problem = null;
                    for (final ConnectionPinningDataSource notificationTarget : notificationTargets) {
                        assert notificationTarget != null;
                        notificationTarget.setConnectionCloseable();
                        if (notificationTarget instanceof TransactionalConnectionPinningDataSource) {
                            final TransactionalConnectionPinningDataSource tds = (TransactionalConnectionPinningDataSource) notificationTarget;
                            try {
                                switch (status) {
                                case Status.STATUS_COMMITTED:
                                    tds.commit();
                                    break;
                                case Status.STATUS_ROLLEDBACK:
                                    tds.rollback();
                                    break;
                                default:
                                    throw new IllegalStateException(String.valueOf(status));
                                }
                            } catch (final SQLException sqlException) {
                                if (problem == null) {
                                    problem = new IllegalStateException(sqlException.getMessage(), sqlException);
                                } else {
                                    problem.addSuppressed(sqlException);
                                }
                            }
                        }
                    }
                    if (problem != null) {
                        throw problem;
                    }
                }
            });
    }

}
