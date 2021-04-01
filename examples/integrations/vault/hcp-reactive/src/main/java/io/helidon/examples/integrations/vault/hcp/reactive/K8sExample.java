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

import java.util.Map;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.k8s.ConfigureK8s;
import io.helidon.integrations.vault.auths.k8s.CreateRole;
import io.helidon.integrations.vault.auths.k8s.K8sAuthRx;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2SecretsRx;
import io.helidon.integrations.vault.sys.SysRx;

class K8sExample {
    private static final String SECRET_PATH = "k8s/example/secret";
    private static final String POLICY_NAME = "k8s_policy";

    private final Vault tokenVault;
    private final String k8sAddress;
    private final Config config;
    private final SysRx sys;

    private Vault k8sVault;

    K8sExample(Vault tokenVault, Config config) {
        this.tokenVault = tokenVault;
        this.sys = tokenVault.sys(SysRx.API);
        this.k8sAddress = config.get("cluster-address").asString().get();
        this.config = config;
    }

    public Single<String> run() {
        /*
         The following tasks must be run before we authenticate
         */
        return enableK8sAuth()
                // Now we can login using k8s - must run within a k8s cluster (or you need the k8s configuration files locally)
                .flatMapSingle(ignored -> workWithSecrets())
                // Now back to token based Vault, as we will clean up
                .flatMapSingle(ignored -> disableK8sAuth())
                .map(ignored -> "k8s example finished successfully.");
    }

    private Single<ApiResponse> workWithSecrets() {
        Kv2SecretsRx secrets = k8sVault.secrets(Kv2SecretsRx.ENGINE);

        return secrets.create(SECRET_PATH, Map.of("secret-key", "secretValue",
                                                  "secret-user", "username"))
                .flatMapSingle(ignored -> secrets.get(SECRET_PATH))
                .peek(secret -> {
                    if (secret.isPresent()) {
                        Kv2Secret kv2Secret = secret.get();
                        System.out.println("k8s first secret: " + kv2Secret.value("secret-key"));
                        System.out.println("k8s second secret: " + kv2Secret.value("secret-user"));
                    } else {
                        System.out.println("k8s secret not found");
                    }
                }).flatMapSingle(ignored -> secrets.deleteAll(SECRET_PATH));
    }

    private Single<ApiResponse> disableK8sAuth() {
        return sys.deletePolicy(POLICY_NAME)
                .flatMapSingle(ignored -> sys.disableAuth(K8sAuthRx.AUTH_METHOD.defaultPath()));
    }

    private Single<ApiResponse> enableK8sAuth() {
        // enable the method
        return sys.enableAuth(K8sAuthRx.AUTH_METHOD)
                // add policy
                .flatMapSingle(ignored -> sys.createPolicy(POLICY_NAME, VaultPolicy.POLICY))
                .flatMapSingle(ignored -> tokenVault.auth(K8sAuthRx.AUTH_METHOD)
                        .configure(ConfigureK8s.Request.builder()
                                           .address(k8sAddress)))
                .flatMapSingle(ignored -> tokenVault.auth(K8sAuthRx.AUTH_METHOD)
                        // this must be the same role name as is defined in application.yaml
                        .createRole(CreateRole.Request.builder()
                                            .roleName("my-role")
                                            .addBoundServiceAccountName("*")
                                            .addBoundServiceAccountNamespace("default")
                                            .addTokenPolicy(POLICY_NAME)))
                .peek(ignored -> k8sVault = Vault.create(config))
                .map(Function.identity());
    }
}
