package io.helidon.integrations.oci.connect;

import java.security.interfaces.RSAPrivateKey;

public interface OciSignatureData {
    String keyId();
    RSAPrivateKey privateKey();

    static OciSignatureData create(String keyId, RSAPrivateKey privateKey) {
        return new OciSignatureData() {
            @Override
            public String keyId() {
                return keyId;
            }

            @Override
            public RSAPrivateKey privateKey() {
                return privateKey;
            }

            @Override
            public String toString() {
                return keyId;
            }
        };
    }
}
