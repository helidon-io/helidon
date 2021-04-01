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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.approle.AppRoleAuthRx;
import io.helidon.integrations.vault.auths.approle.AppRoleVaultAuth;
import io.helidon.integrations.vault.auths.approle.CreateAppRole;
import io.helidon.integrations.vault.auths.approle.GenerateSecretId;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2SecretsRx;
import io.helidon.integrations.vault.sys.SysRx;

class AppRoleExample {
    private static final String SECRET_PATH = "approle/example/secret";
    private static final String ROLE_NAME = "approle_role";
    private static final String POLICY_NAME = "approle_policy";

    private final Vault tokenVault;
    private final Config config;
    private final SysRx sys;

    private Vault appRoleVault;

    AppRoleExample(Vault tokenVault, Config config) {
        this.tokenVault = tokenVault;
        this.config = config;

        this.sys = tokenVault.sys(SysRx.API);
    }

    public Single<String> run() {
        /*
         The following tasks must be run before we authenticate
         */
        return enableAppRoleAuth()
                // Now we can login using AppRole
                .flatMapSingle(ignored -> workWithSecrets())
                // Now back to token based Vault, as we will clean up
                .flatMapSingle(ignored -> disableAppRoleAuth())
                .map(ignored -> "AppRole example finished successfully.");
    }

    private Single<ApiResponse> workWithSecrets() {
        Kv2SecretsRx secrets = appRoleVault.secrets(Kv2SecretsRx.ENGINE);

        return secrets.create(SECRET_PATH, Map.of("secret-key", "secretValue",
                                                  "secret-user", "username"))
                .flatMapSingle(ignored -> secrets.get(SECRET_PATH))
                .peek(secret -> {
                    if (secret.isPresent()) {
                        Kv2Secret kv2Secret = secret.get();
                        System.out.println("appRole first secret: " + kv2Secret.value("secret-key"));
                        System.out.println("appRole second secret: " + kv2Secret.value("secret-user"));
                    } else {
                        System.out.println("appRole secret not found");
                    }
                }).flatMapSingle(ignored -> secrets.deleteAll(SECRET_PATH));
    }

    private Single<ApiResponse> disableAppRoleAuth() {
        if (1 == 1) {
            return Single.empty();
        }
        return sys.deletePolicy(POLICY_NAME)
                .flatMapSingle(ignored -> sys.disableAuth(AppRoleAuthRx.AUTH_METHOD.defaultPath()));
    }

    private Single<String> enableAppRoleAuth() {
        AtomicReference<String> roleId = new AtomicReference<>();
        AtomicReference<String> secretId = new AtomicReference<>();

        // enable the method
        return sys.enableAuth(AppRoleAuthRx.AUTH_METHOD)
                // add policy
                .flatMapSingle(ignored -> sys.createPolicy(POLICY_NAME, VaultPolicy.POLICY))
                .flatMapSingle(ignored -> tokenVault.auth(AppRoleAuthRx.AUTH_METHOD)
                        .createAppRole(CreateAppRole.Request.builder()
                                               .roleName(ROLE_NAME)
                                               .addTokenPolicy(POLICY_NAME)
                                               .tokenExplicitMaxTtl(Duration.ofMinutes(1))))
                .flatMapSingle(ignored -> tokenVault.auth(AppRoleAuthRx.AUTH_METHOD)
                        .readRoleId(ROLE_NAME))
                .peek(it -> it.ifPresent(roleId::set))
                .flatMapSingle(ignored -> tokenVault.auth(AppRoleAuthRx.AUTH_METHOD)
                        .generateSecretId(GenerateSecretId.Request.builder()
                                                  .roleName(ROLE_NAME)
                                                  .addMetadata("name", "helidon")))
                .map(GenerateSecretId.Response::secretId)
                .peek(secretId::set)
                .peek(ignored -> {
                    System.out.println("roleId: " + roleId.get());
                    System.out.println("secretId: " + secretId.get());
                    appRoleVault = Vault.builder()
                            .config(config)
                            .addVaultAuth(AppRoleVaultAuth.builder()
                                                  .appRoleId(roleId.get())
                                                  .secretId(secretId.get())
                                                  .build())
                            .build();
                });
    }
}
