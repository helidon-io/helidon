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

package io.helidon.examples.integrations.vault.hcp.cdi;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.secrets.transit.CreateKey;
import io.helidon.integrations.vault.secrets.transit.Decrypt;
import io.helidon.integrations.vault.secrets.transit.DeleteKey;
import io.helidon.integrations.vault.secrets.transit.Encrypt;
import io.helidon.integrations.vault.secrets.transit.Hmac;
import io.helidon.integrations.vault.secrets.transit.Sign;
import io.helidon.integrations.vault.secrets.transit.TransitSecrets;
import io.helidon.integrations.vault.secrets.transit.TransitSecretsRx;
import io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig;
import io.helidon.integrations.vault.secrets.transit.Verify;
import io.helidon.integrations.vault.sys.DisableEngine;
import io.helidon.integrations.vault.sys.EnableEngine;
import io.helidon.integrations.vault.sys.Sys;

/**
 * JAX-RS resource for Transit secrets engine operations.
 */
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

    /**
     * Enable the secrets engine on the default path.
     *
     * @return response
     */
    @Path("/engine")
    @GET
    public Response enableEngine() {
        EnableEngine.Response response = sys.enableEngine(TransitSecretsRx.ENGINE);

        return Response.ok()
                .entity("Transit secret engine is now enabled. Original status: " + response.status().code())
                .build();
    }

    /**
     * Disable the secrets engine on the default path.
     * @return response
     */
    @Path("/engine")
    @DELETE
    public Response disableEngine() {
        DisableEngine.Response response = sys.disableEngine(TransitSecretsRx.ENGINE);

        return Response.ok()
                .entity("Transit secret engine is now disabled. Original status: " + response.status())
                .build();
    }

    /**
     * Create the encrypting and signature keys.
     *
     * @return response
     */
    @Path("/keys")
    @GET
    public Response createKeys() {
        secrets.createKey(CreateKey.Request.builder()
                                  .name(ENCRYPTION_KEY));

        secrets.createKey(CreateKey.Request.builder()
                                  .name(SIGNATURE_KEY)
                                  .type("rsa-2048"));

        return Response.ok()
                .entity("Created encryption (and HMAC), and signature keys")
                .build();
    }

    /**
     * Delete the encryption and signature keys.
     *
     * @return response
     */
    @Path("/keys")
    @DELETE
    public Response deleteKeys() {
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

    /**
     * Encrypt a secret.
     *
     * @param secret provided as part of the path
     * @return cipher text
     */
    @Path("/encrypt/{secret: .*}")
    @GET
    public String encryptSecret(@PathParam("secret") String secret) {
        return secrets.encrypt(Encrypt.Request.builder()
                                       .encryptionKeyName(ENCRYPTION_KEY)
                                       .data(Base64Value.create(secret)))
                .encrypted()
                .cipherText();
    }

    /**
     * Decrypt a secret.
     *
     * @param cipherText provided as part of the path
     * @return decrypted secret text
     */
    @Path("/decrypt/{cipherText: .*}")
    @GET
    public String decryptSecret(@PathParam("cipherText") String cipherText) {
        return secrets.decrypt(Decrypt.Request.builder()
                                       .encryptionKeyName(ENCRYPTION_KEY)
                                       .cipherText(cipherText))
                .decrypted()
                .toDecodedString();
    }

    /**
     * Create an HMAC for text.
     *
     * @param text text to do HMAC for
     * @return hmac string that can be used to {@link #verifyHmac(String, String)}
     */
    @Path("/hmac/{text}")
    @GET
    public String hmac(@PathParam("text") String text) {
        return secrets.hmac(Hmac.Request.builder()
                                    .hmacKeyName(ENCRYPTION_KEY)
                                    .data(Base64Value.create(text)))
                .hmac();
    }

    /**
     * Create a signature for text.
     *
     * @param text text to sign
     * @return signature string that can be used to {@link #verifySignature(String, String)}
     */
    @Path("/sign/{text}")
    @GET
    public String sign(@PathParam("text") String text) {
        return secrets.sign(Sign.Request.builder()
                                    .signatureKeyName(SIGNATURE_KEY)
                                    .data(Base64Value.create(text)))
                .signature();
    }

    /**
     * Verify HMAC.
     *
     * @param secret secret that was used to {@link #hmac(String)}
     * @param hmac HMAC text
     * @return {@code HMAC Valid} or {@code HMAC Invalid}
     */
    @Path("/verify/hmac/{secret}/{hmac: .*}")
    @GET
    public String verifyHmac(@PathParam("secret") String secret, @PathParam("hmac") String hmac) {
        boolean isValid = secrets.verify(Verify.Request.builder()
                                                 .digestKeyName(ENCRYPTION_KEY)
                                                 .data(Base64Value.create(secret))
                                                 .hmac(hmac))
                .isValid();

        return (isValid ? "HMAC Valid" : "HMAC Invalid");
    }

    /**
     * Verify signature.
     *
     * @param secret secret that was used to {@link #sign(String)}
     * @param signature signature
     * @return {@code Signature Valid} or {@code Signature Invalid}
     */
    @Path("/verify/sign/{secret}/{signature: .*}")
    @GET
    public String verifySignature(@PathParam("secret") String secret, @PathParam("signature") String signature) {
        boolean isValid = secrets.verify(Verify.Request.builder()
                                                 .digestKeyName(SIGNATURE_KEY)
                                                 .data(Base64Value.create(secret))
                                                 .signature(signature))
                .isValid();

        return (isValid ? "Signature Valid" : "Signature Invalid");
    }
}
