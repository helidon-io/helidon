/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import io.helidon.integrations.jta.jdbc.JtaDataSource2;
import io.helidon.integrations.jta.jdbc.SQLExceptionConverter;
import io.helidon.integrations.jta.jdbc.XADataSourceWrappingDataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

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

    private final SQLExceptionConverter sqlExceptionConverter;

    /**
     * A {@link ConcurrentMap} (usually a {@link ConcurrentHashMap})
     * that stores {@link JtaDataSource} instances under their names.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <h2>Design Notes</h2>
     *
     * <p>{@link DataSource} instances used by instances of this class
     * are normally CDI contextual references, so are often client
     * proxies.  Per the specification, a client proxy's {@link
     * Object#equals(Object)} and {@link Object#hashCode()} methods do
     * not behave in such a way that their underlying contextual
     * instances can be tested for equality.  When these {@link
     * DataSource}s are wrapped by {@link JtaDataSource2} instances,
     * we need to ensure that the same {@link JtaDataSource2} is
     * handed out each time a given data source name is supplied.
     * This {@link ConcurrentMap} provides those semantics.</p>
     *
     * @see JtaDataSource2
     *
     * @see <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#client_proxy_invocation">section
     * 5.4.1 of the CDI 2.0 specification</a>
     */
    private final ConcurrentMap<String, DataSource> dataSourcesByName;


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
        this.sqlExceptionConverter = null;
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
    JtaDataSourceProvider(Instance<Object> objects,
                          TransactionManager transactionManager,
                          TransactionSynchronizationRegistry tsr) {
        super();
        this.objects = Objects.requireNonNull(objects);
        this.transactionManager = Objects.requireNonNull(transactionManager);
        this.tsr = Objects.requireNonNull(tsr);
        this.dataSourcesByName = new ConcurrentHashMap<>();
        Instance<SQLExceptionConverter> i = objects.select(SQLExceptionConverter.class);
        this.sqlExceptionConverter = i.isUnsatisfied() ? null : i.get();
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
     * returned will be enrolled in JTA-compliant transactions
     *
     * @param useDefaultJta if {@code true}, and if the {@code jta}
     * parameter value is {@code true}, the supplied {@code
     * dataSourceName} may be ignored and a default {@link DataSource}
     * that will be enrolled in JTA-compliant transactions will be
     * returned if possible
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
    public DataSource getDataSource(boolean jta,
                                    boolean useDefaultJta,
                                    String dataSourceName) {
        DataSource returnValue;
        if (jta) {
            try {
                if (dataSourceName == null) {
                    if (useDefaultJta) {
                        Instance<XADataSource> xaDataSources = this.objects.select(XADataSource.class);
                        if (xaDataSources.isUnsatisfied()) {
                            returnValue = this.convert(this.objects.select(DataSource.class).get(), jta, null);
                        } else {
                            returnValue = this.convert(xaDataSources.get(), jta, null);
                        }
                    } else {
                        returnValue = null;
                    }
                } else {
                    Named named = NamedLiteral.of(dataSourceName);
                    Instance<XADataSource> xaDataSources = this.objects.select(XADataSource.class, named);
                    if (xaDataSources.isUnsatisfied()) {
                        returnValue = this.convert(this.objects.select(DataSource.class, named).get(), jta, dataSourceName);
                    } else {
                        returnValue = this.convert(xaDataSources.get(), jta, dataSourceName);
                    }
                }
            } catch (SQLException sqlException) {
                throw new IllegalStateException(sqlException.getMessage(), sqlException);
            }
        } else if (dataSourceName == null) {
            // There is no default to use.
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
    private DataSource convert(XADataSource xaDataSource, boolean jta, String dataSourceName)
        throws SQLException {
        Objects.requireNonNull(xaDataSource);
        return
            this.dataSourcesByName
            .computeIfAbsent(dataSourceName == null ? NULL_DATASOURCE_NAME : dataSourceName,
                             ignoredKey -> xaDataSource instanceof DataSource ds
                             ? ds
                             : new XADataSourceWrappingDataSource(xaDataSource,
                                                                  this::enlistResource));
    }

    private void enlistResource(XAResource resource) {
        try {
            Transaction transaction = this.transactionManager.getTransaction();
            if (transaction != null && transaction.getStatus() == Status.STATUS_ACTIVE) {
                transaction.enlistResource(resource);
            }
        } catch (RollbackException | SystemException e) {
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
     * <p>In many other cases, a new {@link JtaDataSource2} wrapping
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
    private DataSource convert(DataSource dataSource, boolean jta, String dataSourceName)
        throws SQLException {
        DataSource returnValue;
        if (!jta || dataSource == null || (dataSource instanceof JtaDataSource2)) {
            returnValue = dataSource;
        } else if (dataSource instanceof XADataSource) {
            // Edge case
            returnValue = this.convert((XADataSource) dataSource, jta, dataSourceName);
        } else {
            returnValue =
                this.dataSourcesByName.computeIfAbsent(dataSourceName == null ? NULL_DATASOURCE_NAME : dataSourceName,
                                                       k -> new JtaDataSource2(this.transactionManager,
                                                                               this.tsr,
                                                                               this.sqlExceptionConverter,
                                                                               dataSource));
        }
        return returnValue;
    }

}
