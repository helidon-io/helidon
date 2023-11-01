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
package io.helidon.integrations.cdi.hibernate;

import java.lang.System.Logger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.DialectFactoryImpl;
import org.hibernate.engine.jdbc.dialect.spi.BasicSQLExceptionConverter;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfoSource;
import org.hibernate.service.spi.ServiceContributor;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link DialectFactory} implementation (and a {@link ServiceContributor}, and a {@link StandardServiceInitiator
 * StandardServiceInitiator&lt;DialectFactory&gt;}) that introspects {@link DatabaseMetaData} from a configured {@link
 * DataSource}.
 *
 * <p>Hibernate is <a
 * href="https://docs.jboss.org/hibernate/orm/current/integrationguide/html_single/Hibernate_Integration_Guide.html#services-overriding"
 * target="_top">guaranteed</a> to perform each of the following invocations, once, ever, in order:</p>
 *
 * <ol>
 *
 * <li>(The {@linkplain #DataSourceBackedDialectFactory() zero-argument constructor})</li>
 *
 * <li>The {@link #contribute(StandardServiceRegistryBuilder)} method</li>
 *
 * <li>The {@link #initiateService(Map, ServiceRegistryImplementor)} method (if applicable)</li>
 *
 * </ol>
 *
 * <p>Then, at application runtime, after the sole instance of this class has been installed following the protocol
 * above, Hibernate will call the {@link #buildDialect(Map, DialectResolutionInfoSource)} method as appropriate.</p>
 *
 * @see #contribute(StandardServiceRegistryBuilder)
 *
 * @see #initiateService(Map, ServiceRegistryImplementor)
 *
 * @see #buildDialect(Map, DialectResolutionInfoSource)
 *
 * @see <a
 * href="https://docs.jboss.org/hibernate/orm/current/integrationguide/html_single/Hibernate_Integration_Guide.html#services-overriding"
 * target="_top">Custom {@code Service} Implementations (overriding) in the Hibernate Integration Guide</a>
 */
public final class DataSourceBackedDialectFactory
    implements DialectFactory, ServiceContributor, StandardServiceInitiator<DialectFactory> {


    /*
     * Static fields.
     */


    /**
     * A {@link Logger} for instances of this class.
     *
     * @see Logger
     */
    private static final Logger LOGGER = System.getLogger(DataSourceBackedDialectFactory.class.getName());


    /**
     * A version identifier for {@linkplain java.io.Serializable serialization} purposes.
     */
    private static final long serialVersionUID = 1L;


    /*
     * Instance fields.
     */


    /**
     * An instance of Hibernate's standard {@link DialectFactory} implementation ({@link DialectFactoryImpl}) to which
     * all "real work" is delegated.
     *
     * <p>This field is set once, ever, to a non-{@code null} value, by the {@link #initiateService(Map,
     * ServiceRegistryImplementor)} method, which Hibernate calls as part of its bootstrap protocol.</p>
     *
     * @see #initiateService(Map, ServiceRegistryImplementor)
     *
     * @see StandardServiceInitiator#initiateService(Map, ServiceRegistryImplementor)
     *
     * @see DialectFactoryImpl#buildDialect(Map, DialectResolutionInfoSource)
     */
    private DialectFactoryImpl dfi;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link DataSourceBackedDialectFactory}.
     *
     * @deprecated For use by {@link java.util.ServiceLoader} instances only.
     */
    @Deprecated
    public DataSourceBackedDialectFactory() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Retrieves the {@link DataSource} stored in the supplied {@link Map} under the {@link
     * JdbcSettings#JAKARTA_JTA_DATASOURCE} key, and uses it to ultimately acquire a {@link DatabaseMetaData} instance
     * to adapt into a {@link DialectResolutionInfo} instance, such that a {@link DialectResolutionInfoSource} can be
     * constructed, in the case where the supplied {@code dialectResolutionInfoSource} is {@code null}, and passes the
     * resulting {@link DialectResolutionInfoSource} to the {@linkplain DialectFactoryImpl#buildDialect(Map,
     * DialectResolutionInfoSource) standard <code>DialectFactory</code> that Hibernate uses by default}, such that
     * Hibernate can determine what {@link Dialect} to use when database connectivity is established via a {@code
     * META-INF/persistence.xml}'s {@code jta-data-source} element.
     *
     * @param settings a {@link Map} containing the settings returned by an invocation of the {@link
     * StandardServiceRegistryBuilder#getSettings()} method; must not be {@code null}
     *
     * @param dialectResolutionInfoSource a {@link DialectResolutionInfoSource} supplied by Hibernate; may be, and often
     * is, {@code null}
     *
     * @return a {@link Dialect}; never {@code null}
     *
     * @exception NullPointerException if {@code settings} is {@code null}
     *
     * @exception org.hibernate.HibernateException if a {@link Dialect} could not be constructed
     *
     * @see DialectFactoryImpl#buildDialect(Map, DialectResolutionInfoSource)
     *
     * @see JdbcSettings#JAKARTA_JTA_DATASOURCE
     *
     * @see DatabaseMetaDataDialectResolutionInfoAdapter
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    @Override // DialectFactory
    public Dialect buildDialect(Map<String, Object> settings, DialectResolutionInfoSource dialectResolutionInfoSource) {
        if (dialectResolutionInfoSource == null) {
            DataSource ds = getDataSource(settings);
            if (ds != null) {
                DatabaseMetaData dmd;
                try (Connection c = ds.getConnection()) {
                    dmd = c.getMetaData();
                } catch (SQLException e) {
                    throw BasicSQLExceptionConverter.INSTANCE.convert(e);
                }
                DialectResolutionInfo dri = new DatabaseMetaDataDialectResolutionInfoAdapter(dmd);
                dialectResolutionInfoSource = () -> dri;
            }
        }
        // No null check needed for this.dfi because it is guaranteed to have been set by the initiateService(Map,
        // ServiceRegistryImplementor) method (q.v.).
        return this.dfi.buildDialect(settings, dialectResolutionInfoSource);
    }

    /**
     * {@linkplain StandardServiceRegistryBuilder#addInitiator(StandardServiceInitiator) Contributes} this {@link
     * DataSourceBackedDialectFactory} as a {@link StandardServiceInitiator
     * StandardServiceInitiator&lt;DialectFactory&gt;} if and only if certain requirements are met.
     *
     * <p>This method will call {@link StandardServiceRegistryBuilder#addInitiator(StandardServiceInitiator)
     * standardServiceRegistryBuilder.addInitiator(this)} if and only if all of the following preconditions hold:</p>
     *
     * <ul>
     *
     * <li>{@code settings.get(}{@link JdbcSettings#DIALECT DIALECT}{@code )} returns an object that is either {@code
     * null} or a {@linkplain String#isBlank() blank} {@link String}</li>
     *
     * <li>{@code settings.get(}{@link JdbcSettings#JAKARTA_JTA_DATASOURCE JAKARTA_JTA_DATASOURCE}{@code )} returns a
     * non-{@code null} {@link DataSource}</li>
     *
     * </ul>
     *
     * <p>If any other state of affairs holds, this method takes no action.</p>
     *
     * @param standardServiceRegistryBuilder a {@link StandardServiceRegistryBuilder} whose {@link
     * StandardServiceRegistryBuilder#addInitiator(StandardServiceInitiator)} may be called with {@code this} as its
     * sole argument; must not be {@code null}
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    @Override // ServiceContributor
    public void contribute(StandardServiceRegistryBuilder standardServiceRegistryBuilder) {
        Map<?, ?> settings = standardServiceRegistryBuilder.getSettings();
        Object dialect = settings.get(JdbcSettings.DIALECT);
        if ((dialect == null || dialect instanceof String dialectString && dialectString.isBlank())
            && getDataSource(settings) != null) {
            standardServiceRegistryBuilder.addInitiator(this);
        }
    }

    /**
     * Returns {@link Class Class&lt;DialectFactory&gt;} when invoked, thus describing the type of service the {@link
     * #initiateService(Map, ServiceRegistryImplementor)} will initiate.
     *
     * @return {@link Class Class&lt;DialectFactory&gt;} when invoked
     *
     * @see StandardServiceInitiator#getServiceInitiated()
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    @Override // StandardServiceInitiator<DialectFactory>
    public Class<DialectFactory> getServiceInitiated() {
        return DialectFactory.class;
    }

    /**
     * Returns {@code this} when invoked.
     *
     * @param settings ignored
     *
     * @param serviceRegistry a {@link ServiceRegistryImplementor} supplied by Hibernate
     *
     * @return {@code this} when invoked
     *
     * @deprecated For Hibernate use only.
     */
    @Deprecated
    @Override // StandardServiceInitiator<DialectFactory>
    public DataSourceBackedDialectFactory initiateService(Map<String, Object> settings,
                                                          ServiceRegistryImplementor serviceRegistry) {
        // This method is kind of like a @PostConstruct method. The initiateService(Map, ServiceRegistryImplementor)
        // method is called only once, ever, by Hibernate, in a guaranteed bootstrap protocol. Consequently this is the
        // only place where the dfi instance field will ever be set. No null check for this.dfi is therefore needed in
        // the buildDialect(Map, DialectResolutionInfoSource) method.
        //
        // Additionally, ServiceInitiators are not required to be thread-safe, so the dfi instance variable is not, and
        // need not be, volatile.
        this.dfi = new DialectFactoryImpl();
        this.dfi.injectServices(serviceRegistry);
        return this;
    }


    /*
     * Static methods.
     */


    /**
     * Retrieves the value indexed under the {@link JdbcSettings#JAKARTA_JTA_DATASOURCE} key, and, if and only if it is
     * a {@link DataSource}, returns it.
     *
     * @param settings a {@link Map} containing the settings returned by an invocation of the {@link
     * StandardServiceRegistryBuilder#getSettings()} method; must not be {@code null}
     *
     * @return a {@link DataSource}, or {@code null}
     *
     * @exception NullPointerException if {@code settings} is {@code null}
     */
    private static DataSource getDataSource(Map<?, ?> settings) {
        Object ds = settings.get(JdbcSettings.JAKARTA_JTA_DATASOURCE);
        if (!(ds instanceof DataSource)) {
            if (LOGGER.isLoggable(WARNING)) {
                LOGGER.log(WARNING, "No DataSource found under key " + JdbcSettings.JAKARTA_JTA_DATASOURCE);
            }
            return null;
        }
        return (DataSource) ds;
    }

}
