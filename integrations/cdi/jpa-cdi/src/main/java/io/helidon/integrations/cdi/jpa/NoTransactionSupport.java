/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.Context;

/**
 * A {@link TransactionSupport} implementation that is loaded only if
 * JTA is not available.
 *
 * <p>See the exclusion stanzas in {@code META-INF/beans.xml} for more
 * details.</p>
 *
 * @see TransactionSupport
 *
 * @see JtaTransactionSupport
 */
@ApplicationScoped
final class NoTransactionSupport implements TransactionSupport {


    private static final Logger LOGGER = Logger.getLogger(NoTransactionSupport.class.getName(),
                                                          NoTransactionSupport.class.getPackage().getName() + ".Messages");


    /**
     * Creates a new {@link NoTransactionSupport}.
     */
    NoTransactionSupport() {
        super();
        final String cn = this.getClass().getName();
        final String mn = "<init>";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.logp(Level.WARNING, cn, mn, "noTransactionSupport");
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Returns {@code false} when invoked.
     *
     * @return {@code false} when invoked
     */
    @Override
    public boolean isEnabled() {
        return false;
    }

    /**
     * Returns {@code null} when invoked.
     *
     * @return {@code null} when invoked
     */
    @Override
    public Context getContext() {
        return null;
    }

    /**
     * Returns {@link TransactionSupport#STATUS_NO_TRANSACTION} when
     * invoked.
     *
     * @return {@link TransactionSupport#STATUS_NO_TRANSACTION} when
     * invoked
     */
    @Override
    public int getStatus() {
        return STATUS_NO_TRANSACTION;
    }

}
