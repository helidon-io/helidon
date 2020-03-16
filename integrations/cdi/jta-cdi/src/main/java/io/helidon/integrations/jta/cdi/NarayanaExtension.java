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
package io.helidon.integrations.jta.cdi;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Singleton;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.UserTransaction;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * A <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi">CDI 2.0
 * portable extension</a> that adapts the <a
 * href="https://narayana.io/">Narayana transaction engine</a> to a <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#part_2">CDI
 * 2.0 SE environment</a>.
 */
public final class NarayanaExtension implements Extension {

    /*
     * Static fields.
     */

    /**
     * The {@link Logger} for use by all instances of {@link
     * NarayanaExtension}.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(NarayanaExtension.class.getName(),
                                                          NarayanaExtension.class.getPackage().getName() + ".Messages");

    /**
     * The default {@link JTAEnvironmentBean} used throughout the
     * Narayana transaction engine as configured via the <a
     * href="https://github.com/jbosstm/narayana/blob/ff309b6d8239f18607de98a8e5a2aec08fb3e6c2/common/classes/com/arjuna/common/internal/util/propertyservice/BeanPopulator.java#L105-L175">{@code
     * BeanPopulator} mechanism</a>.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final JTAEnvironmentBean DEFAULT_JTA_ENVIRONMENT_BEAN =
        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);


    /*
     * Instance fields.
     */


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link NarayanaExtension}.
     */
    public NarayanaExtension() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Adds a synthetic bean that creates a {@link Transaction} in
     * {@linkplain TransactionScoped transaction scope}.
     *
     * @param event the {@link AfterBeanDiscovery} event fired by the
     * CDI container; may be {@code null} in which case no action will
     * be taken
     *
     * @param beanManager the {@link BeanManager} in effect; may be
     * {@code null} in which case no action will be taken
     */
    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
        final String cn = NarayanaExtension.class.getName();
        final String mn = "afterBeanDiscovery";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }

        if (event != null && beanManager != null) {

            // Weld registers a UserTransaction bean well before this
            // observer method fires.  OpenWebBeans does not.  We need
            // to handle both cases since this is not part of the CDI
            // specification.
            Collection<? extends Bean<?>> beans = beanManager.getBeans(UserTransaction.class);
            if (beans == null || beans.isEmpty()) {
                event.addBean()
                    .types(UserTransaction.class)
                    // OpenWebBeans does not add these qualifiers;
                    // Weld does automatically:
                    .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE)
                    // see
                    // e.g. https://docs.oracle.com/javaee/6/tutorial/doc/gmgli.html
                    // which reads in part: "Predefined beans are
                    // injected with **dependent scope** [emphasis
                    // mine] and the predefined default
                    // qualifier @Default."  This scope restriction is
                    // not specified in the CDI specification but
                    // seems reasonable and widely expected.
                    .scope(Dependent.class)
                    .createWith(cc -> com.arjuna.ats.jta.UserTransaction.userTransaction());
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, cn, mn, "addedUserTransactionBean");
                }
            }

            event.addBean()
                .id(Transaction.class.getName())
                .types(Transaction.class)
                .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE) // OpenWebBeans does not add these
                .scope(TransactionScoped.class)
                .createWith(cc -> {
                        try {
                            return CDI.current().select(TransactionManager.class).get().getTransaction();
                        } catch (final SystemException systemException) {
                            throw new CreationException(systemException.getMessage(), systemException);
                        }
                    });
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, cn, mn, "addedTransactionBean");
            }

            beans = beanManager.getBeans(JTAEnvironmentBean.class);
            if (beans == null || beans.isEmpty()) {
                event.addBean()
                    .addTransitiveTypeClosure(JTAEnvironmentBean.class)
                    // OpenWebBeans does not add these qualifiers;
                    // Weld does automatically:
                    .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE)
                    .scope(Singleton.class)
                    .createWith(cc -> DEFAULT_JTA_ENVIRONMENT_BEAN);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, cn, mn, "addedJtaEnvironmentBeanBean");
                }
            }

        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Observes the startup of the CDI container (by observing the
     * {@linkplain Initialized initialization} of the {@linkplain
     * ApplicationScoped application scope}) and reacts by {@linkplain
     * Event#fire(Object) firing an event} consisting of the {@link
     * JTAEnvironmentBean} singleton initialized by the Narayana
     * transaction engine and preconfigured through its <a
     * href="https://github.com/jbosstm/narayana/blob/ff309b6d8239f18607de98a8e5a2aec08fb3e6c2/common/classes/com/arjuna/common/internal/util/propertyservice/BeanPopulator.java#L105-L175">{@code
     * BeanPopulator} proprietary mechanism</a>.
     *
     * <p>This allows other portable extensions to further configure
     * the default {@link JTAEnvironmentBean} in whatever manner they
     * see fit.</p>
     *
     * @param event the event representing the {@linkplain
     * ApplicationScoped application scope} {@linkplain Initialized
     * initialization}; may be {@code null}; ignored
     *
     * @param broadcaster an {@link Event} capable of {@linkplain
     * Event#fire(Object) firing} a {@link JTAEnvironmentBean}
     *
     * @see JTAEnvironmentBean
     */
    private static void onStartup(@Observes
                                  @Initialized(ApplicationScoped.class)
                                  @Priority(LIBRARY_BEFORE)
                                  final Object event,
                                  final Event<JTAEnvironmentBean> broadcaster) {
        final String cn = NarayanaExtension.class.getName();
        final String mn = "onStartup";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, broadcaster});
        }
        if (broadcaster != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, cn, mn, "firingJtaEnvironmentBean", DEFAULT_JTA_ENVIRONMENT_BEAN);
            }
            broadcaster.fire(DEFAULT_JTA_ENVIRONMENT_BEAN);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

}
