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
package io.helidon.transaction;

import java.lang.System.Logger.Level;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestTxInterceptor {

    private static final System.Logger LOGGER = System.getLogger(TestTxInterceptor.class.getName());

    private static MockTxSupport support = null;
    private static Dao dao = null;


    @BeforeAll
    static void before() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.before()");
        support = (MockTxSupport) Services.get(TxSupport.class);
        dao = Services.get(Dao.class);
    }

    @AfterAll
    static void after() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.after()");
        support = null;
        dao = null;
    }

    // Verify that interceptor was called and set type to MANDATORY
    @Test
    void testTxMandatory() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxMandatory()");
        dao.txMandatory();
        assertThat(support.txType(), is(Tx.Type.MANDATORY));
    }

    // Verify that interceptor was called and set type to NEW
    @Test
    void testTxNew() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxNew()");
        dao.txNew();
        assertThat(support.txType(), is(Tx.Type.NEW));
    }

    // Verify that interceptor was called and set type to NEVER
    @Test
    void testTxNever() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxNever()");
        dao.txNever();
        assertThat(support.txType(), is(Tx.Type.NEVER));
    }
    @Test

    // Verify that interceptor was called and set type to REQUIRED
    void testTxRequired() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxRequired()");
        dao.txRequired();
        assertThat(support.txType(), is(Tx.Type.REQUIRED));
    }

    // Verify that interceptor was called and set type to SUPPORTED
    @Test
    void testTxSupported() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxSupported()");
        dao.txSupported();
        assertThat(support.txType(), is(Tx.Type.SUPPORTED));
    }

    // Verify that interceptor was called and set type to UNSUPPORTED
    @Test
    void testTxUnsupported() {
        LOGGER.log(Level.DEBUG, "Running TestInterceptor.testTxUnsupported()");
        dao.txUnsupported();
        assertThat(support.txType(), is(Tx.Type.UNSUPPORTED));
    }

    @Service.Singleton
    static class Dao {

        @Tx.Mandatory
        void txMandatory() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txMandatory()");
        }

        @Tx.New
        void txNew() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txNew()");
        }

        @Tx.Never
        void txNever() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txNever()");
        }

        @Tx.Required
        void txRequired() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txRequired()");
        }

        @Tx.Supported
        void txSupported() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txSupported()");
        }

        @Tx.Unsupported
        void txUnsupported() {
            LOGGER.log(Level.DEBUG, "Running TestInterceptor.Dao.txUnsupported()");
        }

    }

}
