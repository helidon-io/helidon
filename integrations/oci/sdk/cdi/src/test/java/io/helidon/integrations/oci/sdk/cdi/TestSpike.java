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
package io.helidon.integrations.oci.sdk.cdi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import com.oracle.bmc.ConfigFileReader;
// Arbitrary
//
// If the tests work for this, they will work for all.  (Feel free
// to substitute other OCI service imports.)
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
// particular reason (some builders are top-level classes, not nested
// classes). We test this single outlier explicitly here.
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

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@AddBean(TestSpike.ExampleBean.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(OciExtension.class)
@DisableDiscovery
@HelidonTest
class TestSpike {


    /*
     * Instance fields.
     */


    @Inject
    private Provider<ExampleBean> exampleBeanProvider;


    /*
     * Constructors.
     */


    private TestSpike() {
        super();
    }


    /*
     * Test methods.
     */


    @Test
    void testSpike() throws IOException {
        // Don't run this test if there's NO ADP anywhere; it will
        // show up as "skipped" in the test run.
        assumeTrue(imdsAvailable() || configFileExists());

        ExampleBean bean = this.exampleBeanProvider.get();
        assertNotNull(bean);

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
                .isReachable(Integer.getInteger("oci.imds.timeout", 100).intValue());
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
    @Dependent
    static class ExampleBean {


        /*
         * Static fields.
         */


        private static boolean customizeAsyncBuilderCalled;

        private static boolean customizeBuilderCalled;


        /*
         * Constructors.
         */


        // Required by the CDI specification.
        @Deprecated
        ExampleBean() {
            super();
        }

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
                            Instance<com.oracle.bmc.circuitbreaker.OciCircuitBreaker> unresolvedJaxRsCircuitBreakerInstance,
                            // Streaming turns out to be the only
                            // convention-violating service in the
                            // entire portfolio, and the violation is
                            // extremely minor, and appears to be a
                            // mistake.  Specifically, its root
                            // subpackage features two main domain
                            // objects (Stream, StreamAdmin) and only
                            // one of them (StreamAdmin) fully follows
                            // the service client pattern.  The other
                            // one (Stream) features a builder class
                            // that is not a nested class
                            // (StreamClientBuilder) but maybe should
                            // be.  We test this explicitly here
                            // because, again, it is the only service
                            // in the entire portfolio that breaks the
                            // pattern.
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
            // https://docs.oracle.com/en-us/iaas/tools/java/latest/overview-summary.html#:~:text=Oracle%20Cloud%20Infrastructure%20Common%20Runtime),
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

        private static void customizeAsyncBuilder(@Observes AIServiceLanguageAsyncClient.Builder asyncClientBuilder) {
            customizeAsyncBuilderCalled = true;
        }

        private static void customizeBuilder(@Observes AIServiceLanguageClient.Builder clientBuilder) {
            customizeBuilderCalled = true;
        }

        private static void customizeAsyncBuilder(@Observes StreamAsyncClientBuilder asyncClientBuilder) {
            // See
            // https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/streaming-quickstart-oci-sdk-for-java.htm#:~:text=Streams%20are%20assigned%20a%20specific%20endpoint%20url
            asyncClientBuilder.endpoint("forTestingOnly");
        }

        private static void customizeAsyncBuilder(@Observes StreamClientBuilder clientBuilder) {
            // See
            // https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/streaming-quickstart-oci-sdk-for-java.htm#:~:text=Streams%20are%20assigned%20a%20specific%20endpoint%20url
            clientBuilder.endpoint("forTestingOnly");
        }

    }

}
