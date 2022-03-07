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
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import com.oracle.bmc.ConfigFileReader;
// Arbitrary
//
// If the tests work for this, they should work for all.  (Feel free
// to substitute other OCI imports.)
import com.oracle.bmc.ailanguage.AIServiceLanguage;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsync;
import com.oracle.bmc.ailanguage.AIServiceLanguageAsyncClient;
import com.oracle.bmc.ailanguage.AIServiceLanguageClient;
// End Arbitrary
//
// Special
//
// Streaming is a strange case where they didn't really do builders
// the same way as for every other service in the portfolio for no
// particular reason.
import com.oracle.bmc.streaming.Stream;
import com.oracle.bmc.streaming.StreamAdmin;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.StreamAsync;
import com.oracle.bmc.streaming.StreamAsyncClient;
import com.oracle.bmc.streaming.StreamAsyncClientBuilder;
import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.StreamClientBuilder;
// End Special
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
        private ExampleBean(// The service* parameters below are
                            // arbitrary; pick another OCI service and
                            // substitute the classes as appropriate
                            // and this will still work (assuming of
                            // course you also add the proper jar file
                            // to this project's test-scoped
                            // dependencies).
                            AIServiceLanguage serviceInterface,
                            AIServiceLanguageAsync serviceAsyncInterface,
                            AIServiceLanguageAsyncClient serviceAsyncClient,
                            AIServiceLanguageAsyncClient.Builder serviceAsyncClientBuilder,
                            AIServiceLanguageClient serviceClient,
                            AIServiceLanguageClient.Builder serviceClientBuilder,
                            // This one is an example of something
                            // that looks like a service client but
                            // isn't; see the comments in the
                            // constructor body below.  It should be
                            // unsatisfied.
                            Instance<com.oracle.bmc.circuitbreaker.JaxRsCircuitBreaker> unresolvedJaxRsCircuitBreakerInstance,
                            // Streaming turns out to be the only
                            // convention-violating service in the
                            // entire portfolio.  Specifically, its
                            // root subpackage features two main
                            // domain objects (Stream, StreamAdmin)
                            // and only one of them (StreamAdmin)
                            // fully follows the service client
                            // pattern.  The other one (Stream)
                            // features a builder class that is not a
                            // nested class (StreamClientBuilder) but
                            // maybe should be.  We test this
                            // explicitly here because, again, it is
                            // the only service in the entire
                            // portfolio that breaks the pattern.
                            Stream streamingServiceInterface,
                            StreamAdmin streamingAdminServiceInterface,
                            StreamAdminClient streamingAdminServiceClient,
                            StreamAdminClient.Builder streamingAdminServiceClientBuilder,
                            StreamAsync streamingServiceAsyncInterface,
                            StreamAsyncClient streamingServiceAsyncClient,
                            StreamAsyncClientBuilder streamingServiceAsyncClientBuilder,
                            StreamClient streamingServiceClient, // oddball
                            StreamClientBuilder streamingServiceClientBuilder) { // oddball
            super();
            // unresolvedJaxRsCircuitBreakerInstance
            // (Instance<com.oracle.bmc.circuitbreaker.JaxRsCircuitBreaker>)
            // is an example of something that seems like it obeys the
            // OCI client service pattern.  But it deviates in a
            // couple ways.
            //
            // First, JaxRsCircuitBreaker is part of the common
            // runtime (see
            // https://docs.oracle.com/en-us/iaas/tools/java/2.18.0/overview-summary.html#:~:text=Oracle%20Cloud%20Infrastructure%20Common%20Runtime),
            // and hence is not a service itself.
            //
            // Second, there are no JaxRsCircuitBreakerAsync,
            // JaxRsCircuitBreakerAsyncClient,
            // JaxRsCircuitBreakerAsyncClient$Builder,
            // JaxRsCircuitBreakerClient or
            // JaxRsCircuitBreakerClient$Builder classes.
            //
            // For all these reasons it should be excluded from
            // processing (and is, or this test would fail).
            assertTrue(unresolvedJaxRsCircuitBreakerInstance.isUnsatisfied());
        }

        private static void customizeAsyncBuilder(@Observes AIServiceLanguageAsyncClient.Builder acb) {
            customizeAsyncBuilderCalled = true;
        }

        private static void customizeBuilder(@Observes AIServiceLanguageClient.Builder cb) {
            customizeBuilderCalled = true;
        }

        private static void customizeAsyncBuilder(@Observes StreamAsyncClientBuilder streamingAsyncClientBuilder) {
            // See
            // https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/streaming-quickstart-oci-sdk-for-java.htm#:~:text=Streams%20are%20assigned%20a%20specific%20endpoint%20url
            streamingAsyncClientBuilder.endpoint("BOGUS");
        }

        private static void customizeAsyncBuilder(@Observes StreamClientBuilder streamingClientBuilder) {
            // See
            // https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/streaming-quickstart-oci-sdk-for-java.htm#:~:text=Streams%20are%20assigned%20a%20specific%20endpoint%20url
            streamingClientBuilder.endpoint("BOGUS");
        }

    }

}
