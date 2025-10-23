/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction.narayana;

import java.util.Optional;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import io.helidon.service.jndi.NamingFactory;
import io.helidon.service.registry.Services;
import io.helidon.transaction.jta.JtaProvider;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class TestLookup {
    static {
        NamingFactory.register();
    }

    // JtaProvider.get() shall return NarayanaProvider instance
    @Test
    void testLookup() {
        Optional<JtaProvider> provider = Services.first(JtaProvider.class);
        assertThat(provider.isPresent(), is(true));
        assertThat(provider.get(), instanceOf(NarayanaProvider.class));
    }

    // TransactionManager instance shall have "com.arjuna.ats" package prefix
    @Test
    void testTransactionManager() {
        Optional<JtaProvider> provider = Services.first(JtaProvider.class);
        assertThat(provider.isPresent(), is(true));
        TransactionManager tm = provider.get().transactionManager();
        assertThat(tm, notNullValue());
        assertThat(tm.getClass().getName(), startsWith("com.arjuna.ats"));
    }

    // UserTransaction instance shall have "com.arjuna.ats" package prefix
    @Test
    void testUserTransaction() {
        Optional<JtaProvider> provider = Services.first(JtaProvider.class);
        assertThat(provider.isPresent(), is(true));
        UserTransaction ut = provider.get().userTransaction();
        assertThat(ut, notNullValue());
        assertThat(ut.getClass().getName(), startsWith("com.arjuna.ats"));
    }

    // TransactionSynchronizationRegistry instance shall have "com.arjuna.ats" package prefix
    @Test
    void testTransactionSynchronizationRegistry() {
        Optional<JtaProvider> provider = Services.first(JtaProvider.class);
        assertThat(provider.isPresent(), is(true));
        TransactionSynchronizationRegistry tsr = provider.get().transactionSynchronizationRegistry();
        assertThat(tsr, notNullValue());
        assertThat(tsr.getClass().getName(), startsWith("com.arjuna.ats"));
    }

    // Test JNDI lookup of TransactionManager
    @Test
    void testTransactionManagerLookup() throws NamingException {
        InitialContext ctx = new InitialContext();
        TransactionManager tm = (TransactionManager) ctx.lookup("java:comp/TransactionManager");
        assertThat(tm, notNullValue());
    }

}
