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
import java.util.concurrent.TimeUnit;

import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import org.junit.jupiter.api.Test;

import static com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.builder;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.newBufferedWriter;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSessionTokenAuthenticationDetailsProvider {

    TestSessionTokenAuthenticationDetailsProvider() {
        super();
    }

    @Test
    final void testMinimalConfigFileBasedConstruction() throws IOException {
        // This test shows that when you create a new SessionTokenAuthenticationDetailsProvider from its
        // ConfigFile-based constructors, you need only minimal information, and that information is different from what
        // you need when you use the builder directly. Specifically you need only:
        //
        // * key_file (not validated on construction but must be non-null)
        // * security_token_file (validated on construction and must be non-null but may be empty)
        // * tenancy (not validated on construction but must be non-null)
        Path ociConfig = createTempFile(null, null);
        Path securityTokenFile = createTempFile(null, null);
        try (BufferedWriter w = newBufferedWriter(ociConfig)) {
            w.write("""
                    [DEFAULT]
                    # note the lack of fingerprint
                    # fingerprint=a non-null value
                    key_file=a non-null value
                    # note the lack of region
                    # region=us-phoenix-1
                    security_token_file=STF
                    tenancy=a non-null value
                    # note the lack of user
                    # user=a non-null value
                    """.replace("STF", securityTokenFile.toString()));
            w.flush();
            new SessionTokenAuthenticationDetailsProvider(ociConfig.toString(), null);
        } finally {
            deleteIfExists(securityTokenFile);
            deleteIfExists(ociConfig);
        }
    }

    @Test
    final void testExamplePortion1() throws IOException {
        // This test exercises a portion of the OCI example that shows proper usage of
        // SessionTokenAuthenticationDetailsProvider. The example is broken.
        //
        // This portion of the example works; see
        // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-examples/src/main/java/SessionTokenExample.java#L45-L54
        builder()
            .privateKeyFilePath("a non-null value")
            .region("us-phoenix-1") // valid region code or ID required apparently
            .sessionToken("<token>")
            .tenantId("a non-null value")
            .build();
    }

    @Test
    final void testExamplePortion2Fails() throws IOException {
        // This test exercises a portion of the OCI example that shows proper usage of
        // SessionTokenAuthenticationDetailsProvider. The example is broken.
        //
        // This portion of the example fails; various undocumented requirements are not satisfied. See
        // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-examples/src/main/java/SessionTokenExample.java#L58-L64
        assertThrows(NullPointerException.class,
                     builder()
                     .privateKeyFilePath("a non-null value")
                     .refreshPeriod(4)
                     .sessionLifetimeHours(2)
                     .timeUnit(TimeUnit.MINUTES)::build);

    }

    @Test
    final void testExamplePortion3Fails() throws IOException {
        // This test exercises a portion of the OCI example that shows proper usage of
        // SessionTokenAuthenticationDetailsProvider. The example is broken.
        //
        // This portion of the example fails; various undocumented requirements are not satisfied. See
        // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-examples/src/main/java/SessionTokenExample.java#L68-L72
        assertThrows(NullPointerException.class, builder().disableScheduledRefresh()::build);
    }

    @Test
    final void testExamplePortion4Fails() throws IOException {
        // This test exercises a portion of the OCI example that shows proper usage of
        // SessionTokenAuthenticationDetailsProvider. The example is broken.
        //
        // This portion of the example fails; various undocumented requirements are not satisfied. See
        // https://github.com/oracle/oci-java-sdk/blob/v3.35.0/bmc-examples/src/main/java/SessionTokenExample.java#L75-L78
        assertThrows(NullPointerException.class, builder().scheduler(newScheduledThreadPool(5))::build);
    }

}
