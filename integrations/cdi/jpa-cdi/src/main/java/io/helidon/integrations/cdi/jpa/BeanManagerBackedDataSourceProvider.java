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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.sql.DataSource;

/**
 * A {@link PersistenceUnitInfoBean.DataSourceProvider} implementation
 * that uses a {@link BeanManager} to look up relevant {@link
 * DataSource}s.
 *
 * @see PersistenceUnitInfoBean.DataSourceProvider
 */
@ApplicationScoped
class BeanManagerBackedDataSourceProvider implements PersistenceUnitInfoBean.DataSourceProvider {


    /*
     * Instance fields.
     */


    /**
     * The {@link BeanManager} to use to look up relevant {@link
     * DataSource}s.
     *
     * <p>This field may be {@code null} in which case the {@link
     * #getDataSource(boolean, boolean, String)} method will throw an
     * {@link IllegalStateException}.</p>
     *
     * @see #BeanManagerBackedDataSourceProvider(BeanManager)
     */
    private final BeanManager beanManager;


    /*
     * Constructors.
     */


    /**
     * Creates a new <strong>nonfunctional</strong> {@link
     * BeanManagerBackedDataSourceProvider}.
     *
     * <p>This constructor exists only to conform to <a
     * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#unproxyable">section
     * 3.15 of the CDI specification</a> and for no other purpose.</p>
     *
     * @deprecated Please use the {@link
     * #BeanManagerBackedDataSourceProvider(BeanManager)} constructor
     * instead.
     */
    @Deprecated
    BeanManagerBackedDataSourceProvider() {
        this(null);
    }

    /**
     * Creates a new {@link BeanManagerBackedDataSourceProvider}.
     *
     * @param beanManager the {@link BeanManager} to use; may be
     * {@code null}, but shouldn't be, and if so the {@link
     * #getDataSource(boolean, boolean, String)} method will throw an
     * {@link IllegalStateException}
     */
    @Inject
    BeanManagerBackedDataSourceProvider(final BeanManager beanManager) {
        super();
        this.beanManager = beanManager;
    }


    /*
     * Instance methods.
     */


    /**
     * Supplies a {@link DataSource} according to rules defined by the
     * JPA specification and portions of the Java EE specification.
     *
     * <p>Implementations of this method are permitted to return
     * {@code null}.</p>
     *
     * @param jta if {@code true}, the {@link DataSource} that is
     * returned may be enrolled in JTA-compliant transactions; this
     * implementation ignores this parameter
     *
     * @param useDefaultJta if {@code true}, and if the {@code jta}
     * parameter value is {@code true}, the supplied {@code
     * dataSourceName} may be ignored and a default {@link DataSource}
     * eligible for enrolling in JTA-compliant transactions will be
     * returned if possible
     *
     * @param dataSourceName the name of the {@link DataSource} to
     * return; may be {@code null}
     *
     * @return an appropriate {@link DataSource}, or {@code null}
     *
     * @see PersistenceUnitInfoBean#getJtaDataSource()
     *
     * @see PersistenceUnitInfoBean#getNonJtaDataSource()
     *
     * @exception IllegalStateException if this {@link
     * BeanManagerBackedDataSourceProvider} was created with a {@code
     * null} {@link BeanManager}
     */
    @Override
    public DataSource getDataSource(final boolean jta,
                                    final boolean useDefaultJta,
                                    final String dataSourceName) {
        if (this.beanManager == null) {
            throw new IllegalStateException("beanManager == null");
        }
        final Bean<?> bean;
        if (dataSourceName == null) {
            if (useDefaultJta) {
                bean = this.beanManager.resolve(this.beanManager.getBeans(DataSource.class));
            } else {
                bean = null;
            }
        } else {
            bean = this.beanManager.resolve(this.beanManager.getBeans(DataSource.class, NamedLiteral.of(dataSourceName)));
        }
        final DataSource returnValue;
        if (bean == null) {
            returnValue = null;
        } else {
            returnValue =
                (DataSource) this.beanManager.getReference(bean,
                                                           DataSource.class,
                                                           this.beanManager.createCreationalContext(bean));
        }
        return returnValue;
    }
}
