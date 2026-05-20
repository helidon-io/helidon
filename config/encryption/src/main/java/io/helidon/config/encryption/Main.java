/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;

/**
 * Cli access to secret encryption.
 */
public final class Main {
    private Main() {
        throw new IllegalStateException("Main class");
    }

    /**
     * Expected parameters: type [enc|aes|gcm|rsa] encryptionConfig [masterPassword|pathToRsaPublicKey] secretToEncrypt.
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        EncryptionCliProcessor cli = new EncryptionCliProcessor();
        try {
            cli.parse(args);

            System.out.println(cli.encrypt());
        } catch (Exception e) {
            System.err.println("Failed to process input.");
            help();
            throw e;
        }
    }

    private static void help() {
        System.out.println("To encrypt password using master password to be used in a property file:");
        System.out.println("java -jar <path-to-app-libs-dir>/helidon-config-encryption-{helidon-version}.jar "
                                   + "enc masterPassword secretToEncrypt [iterations]");
        System.out.println("Optional iterations must be between "
                                   + EncryptionUtil.ENVELOPE_MIN_HASH_ITERATIONS + " and "
                                   + EncryptionUtil.ENVELOPE_MAX_HASH_ITERATIONS + "; default is "
                                   + EncryptionUtil.ENVELOPE_HASH_ITERATIONS + ".");
        System.out.println("To encrypt password using legacy formats:");
        System.out.println("java -jar <path-to-app-libs-dir>/helidon-config-encryption-{helidon-version}.jar "
                                   + "aes masterPassword secretToEncrypt");
        System.out.println("java -jar <path-to-app-libs-dir>/helidon-config-encryption-{helidon-version}.jar "
                                   + "gcm masterPassword secretToEncrypt");
        System.out.println();
        System.out.println("To encrypt password using public key to be used in a property file:");
        System.out.println("java -jar <path-to-app-libs-dir>/helidon-config-encryption-{helidon-version}.jar "
               + "rsa /path/to/pkcs12keystore keystorePassphrase publicCertAlias secretToEncrypt");
    }

    enum Algorithm {
        aes,
        enc,
        gcm,
        rsa
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }

    static class EncryptionCliProcessor {
        private Algorithm algorithm;
        private String secret;
        private PublicKey publicKey;
        private String masterPassword;
        private int aesIterations = EncryptionUtil.ENVELOPE_HASH_ITERATIONS;

        EncryptionCliProcessor() {
        }

        void parse(String[] cliArgs) {
            if (cliArgs.length < 2) {
                help();
                throw new ValidationException("Program must have two or more arguments");
            }
            String algorithm = cliArgs[0];

            if ("enc".equals(algorithm)) {
                parseAes(cliArgs, Algorithm.enc);
            } else if ("aes".equals(algorithm)) {
                parseAes(cliArgs, Algorithm.aes);
            } else if ("gcm".equals(algorithm)) {
                parseAes(cliArgs, Algorithm.gcm);
            } else if ("rsa".equals(algorithm)) {
                parseRsa(cliArgs);
            } else {
                throw new ValidationException("First argument must be a valid algorithm (rsa, enc, aes, or gcm)");
            }
        }

        private void parseRsa(String[] cliArgs) {
            this.algorithm = Algorithm.rsa;
            // /path/to/pkcs12keystore keystorePassphrase "
            //        + "publicCertAlias secretToEncrypt
            /*
            0: algorithm
            1: path to keystore
            2: keystore passphrase
            3: public certificate alias (to extract public key)
            4: secret to encrypt (optional)
            */
            if (cliArgs.length < 4) {
                throw new ValidationException(
                        "RSA encryption must have at least three parameters: keystorePath, keystorePassword and alias of "
                                + "certificate for public key");
            }
            if (cliArgs.length == 4) {
                secret = "";
            } else {
                secret = cliArgs[4];
            }
            Path keyPath = Paths.get(cliArgs[1]);
            if (!Files.exists(keyPath) || !Files.isRegularFile(keyPath)) {
                throw new ValidationException(
                        "For rsa encryption the second parameter must be a keystore path, "
                                + "yet it is not accessible as a file: "
                                + keyPath.toAbsolutePath());
            }
            String certAlias = cliArgs[3];
            KeyConfig kc = KeyConfig.keystoreBuilder()
                    .keystore(Resource.create(keyPath))
                    .keystorePassphrase(cliArgs[2].toCharArray())
                    .certAlias(certAlias)
                    .build();

            publicKey = kc.publicKey()
                    .orElseThrow(() -> new ValidationException("There is no public key available for cert alias: " + certAlias));
        }

        private void parseAes(String[] cliArgs, Algorithm algorithm) {
            String config = cliArgs[1];
            if (cliArgs.length == 2) {
                this.secret = "";
            } else {
                this.secret = cliArgs[2];
            }
            if (algorithm == Algorithm.enc && cliArgs.length > 3) {
                try {
                    this.aesIterations = Integer.parseInt(cliArgs[3]);
                    EncryptionUtil.validateEnvelopeIterations(aesIterations);
                } catch (NumberFormatException e) {
                    throw new ValidationException("AES iterations must be a valid integer");
                } catch (ConfigEncryptionException e) {
                    throw new ValidationException(e.getMessage());
                }
            }

            this.algorithm = algorithm;
            this.masterPassword = config;
        }

        String encrypt() {
            switch (algorithm) {
            case enc:
                return enc();
            case aes:
            case gcm:
                return gcm();
            case rsa:
                return rsa();
            default:
                return secret;
            }
        }

        String rsa() {
            return EncryptionFilter.PREFIX_RSA + EncryptionUtil.encryptRsa(publicKey, secret) + '}';
        }

        String enc() {
            return EncryptionFilter.PREFIX_ENC
                    + EncryptionUtil.encryptAesEnvelope(masterPassword.toCharArray(), secret, aesIterations)
                    + '}';
        }

        String gcm() {
            return EncryptionFilter.PREFIX_GCM + EncryptionUtil.encryptAes(masterPassword.toCharArray(), secret) + '}';
        }

        Algorithm getAlgorithm() {
            return algorithm;
        }

        String getMasterPassword() {
            return masterPassword;
        }

        int getAesIterations() {
            return aesIterations;
        }

        PublicKey getPublicKey() {
            return publicKey;
        }

        String getSecret() {
            return secret;
        }
    }
}
