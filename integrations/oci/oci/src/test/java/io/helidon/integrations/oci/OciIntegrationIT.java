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

package io.helidon.integrations.oci;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import io.helidon.common.configurable.Resource;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class OciIntegrationIT {

    private static final String ENV_USER = "HELIDON_OCI_IT_USER";
    private static final String ENV_FINGERPRINT = "HELIDON_OCI_IT_FINGERPRINT";
    private static final String ENV_TENANCY = "HELIDON_OCI_IT_TENANCY";
    private static final String ENV_REGION = "HELIDON_OCI_IT_REGION";
    private static final String ENV_PRIVATE_KEY = "HELIDON_OCI_IT_KEY";

    private static TestConfiguration testConfig;
    private static Path testOciConfig;
    private static Path testOciPrivateKey;

    @BeforeAll
    static void beforeAll() throws IOException {
        LogConfig.configureRuntime();

        testConfig = testConfiguration();
        createFiles();
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (testOciPrivateKey != null) {
            Files.deleteIfExists(testOciPrivateKey);
        }
        if (testOciConfig != null) {
            Files.deleteIfExists(testOciConfig);
        }
    }

    @Test
    void testRegionFromAtnProvider() {
        String yamlConfig = """
                helidon.oci:
                  config-strategy:
                    region: ${region}
                    fingerprint: ${fingerprint}
                    tenant-id: ${tenant}
                    user-id: ${user}
                    privateKey:
                        file: ${privateKey}
                  config-file-strategy:
                    # we must use a file that does not exist, if this machine has actual oci config file
                    path: src/test/resources/test-oci-config-not-there
                """;

        yamlConfig = yamlConfig.replace("${region}", testConfig.region);
        yamlConfig = yamlConfig.replace("${fingerprint}", testConfig.fingerprint);
        yamlConfig = yamlConfig.replace("${tenant}", testConfig.tenancy);
        yamlConfig = yamlConfig.replace("${user}", testConfig.user);
        yamlConfig = yamlConfig.replace("${privateKey}", testOciPrivateKey.toAbsolutePath().toString());

        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        OciConfigProvider.config(OciConfig.create(config.get("helidon.oci")));
        var registryManager = ServiceRegistryManager.create();
        var registry = registryManager.registry();

        try {
            Region region = registry.get(Region.class);
            assertThat(region, is(Region.fromRegionCodeOrId(testConfig.region)));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void testConnectConfig() {
        AtnStrategyConfig c = new AtnStrategyConfig(OciConfig.builder()
                                                            .configStrategyConfig(cfg -> cfg.userId(testConfig.user)
                                                                    .tenantId(testConfig.tenancy)
                                                                    .fingerprint(testConfig.fingerprint)
                                                                    .region(testConfig.region)
                                                                    .privateKey(Resource.create("privateKey",
                                                                                                testConfig.privateKey)))
                                                            .build());
        Optional<AbstractAuthenticationDetailsProvider> provider = c.provider();

        assertThat(provider, optionalPresent());
        AbstractAuthenticationDetailsProvider atnProvider = provider.get();

        testConnectivity(AtnStrategyConfig.STRATEGY, atnProvider);
    }

    @Test
    void testConnectConfigFile() {

        // run the test, delete the files when done
        String ociConfigPath = testOciConfig.toAbsolutePath().toString();
        AtnStrategyConfigFile f = new AtnStrategyConfigFile(OciConfig.builder()
                                                                    .configFileStrategyConfig(cfg ->
                                                                                                      cfg.path(ociConfigPath))
                                                                    .build());
        Optional<AbstractAuthenticationDetailsProvider> provider = f.provider();

        assertThat(provider, optionalPresent());
        AbstractAuthenticationDetailsProvider atnProvider = provider.get();

        testConnectivity(AtnStrategyConfigFile.STRATEGY, atnProvider);
    }

    private static void createFiles() throws IOException {
        testOciConfig = Paths.get("target/test-classes/generated-oci-config");
        testOciPrivateKey = Paths.get("target/test-classes/generated-oci-key");

        Files.createDirectories(testOciConfig.getParent());

        // create the OCI config file
        try (var ociConfigWriter = new PrintWriter(Files.newBufferedWriter(testOciConfig,
                                                                           StandardOpenOption.CREATE,
                                                                           StandardOpenOption.WRITE,
                                                                           StandardOpenOption.TRUNCATE_EXISTING))) {
            ociConfigWriter.println("[DEFAULT]");
            ociConfigWriter.print("user=");
            ociConfigWriter.println(testConfig.user);
            ociConfigWriter.print("tenancy=");
            ociConfigWriter.println(testConfig.tenancy);
            ociConfigWriter.print("fingerprint=");
            ociConfigWriter.println(testConfig.fingerprint);
            ociConfigWriter.print("region=");
            ociConfigWriter.println(testConfig.region);
            ociConfigWriter.println("key_file=" + testOciPrivateKey.toAbsolutePath());
        }

        // create the private key file
        try (var ociPrivateKeyWriter = Files.newBufferedWriter(testOciPrivateKey,
                                                               StandardOpenOption.CREATE,
                                                               StandardOpenOption.WRITE,
                                                               StandardOpenOption.TRUNCATE_EXISTING)) {
            ociPrivateKeyWriter.write(testConfig.privateKey);
        }

    }

    private static TestConfiguration testConfiguration() {
        assumeTrue(System.getenv(ENV_USER) != null, "Missing environment variable '" + ENV_USER + "'");

        String user = System.getenv(ENV_USER);
        String fingerprint = System.getenv(ENV_FINGERPRINT);
        String tenancy = System.getenv(ENV_TENANCY);
        String region = System.getenv(ENV_REGION);
        String privateKey = System.getenv(ENV_PRIVATE_KEY);

        assertThat(ENV_USER + " environment variable is required for OCI tests", user, notNullValue());
        assertThat(ENV_FINGERPRINT + " environment variable is required for OCI tests", fingerprint, notNullValue());
        assertThat(ENV_TENANCY + " environment variable is required for OCI tests", tenancy, notNullValue());
        assertThat(ENV_REGION + " environment variable is required for OCI tests", region, notNullValue());
        assertThat(ENV_PRIVATE_KEY + " environment variable is required for OCI tests", privateKey, notNullValue());

        privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + privateKey.replace(' ', '\n')
                + "\n-----END PRIVATE KEY-----\n";

        return new TestConfiguration(user, tenancy, fingerprint, region, privateKey);
    }

    private void testConnectivity(String strategy, AbstractAuthenticationDetailsProvider atnProvider) {
        try (ObjectStorageClient osc = ObjectStorageClient.builder()
                .build(atnProvider)) {

            GetNamespaceResponse namespace = osc.getNamespace(GetNamespaceRequest.builder().build());
            assertThat("Failed to get namespace for " + strategy + " strategy",
                       namespace.getValue(),
                       notNullValue());
        }
    }

    private record TestConfiguration(String user, String tenancy, String fingerprint, String region, String privateKey) {
    }
}
