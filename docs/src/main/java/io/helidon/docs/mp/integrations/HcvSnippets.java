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
package io.helidon.docs.mp.integrations;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.Base64Value;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.cdi.VaultName;
import io.helidon.integrations.vault.cdi.VaultPath;
import io.helidon.integrations.vault.secrets.cubbyhole.CreateCubbyhole;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecrets;
import io.helidon.integrations.vault.secrets.cubbyhole.DeleteCubbyhole;
import io.helidon.integrations.vault.secrets.kv1.CreateKv1;
import io.helidon.integrations.vault.secrets.kv1.DeleteKv1;
import io.helidon.integrations.vault.secrets.kv1.Kv1Secrets;
import io.helidon.integrations.vault.secrets.kv2.CreateKv2;
import io.helidon.integrations.vault.secrets.kv2.DeleteAllKv2;
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
import io.helidon.integrations.vault.sys.DisableEngine;
import io.helidon.integrations.vault.sys.EnableEngine;
import io.helidon.integrations.vault.sys.Sys;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@SuppressWarnings("ALL")
class HcvSnippets {

    class Snippet1 {

        // stub
        static final String ENCRYPTION_KEY = "";

        // tag::snippet_1[]
        @Path("/transit")
        class TransitResource {
            private final TransitSecrets secrets;

            @Inject
            TransitResource(TransitSecrets secrets) {
                this.secrets = secrets;
            }

            @Path("/encrypt/{secret: .*}")
            @GET
            public String encrypt(@PathParam("secret") String secret) {
                return secrets.encrypt(Encrypt.Request.builder()
                                               .encryptionKeyName(ENCRYPTION_KEY)
                                               .data(Base64Value.create(secret)))
                        .encrypted()
                        .cipherText();
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Path("/cubbyhole")
        public class CubbyholeResource {
            private final CubbyholeSecrets secrets;

            @Inject
            CubbyholeResource(CubbyholeSecrets secrets) {
                this.secrets = secrets;
            }

            @POST
            @Path("/secrets/{path: .*}")
            public Response createSecret(@PathParam("path") String path, String secret) { // <1>
                CreateCubbyhole.Response response = secrets.create(path, Map.of("secret", secret));

                return Response.ok()
                        .entity(String.format(
                                "Created secret on path: %s. , key is \"secret\", original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @DELETE
            @Path("/secrets/{path: .*}")
            public Response deleteSecret(@PathParam("path") String path) { // <2>
                DeleteCubbyhole.Response response = secrets.delete(path);

                return Response.ok()
                        .entity(String.format(
                                "Deleted secret on path: %s. Original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @GET
            @Path("/secrets/{path: .*}")
            public Response getSecret(@PathParam("path") String path) { // <3>
                Optional<Secret> secret = secrets.get(path);

                if (secret.isPresent()) {
                    Secret kv1Secret = secret.get();
                    return Response.ok()
                            .entity("Secret: " + secret.get().values().toString())
                            .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            }
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Path("/kv1")
        public class Kv1Resource {
            private final Sys sys;
            private final Kv1Secrets secrets;

            @Inject
            Kv1Resource(Sys sys, Kv1Secrets secrets) {
                this.sys = sys;
                this.secrets = secrets;
            }

            @Path("/engine")
            @GET
            public Response enableEngine() { // <1>
                EnableEngine.Response response = sys.enableEngine(Kv1Secrets.ENGINE);

                return Response.ok()
                        .entity("Key/value version 1 secret engine is now enabled."
                                + " Original status: " + response.status().code())
                        .build();
            }

            @Path("/engine")
            @DELETE
            public Response disableEngine() { // <2>
                DisableEngine.Response response = sys.disableEngine(Kv1Secrets.ENGINE);
                return Response.ok()
                        .entity("Key/value version 1 secret engine is now disabled."
                                + " Original status: " + response.status().code())
                        .build();
            }

            @POST
            @Path("/secrets/{path: .*}")
            public Response createSecret(@PathParam("path") String path, String secret) { // <3>
                CreateKv1.Response response = secrets.create(path, Map.of("secret", secret));

                return Response.ok()
                        .entity(String.format(
                                "Created secret on path: %s, key is \"secret\", original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @DELETE
            @Path("/secrets/{path: .*}")
            public Response deleteSecret(@PathParam("path") String path) { // <4>
                DeleteKv1.Response response = secrets.delete(path);

                return Response.ok()
                        .entity(String.format(
                                "Deleted secret on path: %s. Original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @GET
            @Path("/secrets/{path: .*}")
            public Response getSecret(@PathParam("path") String path) { // <5>
                Optional<Secret> secret = secrets.get(path);

                if (secret.isPresent()) {
                    Secret kv1Secret = secret.get();
                    return Response.ok()
                            .entity("Secret: " + secret.get().values().toString())
                            .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Path("/kv2")
        public class Kv2Resource {
            private final Kv2Secrets secrets;

            @Inject
            Kv2Resource(@VaultName("app-role")
                        @VaultPath("custom") Kv2Secrets secrets) {
                this.secrets = secrets;
            }

            @POST
            @Path("/secrets/{path: .*}")
            public Response createSecret(@PathParam("path") String path, String secret) { // <1>
                CreateKv2.Response response = secrets.create(path, Map.of("secret", secret));
                return Response.ok()
                        .entity(String.format(
                                "Created secret on path: %s, key is \"secret\", original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @DELETE
            @Path("/secrets/{path: .*}")
            public Response deleteSecret(@PathParam("path") String path) { // <2>
                DeleteAllKv2.Response response = secrets.deleteAll(path);
                return Response.ok()
                        .entity(String.format(
                                "Deleted secret on path: %s. Original status: %d",
                                path,
                                response.status().code()))
                        .build();
            }

            @GET
            @Path("/secrets/{path: .*}")
            public Response getSecret(@PathParam("path") String path) { // <3>

                Optional<Kv2Secret> secret = secrets.get(path);

                if (secret.isPresent()) {
                    Kv2Secret kv2Secret = secret.get();
                    return Response.ok()
                            .entity(String.format(
                                    "Version %s, secret: %s",
                                    kv2Secret.metadata().version(),
                                    kv2Secret.values()))
                            .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Path("/transit")
        public class TransitResource {
            private static final String ENCRYPTION_KEY = "encryption-key";
            private static final String SIGNATURE_KEY = "signature-key";

            private final Sys sys;
            private final TransitSecrets secrets;

            @Inject
            TransitResource(Sys sys, TransitSecrets secrets) {
                this.sys = sys;
                this.secrets = secrets;
            }

            @Path("/engine")
            @GET
            public Response enableEngine() { // <1>
                EnableEngine.Response response = sys.enableEngine(TransitSecrets.ENGINE);

                return Response.ok()
                        .entity("Transit secret engine is now enabled."
                                + " Original status: " + response.status().code())
                        .build();
            }

            @Path("/engine")
            @DELETE
            public Response disableEngine() { // <2>
                DisableEngine.Response response = sys.disableEngine(TransitSecrets.ENGINE);
                return Response.ok()
                        .entity("Transit secret engine is now disabled."
                                + " Original status: " + response.status())
                        .build();
            }

            @Path("/keys")
            @GET
            public Response createKeys() { // <3>
                secrets.createKey(CreateKey.Request.builder()
                                          .name(ENCRYPTION_KEY));

                secrets.createKey(CreateKey.Request.builder()
                                          .name(SIGNATURE_KEY)
                                          .type("rsa-2048"));

                return Response.ok()
                        .entity("Created encryption (and HMAC), and signature keys")
                        .build();
            }

            @Path("/keys")
            @DELETE
            public Response deleteKeys() { // <4>
                // we must first enable deletion of the key (by default it cannot be deleted)
                secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                                .name(ENCRYPTION_KEY)
                                                .allowDeletion(true));

                secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                                .name(SIGNATURE_KEY)
                                                .allowDeletion(true));

                secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY));
                secrets.deleteKey(DeleteKey.Request.create(SIGNATURE_KEY));

                return Response.ok()
                        .entity("Deleted encryption (and HMAC), and signature keys")
                        .build();
            }

            @Path("/encrypt/{secret: .*}")
            @GET
            public String encryptSecret(@PathParam("secret") String secret) { // <5>
                return secrets.encrypt(Encrypt.Request.builder()
                                               .encryptionKeyName(ENCRYPTION_KEY)
                                               .data(Base64Value.create(secret)))
                        .encrypted()
                        .cipherText();
            }

            @Path("/decrypt/{cipherText: .*}")
            @GET
            public String decryptSecret(@PathParam("cipherText") String cipherText) { // <6>
                return secrets.decrypt(Decrypt.Request.builder()
                                               .encryptionKeyName(ENCRYPTION_KEY)
                                               .cipherText(cipherText))
                        .decrypted()
                        .toDecodedString();
            }

            @Path("/hmac/{text}")
            @GET
            public String hmac(@PathParam("text") String text) { // <7>
                return secrets.hmac(Hmac.Request.builder()
                                            .hmacKeyName(ENCRYPTION_KEY)
                                            .data(Base64Value.create(text)))
                        .hmac();
            }

            @Path("/sign/{text}")
            @GET
            public String sign(@PathParam("text") String text) { // <8>
                return secrets.sign(Sign.Request.builder()
                                            .signatureKeyName(SIGNATURE_KEY)
                                            .data(Base64Value.create(text)))
                        .signature();
            }

            @Path("/verify/hmac/{secret}/{hmac: .*}")
            @GET
            public String verifyHmac(@PathParam("secret") String secret,
                                     @PathParam("hmac") String hmac) { // <9>
                boolean isValid = secrets.verify(Verify.Request.builder()
                                                         .digestKeyName(ENCRYPTION_KEY)
                                                         .data(Base64Value.create(secret))
                                                         .hmac(hmac))
                        .isValid();

                return (isValid ? "HMAC Valid" : "HMAC Invalid");
            }

            @Path("/verify/sign/{secret}/{signature: .*}")
            @GET
            public String verifySignature(@PathParam("secret") String secret,
                                          @PathParam("signature") String signature) { // <10>
                boolean isValid = secrets.verify(Verify.Request.builder()
                                                         .digestKeyName(SIGNATURE_KEY)
                                                         .data(Base64Value.create(secret))
                                                         .signature(signature))
                        .isValid();

                return (isValid ? "Signature Valid" : "Signature Invalid");
            }
        }
        // end::snippet_5[]
    }

}
