/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.oci;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import com.oracle.bmc.ConfigFileReader;
// Arbitrary. If the tests work for this, they should work for all.
// (Feel free to substitute other OCI imports.)
import com.oracle.bmc.ailanguage.AIServiceLanguage;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsync;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsyncClient;
import com.oracle.bmc.ailanguage.AIServiceLanguageClient;
// End Arbitrary.
import io.helidon.microprofile.config.ConfigCdiExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class TestSpike {


    /*
     * Static fields.
     */


    private static String mpInitializerAllow;


    /*
     * Instance fields.
     */


    private SeContainer container;


    /*
     * Constructors.
     */


    private TestSpike() {
        super();
    }


    /*
     * Static setup and teardown methods.
     */


    @BeforeAll
    static void beforeAll() {
        mpInitializerAllow = System.getProperty("mp.initializer.allow", "false");
        System.setProperty("mp.initializer.no-warn", "true");
        System.setProperty("mp.initializer.allow", "true");
    }

    @AfterAll
    static void afterAll() {
        System.setProperty("mp.initializer.allow", mpInitializerAllow);
    }


    /*
     * Instance setup and teardown methods.
     */


    @BeforeEach
    @SuppressWarnings("deprecation") // OK to use deprecated extension constructors
    final void beforeEach() throws IOException {
        // Don't run tests if there's NO ADP anywhere; they'll show up
        // as "skipped" in the test run.
        assumeTrue(imdsAvailable() || configFileExists());
        this.container = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addExtensions(new ConfigCdiExtension(),
                           // new AdpExtension(),
                           new OciExtension())
            .addBeanClasses(ExampleBean.class)
            .initialize();
    }

    @AfterEach
    final void afterEach() {
        final SeContainer container = this.container;
        if (container != null) {
            container.close();
        }
    }


    /*
     * Test methods.
     */


    @Test
    final void testSpike() {
        this.container.select(ExampleBean.class).get();
        assertTrue(ExampleBean.customizeAsyncBuilderCalled);
        ExampleBean.customizeAsyncBuilderCalled = false;
        assertTrue(ExampleBean.customizeBuilderCalled);
        ExampleBean.customizeBuilderCalled = false;
    }


    /*
     * Static methods.
     */


    private static final boolean configFileExists() throws IOException {
        try {
            return
                ConfigFileReader.parse(System.getProperty("oci.config.file", "~/.oci/config"),
                                       System.getProperty("oci.auth.profile")) != null;
        } catch (final FileNotFoundException ignored) {
            return false;
        }
    }

    private static final boolean imdsAvailable() {
        try {
            return InetAddress.getByName(System.getProperty("oci.imds.hostname", "169.254.169.254"))
                .isReachable(Integer.getInteger("oci.imds.timeout", 500).intValue());
        } catch (final IOException ignored) {
            return false;
        }
    }


    /*
     * Inner and nested classes.
     */


    /**
     * An example bean that a user might write.
     */
    private static final class ExampleBean {


        /*
         * Static fields.
         */


        private static boolean customizeAsyncBuilderCalled;

        private static boolean customizeBuilderCalled;


        /*
         * Constructors.
         */


        @Inject
        private ExampleBean(AIServiceLanguage t,
                            AIServiceLanguageAsync at,
                            AIServiceLanguageAsyncClient ac,
                            AIServiceLanguageAsyncClient.Builder acb,
                            AIServiceLanguageClient c,
                            AIServiceLanguageClient.Builder cb) {
            super();
        }

        private static void customizeAsyncBuilder(@Observes AIServiceLanguageAsyncClient.Builder acb) {
            customizeAsyncBuilderCalled = true;
        }

        private static void customizeBuilder(@Observes AIServiceLanguageClient.Builder cb) {
            customizeBuilderCalled = true;
        }

    }

}
