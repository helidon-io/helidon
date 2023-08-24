/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.net.URI;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.integrations.oci.sdk.runtime.OciExtension;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;

import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.model.ExportKeyDetails;
import com.oracle.bmc.keymanagement.requests.ExportKeyRequest;
import com.oracle.bmc.keymanagement.responses.ExportKeyResponse;
import jakarta.inject.Singleton;

/**
 * Implementation of the {@link OciPrivateKeyDownloader} that will use OCI's KMS to export a key.
 */
@Singleton
class DefaultOciPrivateKeyDownloader implements OciPrivateKeyDownloader {
    private final PrivateKey wrappingPrivateKey;
    private final String wrappingPublicKeyPem;

    DefaultOciPrivateKeyDownloader() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair wrappingKeyPair = generator.generateKeyPair();
            this.wrappingPrivateKey = wrappingKeyPair.getPrivate();
            PublicKey wrappingPublicKey = wrappingKeyPair.getPublic();
            String pubBase64 = Base64.getEncoder().encodeToString(wrappingPublicKey.getEncoded());
            this.wrappingPublicKeyPem = "-----BEGIN PUBLIC KEY-----" + pubBase64 + "-----END PUBLIC KEY-----";
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PrivateKey loadKey(String keyOcid,
                              URI vaultCryptoEndpoint) {
        try (KmsCryptoClient client = KmsCryptoClient.builder()
                .endpoint(vaultCryptoEndpoint.toString())
                .build(OciExtension.ociAuthenticationProvider().get())) {
            ExportKeyResponse exportKeyResponse =
                    client.exportKey(ExportKeyRequest.builder()
                                             .exportKeyDetails(ExportKeyDetails.builder()
                                                                       .keyId(keyOcid)
                                                                       .publicKey(wrappingPublicKeyPem)
                                                                       .algorithm(ExportKeyDetails.Algorithm.RsaOaepAesSha256)
                                                                       .build())
                                             .build());
            String encryptedKey = exportKeyResponse.getExportedKeyData().getEncryptedKey();
            return decode(encryptedKey);
        } catch (Exception e) {
            throw new RuntimeException("Problem encountered loading key: " + keyOcid + " from: " + vaultCryptoEndpoint, e);
        }
    }

    PrivateKey decode(String encryptedKey) throws Exception {
        byte[] encryptedMaterial = Base64.getDecoder().decode(encryptedKey);

        //rfc3394 - first 256 bytes is tmp AES key encrypted by our temp wrapping RSA
        byte[] tmpAes = decryptAesKey(Arrays.copyOf(encryptedMaterial, 256));

        //rfc3394 - rest of the bytes is secret key wrapped by tmp AES
        byte[] wrappedSecretKey = Arrays.copyOfRange(encryptedMaterial, 256, encryptedMaterial.length);

        // Unwrap with decrypted tmp AES
        return (PrivateKey) unwrapRSA(wrappedSecretKey, tmpAes);
    }

    Key unwrapRSA(byte[] in, byte[] keyBytes) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher c = Cipher.getInstance("AESWrapPad");
        c.init(Cipher.UNWRAP_MODE, key);
        return c.unwrap(in, "RSA", Cipher.PRIVATE_KEY);
    }

    byte[] decryptAesKey(byte[] in) throws Exception {
        // OCI uses BC
        //https://stackoverflow.com/a/23859386/626826
        //https://bugs.openjdk.org/browse/JDK-7038158
        Cipher decrypt = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING", "BC");
        decrypt.init(Cipher.DECRYPT_MODE, wrappingPrivateKey);
        return decrypt.doFinal(in);
    }

}
