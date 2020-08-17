/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.eclipselink;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import javax.management.MBeanServer;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.eclipse.persistence.exceptions.ValidationException;
import org.eclipse.persistence.platform.server.JMXServerPlatformBase;
import org.eclipse.persistence.platform.server.ServerPlatformBase;
import org.eclipse.persistence.sessions.DatabaseSession;
import org.eclipse.persistence.sessions.DatasourceLogin;
import org.eclipse.persistence.sessions.JNDIConnector;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.transaction.JTATransactionController;

/**
 * A {@link JMXServerPlatformBase} that arranges things such that CDI,
 * not JNDI, will be used to acquire a {@link TransactionManager} and
 * {@link MBeanServer}.
 *
 * <p>Most users will not use this class directly, but will supply its
 * fully-qualified name as the value of the <a
 * href="https://www.eclipse.org/eclipselink/documentation/2.7/jpa/extensions/persistenceproperties_ref.htm#target-server">{@code
 * eclipselink.target-server} Eclipselink JPA extension property</a>
 * in a <a
 * href="https://javaee.github.io/tutorial/persistence-intro004.html#persistence-units">{@code
 * META-INF/persistence.xml} file</a>.</p>
 *
 * <p>For example:</p>
 *
 * <blockquote><pre>&lt;property name="eclipselink.target-server"
 *          value="io.helidon.integrations.cdi.eclipselink.CDISEPlatform"/&gt;</pre></blockquote>
 *
 * @see #getExternalTransactionControllerClass()
 */
public class CDISEPlatform extends JMXServerPlatformBase {


    /*
     * Constructors.
     */


    /**
     * Creates a {@link CDISEPlatform}.
     *
     * @param session the {@link DatabaseSession} this platform will
     * wrap; must not be {@code null}
     *
     * @see JMXServerPlatformBase#JMXServerPlatformBase(DatabaseSession)
     */
    public CDISEPlatform(final DatabaseSession session) {
        super(session);
    }


    /*
     * Instance methods.
     */


    /**
     * Sets the name of the platform.
     *
     * <p>The format of the platform name is subject to change without
     * notice.</p>
     *
     * @see #getServerNameAndVersion()
     */
    @Override
    protected void initializeServerNameAndVersion() {
        this.serverNameAndVersion = this.getClass().getSimpleName();
    }

    /**
     * Uses CDI to find a relevant {@link MBeanServer}, caches it, and
     * returns it.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>Overrides of this method may return {@code null}.</p>
     *
     * <p>If there is no such {@link MBeanServer} then the {@link
     * MBeanServer} found and cached by the {@linkplain
     * JMXServerPlatformBase#getMBeanServer() superclass
     * implementation of this method} is returned instead.</p>
     *
     * @return an {@link MBeanServer}, or {@code null}
     */
    @Override
    public MBeanServer getMBeanServer() {
        if (this.mBeanServer == null) {
            final CDI<Object> cdi = CDI.current();
            if (cdi != null) {
                Instance<MBeanServer> instance = cdi.select(MBeanServer.class, Eclipselink.Literal.INSTANCE);
                assert instance != null;
                if (instance.isUnsatisfied()) {
                    instance = cdi.select(MBeanServer.class);
                }
                if (!instance.isUnsatisfied()) {
                    final MBeanServer mBeanServer = instance.get();
                    assert mBeanServer != null;
                    this.mBeanServer = mBeanServer;
                }
            }
        }
        return super.getMBeanServer();
    }

    /**
     * Uses CDI to find a relevant {@link Executor} whose {@link
     * Executor#execute(Runnable)} method will be used to submit the
     * supplied {@link Runnable}.
     *
     * <p>If there is no such {@link Executor}, then the {@linkplain
     * JMXServerPlatformBase#launchContainerRunnable(Runnable)
     * superclass implementation of this method} is used instead.</p>
     *
     * @param runnable the {@link Runnable} to launch; should not be
     * {@code null}
     *
     * @see JMXServerPlatformBase#launchContainerRunnable(Runnable)
     */
    @Override
    public void launchContainerRunnable(final Runnable runnable) {
        if (runnable == null) {
            super.launchContainerRunnable(null);
        } else {
            final CDI<Object> cdi = CDI.current();
            if (cdi == null) {
                super.launchContainerRunnable(runnable);
            } else {
                Instance<Executor> executorInstance = cdi.select(Executor.class, Eclipselink.Literal.INSTANCE);
                assert executorInstance != null;
                if (executorInstance.isUnsatisfied()) {
                    executorInstance = cdi.select(Executor.class);
                }
                assert executorInstance != null;
                final Executor executor;
                if (executorInstance.isUnsatisfied()) {
                    executor = null;
                } else {
                    executor = executorInstance.get();
                }
                if (executor != null) {
                    executor.execute(runnable);
                } else {
                    super.launchContainerRunnable(runnable);
                }
            }
        }
    }

    /**
     * Overrides the {@link
     * ServerPlatformBase#initializeExternalTransactionController()}
     * method to {@linkplain #disableJTA() disable JTA} if there is no
     * {@link TransactionManager} bean present in CDI before invoking
     * the {@linkplain
     * ServerPlatformBase#initializeExternalTransactionController()
     * superclass implementation}.
     *
     * <p>This method also acquires a {@link DataSource} from
     * {@linkplain CDI CDI} proactively, and {@linkplain
     * JNDIConnector#setDataSource(DataSource) installs it} to
     * pre&euml;mpt any JNDI operations.</p>
     *
     * @exception ValidationException if a {@link DataSource} could
     * not be acquired
     *
     * @see ServerPlatformBase#initializeExternalTransactionController()
     *
     * @see Session#getDatasourceLogin()
     *
     * @see DatasourceLogin#getConnector()
     *
     * @see JNDIConnector
     *
     * @see JNDIConnector#getName()
     *
     * @see JNDIConnector#setDataSource(DataSource)
     */
    @Override
    public void initializeExternalTransactionController() {
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        if (cdi.select(TransactionManager.class).isUnsatisfied()) {
            this.disableJTA();
        }
        super.initializeExternalTransactionController();

        // See https://github.com/oracle/helidon/issues/949.  This is
        // the only spot where we can actually change the Connector
        // that is used by EclipseLink to look up a data source during
        // JPA "SE mode" persistence unit acquisition such that it
        // doesn't get overwritten by other EclipseLink internals.
        final Session session = this.getDatabaseSession();
        if (session != null) {
            final Object login = session.getDatasourceLogin();
            if (login instanceof DatasourceLogin) {
                final Object connector = ((DatasourceLogin) login).getConnector();
                if (connector instanceof JNDIConnector) {
                    final JNDIConnector jndiConnector = (JNDIConnector) connector;
                    final String dataSourceName = jndiConnector.getName();
                    if (dataSourceName != null) {
                        try {
                            jndiConnector.setDataSource(cdi.select(DataSource.class,
                                                                   NamedLiteral.of(dataSourceName)).get());
                        } catch (final InjectionException injectionExceptionOfAnyKind) {
                            throw ValidationException.cannotAcquireDataSource(dataSourceName, injectionExceptionOfAnyKind);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns a non-{@code null} {@link Class} that extends {@link
     * org.eclipse.persistence.transaction.AbstractTransactionController},
     * namely {@link TransactionController}.
     *
     * @return a non-{@code null} {@link Class} that extends {@link
     * org.eclipse.persistence.transaction.AbstractTransactionController}
     *
     * @see
     * org.eclipse.persistence.transaction.AbstractTransactionController
     *
     * @see TransactionController
     */
    @Override
    public Class<?> getExternalTransactionControllerClass() {
        if (this.externalTransactionControllerClass == null) {
            this.externalTransactionControllerClass = TransactionController.class;
        }
        return this.externalTransactionControllerClass;
    }

    /**
     * Returns {@link JNDIConnector#UNDEFINED_LOOKUP} when invoked.
     *
     * @return {@link JNDIConnector#UNDEFINED_LOOKUP}
     */
    @Override
    public final int getJNDIConnectorLookupType() {
        return JNDIConnector.UNDEFINED_LOOKUP;
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A {@link JTATransactionController} whose {@link
     * #acquireTransactionManager()} method uses CDI, not JNDI, to
     * return a {@link TransactionManager} instance.
     *
     * @see #acquireTransactionManager()
     *
     * @see JTATransactionController
     *
     * @see CDISEPlatform#getExternalTransactionControllerClass()
     */
    public static class TransactionController extends JTATransactionController {


        /*
         * Constructors.
         */


        /**
         * Creates a new {@link TransactionController}.
         */
        public TransactionController() {
            super();
        }


        /*
         * Instance methods.
         */


        /**
         * Returns a non-{@code null} {@link TransactionManager}.
         *
         * <p>This method never returns {@code null}.</p>
         *
         * @return a non-{@code null} {@link TransactionManager}
         *
         * @exception NullPointerException if in exceedingly rare
         * specification-violating cases the return value of {@link
         * CDI#current()} is {@code null}, or if the {@link
         * Instance#get()} method returns {@code null}
         *
         * @exception RuntimeException if the {@link Instance#get()}
         * method encounters an error providing a {@link
         * TransactionManager}
         *
         * @see JTATransactionController#acquireTransactionManager()
         */
        @Override
        protected TransactionManager acquireTransactionManager() {
            return Objects.requireNonNull(CDI.current().select(TransactionManager.class).get());
        }

    }


    /**
     * A {@link Qualifier} used to designate various things as being
     * related to <a href="https://www.eclipse.org/eclipselink/"
     * target="_parent">Eclipselink</a> in some way.
     *
     * <p>The typical end user will apply this annotation to an
     * implementation of {@link Executor} if she wants that particular
     * {@link Executor} used by the {@link
     * CDISEPlatform#launchContainerRunnable(Runnable)} method.</p>
     *
     * <p>The {@link Eclipselink} qualifier may also be used to
     * annotate an implementation of {@link MBeanServer} for use by
     * the {@link CDISEPlatform#getMBeanServer()} method.</p>
     *
     * @see CDISEPlatform#launchContainerRunnable(Runnable)
     *
     * @see CDISEPlatform#getMBeanServer()
     */
    @Documented
    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.TYPE })
    public @interface Eclipselink {

        /**
         * An {@link AnnotationLiteral} that implements {@link
         * Eclipselink}.
         */
        class Literal extends AnnotationLiteral<Eclipselink> implements Eclipselink {

            /**
             * The single instance of the {@link Literal} class.
             */
            public static final Eclipselink INSTANCE = new Literal();

            /**
             * The version of this class for Java serialization
             * purposes.
             */
            private static final long serialVersionUID = 1L;

        }

    }

}
