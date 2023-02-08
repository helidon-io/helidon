/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.weld;

import io.helidon.integrations.jta.cdi.NarayanaExtension;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@ApplicationScoped
public class TestAutomaticUserTransactionInjection {

    private SeContainer cdiContainer;

    TestAutomaticUserTransactionInjection() {
        super();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void startCdiContainer() {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addBeanClasses(TestAutomaticUserTransactionInjection.class)
            .addExtensions(NarayanaExtension.class);
        assertThat(initializer, notNullValue());
        this.cdiContainer = initializer.initialize();
    }

    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }

    private void onJtaEnvironmentBeanLoad(@Observes final JTAEnvironmentBean instance) {
        assertThat(instance, notNullValue());
    }
    
    private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                           final UserTransaction userTransaction)
        throws NotSupportedException, SystemException {
        assertThat(userTransaction, notNullValue());
        assertThat(userTransaction.toString(), is("Transaction: unknown"));
        assertThat(userTransaction.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        try {
            userTransaction.begin();
            assertThat(userTransaction.getStatus(), is(Status.STATUS_ACTIVE));
        } finally {
            userTransaction.rollback();
            assertThat(userTransaction.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        }
    }

    @Test
    void testSpike() {

    }    

}
