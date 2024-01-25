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
package io.helidon.docs.se.integrations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Base64Value;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.k8s.ConfigureK8s;
import io.helidon.integrations.vault.auths.k8s.CreateRole;
import io.helidon.integrations.vault.auths.k8s.K8sAuth;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecrets;
import io.helidon.integrations.vault.secrets.kv1.Kv1Secrets;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.secrets.transit.CreateKey;
import io.helidon.integrations.vault.secrets.transit.Decrypt;
import io.helidon.integrations.vault.secrets.transit.DeleteKey;
import io.helidon.integrations.vault.secrets.transit.Encrypt;
import io.helidon.integrations.vault.secrets.transit.Hmac;
import io.helidon.integrations.vault.secrets.transit.Sign;
import io.helidon.integrations.vault.secrets.transit.TransitSecrets;
import io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig;
import io.helidon.integrations.vault.secrets.transit.Verify;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@SuppressWarnings("ALL")
class HcvSnippets {

    void kv2Secrets(Config config) {
        // tag::snippet_1[]
        Vault vault = Vault.builder()
                .config(config.get("vault"))
                .build();
        Kv2Secrets secrets = vault.secrets(Kv2Secrets.ENGINE);
        // end::snippet_1[]
    }

    void sys(Vault vault) {
        // tag::snippet_2[]
        Sys sys = vault.sys(Sys.API);
        // end::snippet_2[]
    }

    void tokenVault(Config config) {
        // tag::snippet_3[]
        Vault tokenVault = Vault.builder()
                .config(config.get("vault.token"))
                .updateWebClient(it -> it
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5)))
                .build();
        // end::snippet_3[]
    }

    void webserverExample(Vault tokenVault, Config config) {
        // tag::snippet_4[]
        Sys sys = tokenVault.sys(Sys.API);
        WebServer webServer = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .register("/cubbyhole", new CubbyholeService(sys, tokenVault.secrets(CubbyholeSecrets.ENGINE)))
                        .register("/kv1", new Kv1Service(sys, tokenVault.secrets(Kv1Secrets.ENGINE)))
                        .register("/kv2", new Kv2Service(sys, tokenVault.secrets(Kv2Secrets.ENGINE)))
                        .register("/transit", new TransitService(sys, tokenVault.secrets(TransitSecrets.ENGINE))))
                .build()
                .start();
        // end::snippet_4[]
    }

    record CubbyholeService(Sys sys, CubbyholeSecrets secrets) implements HttpService {

        // tag::snippet_4[]
        @Override
        public void routing(HttpRules rules) {
            rules.get("/create", this::createSecrets)
                    .get("/secrets/{path:.*}", this::getSecret);
        }

        void createSecrets(ServerRequest req, ServerResponse res) { // <1>
            secrets.create("first/secret", Map.of("key", "secretValue"));
            res.send("Created secret on path /first/secret");
        }

        void getSecret(ServerRequest req, ServerResponse res) { // <2>
            String path = req.path().pathParameters().get("path");
            Optional<Secret> secret = secrets.get(path);
            if (secret.isPresent()) {
                // using toString so we do not need to depend on JSON-B
                res.send(secret.get().values().toString());
            } else {
                res.status(Status.NOT_FOUND_404);
                res.send();
            }
        }
        // end::snippet_4[]
    }

    record Kv1Service(Sys sys, Kv1Secrets secrets) implements HttpService {

        // tag::snippet_5[]
        @Override
        public void routing(HttpRules rules) {
            rules.get("/enable", this::enableEngine)
                    .get("/create", this::createSecrets)
                    .get("/secrets/{path:.*}", this::getSecret)
                    .delete("/secrets/{path:.*}", this::deleteSecret)
                    .get("/disable", this::disableEngine);
        }

        void disableEngine(ServerRequest req, ServerResponse res) { // <1>
            sys.disableEngine(Kv1Secrets.ENGINE);
            res.send("KV1 Secret engine disabled");
        }

        void enableEngine(ServerRequest req, ServerResponse res) { // <2>
            sys.enableEngine(Kv1Secrets.ENGINE);
            res.send("KV1 Secret engine enabled");
        }

        void createSecrets(ServerRequest req, ServerResponse res) { // <3>
            secrets.create("first/secret", Map.of("key", "secretValue"));
            res.send("Created secret on path /first/secret");
        }

        void deleteSecret(ServerRequest req, ServerResponse res) { // <4>
            String path = req.path().pathParameters().get("path");
            secrets.delete(path);
            res.send("Deleted secret on path " + path);
        }

        void getSecret(ServerRequest req, ServerResponse res) { // <5>
            String path = req.path().pathParameters().get("path");

            Optional<Secret> secret = secrets.get(path);
            if (secret.isPresent()) {
                // using toString so we do not need to depend on JSON-B
                res.send(secret.get().values().toString());
            } else {
                res.status(Status.NOT_FOUND_404);
                res.send();
            }
        }
        // end::snippet_5[]
    }

    record Kv2Service(Sys sys, Kv2Secrets secrets) implements HttpService {

        // tag::snippet_6[]
        @Override
        public void routing(HttpRules rules) {
            rules.get("/create", this::createSecrets)
                    .get("/secrets/{path:.*}", this::getSecret)
                    .delete("/secrets/{path:.*}", this::deleteSecret);
        }

        void createSecrets(ServerRequest req, ServerResponse res) { // <1>
            secrets.create("first/secret", Map.of("key", "secretValue"));
            res.send("Created secret on path /first/secret");
        }

        void deleteSecret(ServerRequest req, ServerResponse res) { // <2>
            String path = req.path().pathParameters().get("path");
            secrets.deleteAll(path);
            res.send("Deleted secret on path " + path);
        }

        void getSecret(ServerRequest req, ServerResponse res) { // <3>
            String path = req.path().pathParameters().get("path");

            Optional<Kv2Secret> secret = secrets.get(path);
            if (secret.isPresent()) {
                // using toString so we do not need to depend on JSON-B
                Kv2Secret kv2Secret = secret.get();
                res.send("Version " + kv2Secret.metadata().version() + ", secret: " + kv2Secret.values().toString());
            } else {
                res.status(Status.NOT_FOUND_404);
                res.send();
            }
        }
        // end::snippet_6[]
    }

    record TransitService(Sys sys, TransitSecrets secrets) implements HttpService {

        static final String ENCRYPTION_KEY = "";
        static final String SIGNATURE_KEY = "";
        static final Base64Value SECRET_STRING = Base64Value.create("");

        // tag::snippet_7[]
        @Override
        public void routing(HttpRules rules) {
            rules.get("/enable", this::enableEngine)
                    .get("/keys", this::createKeys)
                    .delete("/keys", this::deleteKeys)
                    .get("/batch", this::batch)
                    .get("/encrypt/{text:.*}", this::encryptSecret)
                    .get("/decrypt/{text:.*}", this::decryptSecret)
                    .get("/sign", this::sign)
                    .get("/hmac", this::hmac)
                    .get("/verify/sign/{text:.*}", this::verify)
                    .get("/verify/hmac/{text:.*}", this::verifyHmac)
                    .get("/disable", this::disableEngine);
        }

        void enableEngine(ServerRequest req, ServerResponse res) { // <1>
            sys.enableEngine(TransitSecrets.ENGINE);
            res.send("Transit Secret engine enabled");
        }

        void disableEngine(ServerRequest req, ServerResponse res) { // <2>
            sys.disableEngine(TransitSecrets.ENGINE);
            res.send("Transit Secret engine disabled");
        }

        void createKeys(ServerRequest req, ServerResponse res) { // <3>
            CreateKey.Request request = CreateKey.Request.builder()
                    .name(ENCRYPTION_KEY);

            secrets.createKey(request);
            secrets.createKey(CreateKey.Request.builder()
                                      .name(SIGNATURE_KEY)
                                      .type("rsa-2048"));

            res.send("Created keys");
        }

        void deleteKeys(ServerRequest req, ServerResponse res) { // <4>
            secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                            .name(ENCRYPTION_KEY)
                                            .allowDeletion(true));
            System.out.println("Updated key config");

            secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY));

            res.send("Deleted key.");
        }

        void encryptSecret(ServerRequest req, ServerResponse res) { // <5>
            String secret = req.path().pathParameters().get("text");

            Encrypt.Response encryptResponse = secrets.encrypt(Encrypt.Request.builder()
                                                                       .encryptionKeyName(ENCRYPTION_KEY)
                                                                       .data(Base64Value.create(secret)));

            res.send(encryptResponse.encrypted().cipherText());
        }

        void decryptSecret(ServerRequest req, ServerResponse res) { // <6>
            String encrypted = req.path().pathParameters().get("text");

            Decrypt.Response decryptResponse = secrets.decrypt(Decrypt.Request.builder()
                                                                       .encryptionKeyName(ENCRYPTION_KEY)
                                                                       .cipherText(encrypted));

            res.send(String.valueOf(decryptResponse.decrypted().toDecodedString()));
        }

        void hmac(ServerRequest req, ServerResponse res) { // <7>
            Hmac.Response hmacResponse = secrets.hmac(Hmac.Request.builder()
                                                              .hmacKeyName(ENCRYPTION_KEY)
                                                              .data(SECRET_STRING));

            res.send(hmacResponse.hmac());
        }

        void sign(ServerRequest req, ServerResponse res) { // <8>
            Sign.Response signResponse = secrets.sign(Sign.Request.builder()
                                                              .signatureKeyName(SIGNATURE_KEY)
                                                              .data(SECRET_STRING));

            res.send(signResponse.signature());
        }

        void verifyHmac(ServerRequest req, ServerResponse res) { // <9>
            String hmac = req.path().pathParameters().get("text");

            Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                                    .digestKeyName(ENCRYPTION_KEY)
                                                                    .data(SECRET_STRING)
                                                                    .hmac(hmac));

            res.send("Valid: " + verifyResponse.isValid());
        }

        void verify(ServerRequest req, ServerResponse res) { // <10>
            String signature = req.path().pathParameters().get("text");

            Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                                    .digestKeyName(SIGNATURE_KEY)
                                                                    .data(SECRET_STRING)
                                                                    .signature(signature));

            res.send("Valid: " + verifyResponse.isValid());
        }
        // end::snippet_7[]

        void batch(ServerRequest req, ServerResponse res) {
            // stub
        }
    }

    final class VaultPolicy {
        static final String POLICY = "";
    }

    // tag::snippet_8[]
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

        public String run() { // <1>
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

        private void workWithSecrets() { // <2>
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

        private void disableK8sAuth() { // <3>
            sys.deletePolicy(POLICY_NAME);
            sys.disableAuth(K8sAuth.AUTH_METHOD.defaultPath());
        }

        private void enableK8sAuth() { // <4>
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
    // end::snippet_8[]

}
