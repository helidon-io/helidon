/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault;

import java.io.IOException;

import com.oracle.bmc.keymanagement.KmsCrypto;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.vault.Vaults;
import com.oracle.bmc.vault.VaultsClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.config.Config;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.model.BmcException;
import io.helidon.nima.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of the example.
 * Boots a web server and provides REST API for Vault interactions.
 */
public final class OciVaultMain {
    private OciVaultMain() {
    }

    /**
     * Main method.
     * @param args ignored
     */
    public static void main(String[] args) throws IOException {
        LogConfig.configureRuntime();

        // as I cannot share my configuration of OCI, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        Config vaultConfig = config.get("oci.vault");
        // the following three parameters are required
        String vaultOcid = vaultConfig.get("vault-ocid").asString().get();
        String compartmentOcid = vaultConfig.get("compartment-ocid").asString().get();
        String encryptionKey = vaultConfig.get("encryption-key-ocid").asString().get();
        String signatureKey = vaultConfig.get("signature-key-ocid").asString().get();
        String cryptoEndpoint = vaultConfig.get("cryptographic-endpoint").asString().get();

        // this requires OCI configuration in the usual place
        // ~/.oci/config
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());

        Secrets secrets = SecretsClient.builder().build(authProvider);
        KmsCrypto crypto = KmsCryptoClient.builder()
                .endpoint(cryptoEndpoint)
                .build(authProvider);
        Vaults vaults = VaultsClient.builder().build(authProvider);

        WebServer server = WebServer.builder()
                .routing(routing -> routing
                        .register("/vault", new VaultService(secrets,
                                vaults,
                                crypto,
                                vaultOcid,
                                compartmentOcid,
                                encryptionKey,
                                signatureKey))
                        .error(BmcException.class, (req, res, ex) -> res.status(
                                ex.getStatusCode()).send(ex.getMessage())))
                .port(config.get("server.port").asInt().orElse(8080))
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());

    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        // you can use this file to override the defaults that are built-in
                        file(System.getProperty("user.home") + "/helidon/conf/examples.yaml").optional(),
                        // in jar file (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                .build();
    }
}
