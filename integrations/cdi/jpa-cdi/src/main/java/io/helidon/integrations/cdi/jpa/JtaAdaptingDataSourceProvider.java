/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import io.helidon.integrations.jta.jdbc.ExceptionConverter;
import io.helidon.integrations.jta.jdbc.JtaAdaptingDataSource;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

@Singleton
final class JtaAdaptingDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Static fields.
     */


    /**
     * A token to use as a key in the {@link #jtaDataSourcesByName} field value for a data source name when the real
     * data source name is {@code null}.
     *
     * <p>Real data source names can be {@code null} and the empty string ({@code ""}), so a different value is used
     * here.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final String NULL_DATASOURCE_NAME = "\u0000";


    /*
     * Instance fields.
     */


    private final ConcurrentMap<String, JtaAdaptingDataSource> jtaDataSourcesByName;

    /**
     * An {@link Instance} providing access to CDI contextual references.
     *
     * <p>This field may be {@code null} if the {@linkplain #JtaAdaptingDataSourceProvider() deprecated zero-argument
     * constructor of this class} is used.</p>
     */
    private final Instance<Object> objects;

    /**
     * The {@link TransactionManager} for the system.
     *
     * <p>This field may be {@code null} if the {@linkplain #JtaAdaptingDataSourceProvider() deprecated zero-argument
     * constructor of this class} is used.</p>
     */
    private final TransactionManager transactionManager;

    private final TransactionSynchronizationRegistry tsr;

    private final boolean interposedSynchronizations;

    private final boolean immediateEnlistment;

    private final ExceptionConverter exceptionConverter;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaAdaptingDataSourceProvider}.
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
    JtaAdaptingDataSourceProvider(Instance<Object> objects,
                                  TransactionManager transactionManager,
                                  TransactionSynchronizationRegistry tsr) {
        super();
        this.jtaDataSourcesByName = new ConcurrentHashMap<>();
        this.objects = Objects.requireNonNull(objects, "objects");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.tsr = Objects.requireNonNull(tsr, "tsr");
        this.interposedSynchronizations =
            Boolean.parseBoolean(System.getProperty("helidon.jta.interposedSynchronizations", "true"));
        this.immediateEnlistment =
            Boolean.parseBoolean(System.getProperty("helidon.jta.immediateEnlistment", "false"));
        Instance<ExceptionConverter> i = objects.select(ExceptionConverter.class);
        this.exceptionConverter = i.isUnsatisfied() ? null : i.get();
    }


    /*
     * Instance methods.
     */


    /**
     * Supplies a {@link DataSource}.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param jta if {@code true}, the {@link DataSource} that is returned will be enrolled in JTA transactions
     *
     * @param useDefaultJta if {@code true}, and if the {@code jta} parameter value is {@code true}, the supplied {@code
     * dataSourceName} may be ignored and a default {@link DataSource} that will be enrolled in JTA transactions will be
     * returned if possible
     *
     * @param dataSourceName the name of the {@link DataSource} to return; may be {@code null}; ignored if both {@code
     * jta} and {@code useDefaultJta} are {@code true}
     *
     * @return an appropriate {@link DataSource}, or {@code null} in edge cases
     *
     * @see PersistenceUnitInfoBean#getJtaDataSource()
     *
     * @see PersistenceUnitInfoBean#getNonJtaDataSource()
     */
    @Override // PersistenceUnitInfoBean.DataSourceProvider
    public DataSource getDataSource(boolean jta, boolean useDefaultJta, String dataSourceName) {
        if (jta) {
            if (dataSourceName == null) {
                return useDefaultJta ? this.getDefaultJtaDataSource() : null;
            }
            return this.getNamedJtaDataSource(dataSourceName);
        }
        return dataSourceName == null ? null : this.getNamedNonJtaDataSource(dataSourceName);
    }

    private JtaAdaptingDataSource getDefaultJtaDataSource() {
        return
            jtaDataSourcesByName.computeIfAbsent(NULL_DATASOURCE_NAME,
                                                 n -> new JtaAdaptingDataSource(this.transactionManager::getTransaction,
                                                                                this.tsr,
                                                                                this.interposedSynchronizations,
                                                                                this.exceptionConverter,
                                                                                this.objects.select(DataSource.class).get(),
                                                                                this.immediateEnlistment));
    }

    private JtaAdaptingDataSource getNamedJtaDataSource(String name) {
        return
            jtaDataSourcesByName.computeIfAbsent(name,
                                                 n -> new JtaAdaptingDataSource(this.transactionManager::getTransaction,
                                                                                this.tsr,
                                                                                this.interposedSynchronizations,
                                                                                this.exceptionConverter,
                                                                                this.objects.select(DataSource.class,
                                                                                                    NamedLiteral.of(n)).get(),
                                                                                this.immediateEnlistment));
    }

    private DataSource getNamedNonJtaDataSource(String name) {
        return this.objects.select(DataSource.class, NamedLiteral.of(name)).get();
    }

    @PreDestroy
    private void clear() {
        this.jtaDataSourcesByName.clear();
    }

}
