/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import jakarta.ws.rs.ProcessingException;
import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.Test;

import static com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.builder;
import static com.oracle.bmc.http.client.jersey3.ApacheClientProperties.REQUEST_CONFIG;
import static com.oracle.bmc.http.client.StandardClientProperties.CONNECT_TIMEOUT;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestInstancePrincipalsAuthenticationDetailsProvider {

    TestInstancePrincipalsAuthenticationDetailsProvider() {
        super();
    }

    @Test
    final void testThatInstancePrincipalsAuthenticationDetailsProviderAlwaysTakesManySecondsToRun() {
        // Don't run in the pipeline/Github Actions; there's no point. See comments below.
        String ci = System.getenv("CI");
        assumeTrue(ci == null || ci.isBlank());

        assertThrows(ProcessingException.class, () -> builder()
                     // (There does not seem to be a way to avoid this taking multiple seconds, no matter what timeout
                     // customization is set. This is due to the fact that the retry logic, which includes sleeping,
                     // always runs, including when, erroneously, the total number of attempts (erroneously named
                     // "retries") has been explicitly set to 1. This is a bug in
                     // com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder and has been
                     // reported. This test runs locally only.)
                     //
                     // See
                     // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/waiter/ExponentialBackoffDelayStrategy.java#L13-L21
                     //
                     // See
                     // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/waiter/ExponentialBackoffDelayStrategyWithJitter.java#L14-L20
                     //
                     // See
                     // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-common/src/main/java/com/oracle/bmc/auth/AbstractFederationClientAuthenticationDetailsProviderBuilder.java#L381-L384
                     .federationClientConfigurator(b ->
                                                   b.property(REQUEST_CONFIG, RequestConfig.custom()
                                                              .setConnectionRequestTimeout(1) // milliseconds; no visible effect
                                                              .setSocketTimeout(1) // milliseconds; no visible effect
                                                              .build())
                                                   .property(CONNECT_TIMEOUT, Duration.ofMillis(1))) // no visible effect
                     .detectEndpointRetries(1) // actually "tries", not "retries"
                     .timeoutForEachRetry(1) // milliseconds; no visible effect
                     .build()); // takes multiple seconds
    }

}
