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

import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.k8s.ConfigureK8s;
import io.helidon.integrations.vault.auths.k8s.CreateRole;
import io.helidon.integrations.vault.auths.k8s.K8sAuth;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.sys.Sys;

class K8sExample {
    private static final String SECRET_PATH = "k8s/example/secret";
    private static final String POLICY_NAME = "k8s_policy";

    private final Vault tokenVault;
    private final String k8sAddress;
    private final Config config;
    private final Sys sys;

    private Vault k8sVault;

    K8sExample(Vault tokenVault, Config config) {
        this.tokenVault = tokenVault;
        this.sys = tokenVault.sys(Sys.API);
        this.k8sAddress = config.get("cluster-address").asString().get();
        this.config = config;
    }

    public String run() {
        /*
         The following tasks must be run before we authenticate
         */
        enableK8sAuth();
        // Now we can login using k8s - must run within a k8s cluster (or you need the k8s configuration files locally)
        workWithSecrets();
        // Now back to token based Vault, as we will clean up
        disableK8sAuth();
        return "k8s example finished successfully.";
    }

    private void workWithSecrets() {
        Kv2Secrets secrets = k8sVault.secrets(Kv2Secrets.ENGINE);

        secrets.create(SECRET_PATH, Map.of("secret-key", "secretValue",
                "secret-user", "username"));

        Optional<Kv2Secret> secret = secrets.get(SECRET_PATH);
        if (secret.isPresent()) {
            Kv2Secret kv2Secret = secret.get();
            System.out.println("k8s first secret: " + kv2Secret.value("secret-key"));
            System.out.println("k8s second secret: " + kv2Secret.value("secret-user"));
        } else {
            System.out.println("k8s secret not found");
        }
        secrets.deleteAll(SECRET_PATH);
    }

    private void disableK8sAuth() {
        sys.deletePolicy(POLICY_NAME);
        sys.disableAuth(K8sAuth.AUTH_METHOD.defaultPath());
    }

    private void enableK8sAuth() {
        // enable the method
        sys.enableAuth(K8sAuth.AUTH_METHOD);
        sys.createPolicy(POLICY_NAME, VaultPolicy.POLICY);
        tokenVault.auth(K8sAuth.AUTH_METHOD)
                  .configure(ConfigureK8s.Request.builder()
                                                 .address(k8sAddress));
        tokenVault.auth(K8sAuth.AUTH_METHOD)
                  // this must be the same role name as is defined in application.yaml
                  .createRole(CreateRole.Request.builder()
                                                .roleName("my-role")
                                                .addBoundServiceAccountName("*")
                                                .addBoundServiceAccountNamespace("default")
                                                .addTokenPolicy(POLICY_NAME));
        k8sVault = Vault.create(config);
    }
}
