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
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Prioritized;
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
public class DefaultOciPrivateKeyDownloader implements OciPrivateKeyDownloader, Prioritized {
    private final PrivateKey wrappingPrivateKey;
    private final String wrappingPublicKeyPem;

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public DefaultOciPrivateKeyDownloader() {
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
        Objects.requireNonNull(keyOcid);
        Objects.requireNonNull(vaultCryptoEndpoint);
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
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Problem encountered loading key: " + keyOcid + " from: " + vaultCryptoEndpoint, e);
        }
    }

    PrivateKey decode(String encryptedKey) throws GeneralSecurityException {
        byte[] encryptedMaterial = Base64.getDecoder().decode(encryptedKey);

        // rfc3394 - first 256 bytes is tmp AES key encrypted by our temp wrapping RSA
        byte[] tmpAes = decryptAesKey(Arrays.copyOf(encryptedMaterial, 256));

        // rfc3394 - rest of the bytes is secret key wrapped by tmp AES
        byte[] wrappedSecretKey = Arrays.copyOfRange(encryptedMaterial, 256, encryptedMaterial.length);

        // unwrap with decrypted tmp AES
        return (PrivateKey) unwrapRSA(wrappedSecretKey, tmpAes);
    }

    Key unwrapRSA(byte[] in,
                  byte[] keyBytes) throws GeneralSecurityException {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        // https://docs.oracle.com/en/java/javase/20/docs/specs/security/standard-names.html
        Cipher c = Cipher.getInstance("AESWrapPad");
        c.init(Cipher.UNWRAP_MODE, key);
        return c.unwrap(in, "RSA", Cipher.PRIVATE_KEY);
    }

    byte[] decryptAesKey(byte[] in) throws GeneralSecurityException {
        Cipher decrypt = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256andMGF1Padding");

        // https://stackoverflow.com/questions/61665435/encryption-in-java-with-rsa-ecb-oaepwithsha-256andmgf1padding-could-not-be-decry
        // decrypt.init(Cipher.DECRYPT_MODE, wrappingPrivateKey);
        decrypt.init(Cipher.DECRYPT_MODE, wrappingPrivateKey,
                     new OAEPParameterSpec("SHA-256", "MGF1",
                                           new MGF1ParameterSpec("SHA-256"),
                                           PSource.PSpecified.DEFAULT));

        return decrypt.doFinal(in);
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }
}
