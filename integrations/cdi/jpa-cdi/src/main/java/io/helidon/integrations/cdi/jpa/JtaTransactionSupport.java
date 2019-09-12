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

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.transaction.TransactionScoped;

/**
 * A {@link TransactionSupport} implementation that is loaded only if
 * JTA is available.
 *
 * <p>See the exclusion stanzas in {@code META-INF/beans.xml} for more
 * details.</p>
 */
@ApplicationScoped
final class JtaTransactionSupport implements TransactionSupport {


    /*
     * Instance fields.
     */


    /**
     * A {@link Provider} of {@link BeanManager} instances.
     *
     * <p>This field will never be {@code null}.</p>
     */
    private final Provider<BeanManager> beanManagerProvider;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaTransactionSupport}.
     *
     * @param beanManagerProvider a {@link Provider} of {@link
     * BeanManager} instances; must not be {@code null}
     *
     * @exception NullPointerException
     */
    @Inject
    JtaTransactionSupport(final Provider<BeanManager> beanManagerProvider) {
        super();
        this.beanManagerProvider = Objects.requireNonNull(beanManagerProvider);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns {@code true} when invoked.
     *
     * @return {@code true} when invoked
     */
    @Override
    public boolean isActive() {
        return true;
    }

    /**
     * Returns the {@link Context} that supports JTA transactions, if
     * there is one and it is active.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the active {@link Context} that supports JTA
     * transactions, if there is one and it is active; {@code null} in
     * all other cases
     */
    @Override
    public Context getContext() {
        final Context returnValue;
        final BeanManager beanManager = this.beanManagerProvider.get();
        if (beanManager == null) {
            returnValue = null;
        } else {
            Context temp = null;
            try {
                temp = beanManager.getContext(TransactionScoped.class);
            } catch (final ContextNotActiveException contextNotActiveException) {
                temp = null;
            } finally {
                returnValue = temp;
            }
        }
        return returnValue;
    }

    /**
     * Returns {@code true} if the return value of {@link
     * #getContext()} is non-{@code null}.
     *
     * @return {@code true} if the return value of {@link
     * #getContext()} is non-{@code null}; {@code false} otherwise
     */
    @Override
    public boolean inTransaction() {
        return this.getContext() != null;
    }

}
