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
package io.helidon.integrations.cdi.jpa.weld;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.transaction.TransactionScoped;

/**
 * A bean housing an observer method that alerts a {@link
 * WeldJpaInjectionServices} instance when a JTA transaction is
 * available.
 *
 * <h2>Design Notes</h2>
 *
 * <p>This class is excluded by this bean archive's {@code
 * META-INF/beans.xml} resource if the {@link TransactionScoped
 * javax.transaction.TransactionScoped} class is not available.  This
 * has the effect of fully decoupling the rest of this bean archive
 * (most notably the {@link WeldJpaInjectionServices} class) from
 * transactional concerns if a JTA implementation is not present at
 * runtime.</p>
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see WeldJpaInjectionServices
 *
 * @see Initialized
 *
 * @see TransactionScoped
 */
@ApplicationScoped
final class TransactionObserver {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link TransactionObserver}.
     */
    private TransactionObserver() {
        super();
        final String cn = this.getClass().getName();
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, "<init>");
            logger.exiting(cn, "<init>");
        }
    }


    /*
     * Static methods.
     */


    /**
     * Observes the {@linkplain Initialized initialization} of the
     * {@link TransactionScoped} scope and calls the {@link
     * WeldJpaInjectionServices#jtaTransactionBegun()} method on
     * the supplied {@link WeldJpaInjectionServices} instance.
     *
     * @param event the opaque event that represents the
     * initialization of the {@link TransactionScoped} scope; may be
     * {@code null}; ignored
     *
     * @param services the {@link WeldJpaInjectionServices} to
     * notify; may be {@code null} in which case no action will be
     * taken
     */
    private static void jtaTransactionBegun(@Observes @Initialized(TransactionScoped.class) final Object event,
                                            final WeldJpaInjectionServices services) {
        final String cn = TransactionObserver.class.getName();
        final String mn = "jtaTransactionBegun";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {event, services});
        }

        if (services != null) {
            services.jtaTransactionBegun();
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

    /**
     * Observes the {@linkplain Destroyed destruction} of the
     * {@link TransactionScoped} scope and calls the {@link
     * WeldJpaInjectionServices#jtaTransactionEnded()} method on
     * the supplied {@link WeldJpaInjectionServices} instance.
     *
     * @param event the opaque event that represents the
     * destruction of the {@link TransactionScoped} scope; may be
     * {@code null}; ignored
     *
     * @param services the {@link WeldJpaInjectionServices} to
     * notify; may be {@code null} in which case no action will be
     * taken
     */
    private static void jtaTransactionEnded(@Observes @Destroyed(TransactionScoped.class) final Object event,
                                            final WeldJpaInjectionServices services) {
        final String cn = TransactionObserver.class.getName();
        final String mn = "jtaTransactionEnded";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {event, services});
        }

        if (services != null) {
            services.jtaTransactionEnded();
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
        }
    }

}
