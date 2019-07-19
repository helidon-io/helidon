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
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.transaction.TransactionScoped;

@ApplicationScoped
final class JtaTransactionSupport implements TransactionSupport {

    private final Provider<BeanManager> beanManagerProvider;

    @Inject
    private JtaTransactionSupport(final Provider<BeanManager> beanManagerProvider) {
        super();
        this.beanManagerProvider = beanManagerProvider;
    }

    @Override
    public boolean isActive() {
        return this.beanManagerProvider != null;
    }

    @Override
    public Context getContext() {
        final Context returnValue;
        if (this.beanManagerProvider == null) {
            returnValue = null;
        } else {
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
        }
        return returnValue;
    }

    @Override
    public boolean inTransaction() {
        final boolean returnValue;
        if (this.beanManagerProvider == null) {
            returnValue = false;
        } else {
            returnValue = this.getContext() != null;
        }
        return returnValue;
    }

}
