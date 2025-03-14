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

public class TestLookup {
/*
    // JtaProvider.get() shall return NarayanaProvider instance
    @Test
    void testLookup() {
        assertThat(Services.first(JtaProvider.class).isPresent(), is(true));
        JtaProvider provider = JtaProvider.provider().get();
        assertThat(provider, notNullValue());
        assertThat(provider, instanceOf(NarayanaProvider.class));
    }

    // TransactionManager instance shall have "com.arjuna.ats" package prefix
    @Test
    void testTransactionManager() {
        assertThat(JtaProvider.provider().isPresent(), is(true));
        JtaProvider provider = JtaProvider.provider().get();
        assertThat(provider, notNullValue());
        TransactionManager tm = provider.transactionManager();
        assertThat(tm, notNullValue());
        assertThat(tm.getClass().getName(), startsWith("com.arjuna.ats"));
    }

    // UserTransaction instance shall have "com.arjuna.ats" package prefix
    @Test
    void testUserTransaction() {
        assertThat(JtaProvider.provider().isPresent(), is(true));
        JtaProvider provider = JtaProvider.provider().get();
        assertThat(provider, notNullValue());
        UserTransaction ut = provider.userTransaction();
        assertThat(ut, notNullValue());
        assertThat(ut.getClass().getName(), startsWith("com.arjuna.ats"));
    }

    // TransactionSynchronizationRegistry instance shall have "com.arjuna.ats" package prefix
    @Test
    void testTransactionSynchronizationRegistry() {
        assertThat(JtaProvider.provider().isPresent(), is(true));
        JtaProvider provider = JtaProvider.provider().get();
        assertThat(provider, notNullValue());
        TransactionSynchronizationRegistry tsr = provider.transactionSynchronizationRegistry();
        assertThat(tsr, notNullValue());
        assertThat(tsr.getClass().getName(), startsWith("com.arjuna.ats"));
    }
*/
}
