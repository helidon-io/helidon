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

package io.helidon.examples.integrations.vault.hcp;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.approle.AppRoleAuth;
import io.helidon.integrations.vault.auths.approle.AppRoleVaultAuth;
import io.helidon.integrations.vault.auths.approle.CreateAppRole;
import io.helidon.integrations.vault.auths.approle.GenerateSecretId;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.sys.EnableAuth;
import io.helidon.integrations.vault.sys.Sys;

class AppRoleExample {
    private static final String SECRET_PATH = "approle/example/secret";
    private static final String ROLE_NAME = "approle_role";
    private static final String POLICY_NAME = "approle_policy";
    private static final String CUSTOM_APP_ROLE_PATH = "customapprole";

    private final Vault tokenVault;
    private final Config config;
    private final Sys sys;

    private Vault appRoleVault;

    AppRoleExample(Vault tokenVault, Config config) {
        this.tokenVault = tokenVault;
        this.config = config;

        this.sys = tokenVault.sys(Sys.API);
    }

    public String run() {
        /*
         The following tasks must be run before we authenticate
         */
        enableAppRoleAuth();
        workWithSecrets();
        disableAppRoleAuth();
        return "AppRole example finished successfully.";
    }

    private void workWithSecrets() {
        Kv2Secrets secrets = appRoleVault.secrets(Kv2Secrets.ENGINE);
        secrets.create(SECRET_PATH, Map.of("secret-key", "secretValue",
                "secret-user", "username"));
        Optional<Kv2Secret> secret = secrets.get(SECRET_PATH);
        if (secret.isPresent()) {
            Kv2Secret kv2Secret = secret.get();
            System.out.println("appRole first secret: " + kv2Secret.value("secret-key"));
            System.out.println("appRole second secret: " + kv2Secret.value("secret-user"));
        } else {
            System.out.println("appRole secret not found");
        }
        secrets.deleteAll(SECRET_PATH);
    }

    private void disableAppRoleAuth() {
        sys.deletePolicy(POLICY_NAME);
        sys.disableAuth(CUSTOM_APP_ROLE_PATH);
    }

    private void enableAppRoleAuth() {

        // enable the method
        sys.enableAuth(EnableAuth.Request.builder()
                                         .auth(AppRoleAuth.AUTH_METHOD)
                                         // must be aligned with path configured in application.yaml
                                         .path(CUSTOM_APP_ROLE_PATH));

        // add policy
        sys.createPolicy(POLICY_NAME, VaultPolicy.POLICY);

        tokenVault.auth(AppRoleAuth.AUTH_METHOD, CUSTOM_APP_ROLE_PATH)
                  .createAppRole(CreateAppRole.Request.builder()
                                                      .roleName(ROLE_NAME)
                                                      .addTokenPolicy(POLICY_NAME)
                                                      .tokenExplicitMaxTtl(Duration.ofMinutes(1)));

        String roleId = tokenVault.auth(AppRoleAuth.AUTH_METHOD, CUSTOM_APP_ROLE_PATH)
                                  .readRoleId(ROLE_NAME)
                                  .orElseThrow();


        GenerateSecretId.Response response = tokenVault.auth(AppRoleAuth.AUTH_METHOD, CUSTOM_APP_ROLE_PATH)
                                                       .generateSecretId(GenerateSecretId.Request.builder()
                                                                                                 .roleName(ROLE_NAME)
                                                                                                 .addMetadata("name", "helidon"));

        String secretId = response.secretId();

        System.out.println("roleId: " + roleId);
        System.out.println("secretId: " + secretId);
        appRoleVault = Vault.builder()
                            .config(config)
                            .addVaultAuth(AppRoleVaultAuth.builder()
                                                          .path(CUSTOM_APP_ROLE_PATH)
                                                          .appRoleId(roleId)
                                                          .secretId(secretId)
                                                          .build())
                            .build();
    }
}
