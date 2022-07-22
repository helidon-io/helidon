/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault.reactive;

import java.io.IOException;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.keymanagement.KmsCryptoAsync;
import com.oracle.bmc.keymanagement.KmsCryptoAsyncClient;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.secrets.SecretsAsync;
import com.oracle.bmc.secrets.SecretsAsyncClient;
import com.oracle.bmc.vault.VaultsAsync;
import com.oracle.bmc.vault.VaultsAsyncClient;

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

        SecretsAsync secrets = SecretsAsyncClient.builder().build(authProvider);
        KmsCryptoAsync crypto = KmsCryptoAsyncClient.builder()
                .endpoint(cryptoEndpoint)
                .build(authProvider);
        VaultsAsync vaults = VaultsAsyncClient.builder().build(authProvider);

        WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/vault", new VaultService(secrets,
                                                                      vaults,
                                                                      crypto,
                                                                      vaultOcid,
                                                                      compartmentOcid,
                                                                      encryptionKey,
                                                                      signatureKey))
                                 // OCI SDK error handling
                                 .error(BmcException.class, (req, res, ex) -> res.status(ex.getStatusCode())
                                         .send(ex.getMessage())))
                .build()
                .start()
                .await();
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
