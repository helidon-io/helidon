/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.vault.hcp.reactive;

import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.CompletionAwaitable;
import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecretsRx;
import io.helidon.integrations.vault.secrets.kv1.Kv1SecretsRx;
import io.helidon.integrations.vault.secrets.kv2.Kv2SecretsRx;
import io.helidon.integrations.vault.secrets.transit.TransitSecretsRx;
import io.helidon.integrations.vault.sys.SysRx;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of example.
 */
public final class ReactiveVaultMain {
    private ReactiveVaultMain() {
    }

    /**
     * Main method of example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        // as I cannot share my secret configuration, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        // we have three configurations available
        // 1. Token based authentication
        Vault tokenVault = Vault.builder()
                .config(config.get("vault.token"))
                .updateWebClient(it -> it.connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS))
                .build();

        // 2. App role based authentication - must be created after we obtain the role id an token
        // 3. Kubernetes (k8s) based authentication (requires to run on k8s) - must be created after we create
        //      the authentication method

        // the tokenVault is using the root token and can be used to enable engines and
        // other authentication mechanisms

        CompletionAwaitable<Void> k8sFuture = new K8sExample(tokenVault, config.get("vault.k8s"))
                .run()
                .forSingle(System.out::println);

        CompletionAwaitable<Void> appRoleFuture = new AppRoleExample(tokenVault, config.get("vault.approle"))
                .run()
                .forSingle(System.out::println);

        /*
        We do not need to block here for our examples, as the server started below will keep the process running
         */

        SysRx sys = tokenVault.sys(SysRx.API);
        // we use await for webserver, as we do not care if we block the main thread - it is not used
        // for anything
        WebServer webServer = WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/cubbyhole", new CubbyholeService(sys, tokenVault.secrets(CubbyholeSecretsRx.ENGINE)))
                                 .register("/kv1", new Kv1Service(sys, tokenVault.secrets(Kv1SecretsRx.ENGINE)))
                                 .register("/kv2", new Kv2Service(sys, tokenVault.secrets(Kv2SecretsRx.ENGINE)))
                                 .register("/transit", new TransitService(sys, tokenVault.secrets(TransitSecretsRx.ENGINE))))
                .build()
                .start()
                .await();

        try {
            appRoleFuture.await();
        } catch (Exception e) {
            System.err.println("AppRole example failed");
            e.printStackTrace();
        }

        try {
            k8sFuture.await();
        } catch (Exception e) {
            System.err.println("Kubernetes example failed");
            e.printStackTrace();
        }

        String baseAddress = "http://localhost:" + webServer.port() + "/";
        System.out.println("Server started on " + baseAddress);
        System.out.println();
        System.out.println("Key/Value Version 1 Secrets Engine");
        System.out.println("\t" + baseAddress + "kv1/enable");
        System.out.println("\t" + baseAddress + "kv1/create");
        System.out.println("\t" + baseAddress + "kv1/secrets/first/secret");
        System.out.println("\tcurl -i -X DELETE " + baseAddress + "kv1/secrets/first/secret");
        System.out.println("\t" + baseAddress + "kv1/disable");
        System.out.println();
        System.out.println("Key/Value Version 2 Secrets Engine");
        System.out.println("\t" + baseAddress + "kv2/create");
        System.out.println("\t" + baseAddress + "kv2/secrets/first/secret");
        System.out.println("\tcurl -i -X DELETE " + baseAddress + "kv2/secrets/first/secret");
        System.out.println();
        System.out.println("Transit Secrets Engine");
        System.out.println("\t" + baseAddress + "transit/enable");
        System.out.println("\t" + baseAddress + "transit/keys");
        System.out.println("\t" + baseAddress + "transit/encrypt/secret_text");
        System.out.println("\t" + baseAddress + "transit/decrypt/cipher_text");
        System.out.println("\t" + baseAddress + "transit/sign");
        System.out.println("\t" + baseAddress + "transit/verify/sign/signature_text");
        System.out.println("\t" + baseAddress + "transit/hmac");
        System.out.println("\t" + baseAddress + "transit/verify/hmac/hmac_text");
        System.out.println("\tcurl -i -X DELETE " + baseAddress + "transit/keys");
        System.out.println("\t" + baseAddress + "transit/disable");
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
