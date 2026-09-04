/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.quic.interop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemReader;
import io.helidon.common.tls.Tls;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.staticcontent.FileSystemHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;

/**
 * Runnable HTTP/3 server harness for QUIC interop work.
 */
@Api.Internal
public class Main {
    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());
    private static final Pattern EC_PARAMETERS_BLOCK = Pattern.compile(
            "\\A-----BEGIN EC PARAMETERS-----\\R.*?\\R-----END EC PARAMETERS-----\\R*",
            Pattern.DOTALL);
    private static final Pattern EC_PRIVATE_KEY_BLOCK = Pattern.compile(
            "-----BEGIN EC PRIVATE KEY-----\\R(.*?)\\R-----END EC PRIVATE KEY-----",
            Pattern.DOTALL);
    private static final byte[] PKCS8_EC_P256_ALGORITHM_IDENTIFIER = {
            0x30, 0x13, 0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01,
            0x06, 0x08, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07
    };

    private static final String ENV_HOST = "HELIDON_QUIC_INTEROP_HOST";
    private static final String ENV_PORT = "HELIDON_QUIC_INTEROP_PORT";
    private static final String ENV_CERT_CHAIN = "HELIDON_QUIC_INTEROP_CERT_CHAIN";
    private static final String ENV_PRIVATE_KEY = "HELIDON_QUIC_INTEROP_PRIVATE_KEY";
    private static final String ENV_PRIVATE_KEY_PASSPHRASE = "HELIDON_QUIC_INTEROP_PRIVATE_KEY_PASSPHRASE";
    private static final String ENV_WEB_ROOT = "HELIDON_QUIC_INTEROP_WEB_ROOT";
    private static final String ENV_WELCOME_FILE = "HELIDON_QUIC_INTEROP_WELCOME_FILE";

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 443;
    private static final String DEFAULT_CERT_CHAIN = "/certs/cert.pem";
    private static final String DEFAULT_PRIVATE_KEY = "/certs/priv.key";
    private static final String DEFAULT_WEB_ROOT = "/www";
    private static final String DEFAULT_WELCOME_FILE = "index.html";

    private Main() {
    }

    /**
     * Start the interop server and keep the process alive until shutdown.
     *
     * @param args ignored
     * @throws InterruptedException when interrupted while waiting for shutdown
     */
    public static void main(String[] args) throws InterruptedException {
        LogConfig.configureRuntime();

        InteropConfig config = interopConfig(System.getenv());
        WebServer server = startServer(config);

        LOGGER.log(System.Logger.Level.INFO,
                   () -> "Helidon HTTP/3 interop server listening on "
                           + config.host() + ":" + server.port()
                           + ", serving " + config.webRoot());

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } finally {
                shutdown.countDown();
            }
        }, "helidon-quic-interop-shutdown"));
        shutdown.await();
    }

    static InteropConfig interopConfig(Map<String, String> environment) {
        String host = value(environment, ENV_HOST, DEFAULT_HOST);
        int port = Integer.parseInt(value(environment, ENV_PORT, Integer.toString(DEFAULT_PORT)));
        Path certChain = Path.of(value(environment, ENV_CERT_CHAIN, DEFAULT_CERT_CHAIN)).toAbsolutePath().normalize();
        Path privateKey = Path.of(value(environment, ENV_PRIVATE_KEY, DEFAULT_PRIVATE_KEY)).toAbsolutePath().normalize();
        Path webRoot = Path.of(value(environment, ENV_WEB_ROOT, DEFAULT_WEB_ROOT)).toAbsolutePath().normalize();
        String welcomeFile = value(environment, ENV_WELCOME_FILE, DEFAULT_WELCOME_FILE);

        return new InteropConfig(host,
                                 port,
                                 certChain,
                                 privateKey,
                                 optionalValue(environment, ENV_PRIVATE_KEY_PASSPHRASE),
                                 webRoot,
                                 welcomeFile);
    }

    static WebServer startServer(InteropConfig config) {
        validate(config);
        Tls tls = tls(config);

        WebServer server = WebServer.builder()
                .host(config.host())
                .port(config.port())
                .protocolsDiscoverServices(false)
                .tls(tls)
                .addProtocol(Http3Config.create())
                .routing(routing -> routing.post("/echo", (req, res) -> res.send(req.content().as(String.class)))
                        .register("/",
                                  StaticContentFeature.createService(FileSystemHandlerConfig.builder()
                                          .location(config.webRoot())
                                          .welcome(config.welcomeFile())
                                          .build())))
                .build()
                .start();

        if (!server.isRunning() || server.port() < 0) {
            server.stop();
            throw new IllegalStateException("Failed to start the Helidon HTTP/3 interop server.");
        }

        return server;
    }

    static String normalizePrivateKeyPem(String pemContent) {
        return EC_PARAMETERS_BLOCK.matcher(pemContent).replaceFirst("");
    }

    private static void validate(InteropConfig config) {
        requireReadable(config.certChain(), ENV_CERT_CHAIN);
        requireReadable(config.privateKey(), ENV_PRIVATE_KEY);

        if (!Files.isDirectory(config.webRoot())) {
            throw new IllegalArgumentException(ENV_WEB_ROOT + " must point to an existing directory: " + config.webRoot());
        }
    }

    private static void requireReadable(Path path, String envName) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(envName + " must point to a readable file: " + path);
        }
    }

    private static Tls tls(InteropConfig config) {
        String pemContent = readPrivateKeyPem(config.privateKey());
        String normalized = normalizePrivateKeyPem(pemContent);
        if (normalized.startsWith("-----BEGIN EC PRIVATE KEY-----")) {
            PrivateKey privateKey = loadPkcs1EcPrivateKey(normalized, config.privateKey());
            List<X509Certificate> certChain = PemReader.readCertificates(Resource.create(config.certChain()).stream());

            return Tls.builder()
                    .privateKey(privateKey)
                    .privateKeyCertChain(certChain)
                    .enabledProtocols(List.of("TLSv1.3"))
                    .build();
        }

        Path privateKey = normalizedPrivateKey(config.privateKey(), pemContent, normalized);
        Keys keys = Keys.builder()
                .pem(pem -> {
                    pem.certChain(Resource.create(config.certChain()));
                    pem.key(Resource.create(privateKey));
                    config.privateKeyPassphrase().ifPresent(passphrase -> pem.keyPassphrase(passphrase.toCharArray()));
                })
                .build();

        return Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
    }

    private static String readPrivateKeyPem(Path privateKey) {
        String pemContent;
        try {
            pemContent = Files.readString(privateKey, StandardCharsets.US_ASCII);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read private key: " + privateKey, e);
        }
        return pemContent;
    }

    private static Path normalizedPrivateKey(Path privateKey, String pemContent, String normalized) {
        if (normalized.equals(pemContent)) {
            return privateKey;
        }

        try {
            Path normalizedPrivateKey = Files.createTempFile("helidon-quic-interop-key", ".pem");
            Files.writeString(normalizedPrivateKey, normalized, StandardCharsets.US_ASCII);
            normalizedPrivateKey.toFile().deleteOnExit();
            return normalizedPrivateKey;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to normalize private key: " + privateKey, e);
        }
    }

    private static PrivateKey loadPkcs1EcPrivateKey(String pemContent, Path privateKey) {
        Matcher matcher = EC_PRIVATE_KEY_BLOCK.matcher(pemContent);
        if (!matcher.find()) {
            throw new IllegalStateException("Failed to locate EC private key block in: " + privateKey);
        }

        byte[] sec1 = Base64.getMimeDecoder().decode(matcher.group(1));
        byte[] pkcs8 = derSequence(derIntegerZero(),
                                   PKCS8_EC_P256_ALGORITHM_IDENTIFIER,
                                   derOctetString(sec1));
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert EC private key to PKCS#8: " + privateKey, e);
        }
    }

    private static byte[] derIntegerZero() {
        return new byte[]{0x02, 0x01, 0x00};
    }

    private static byte[] derOctetString(byte[] value) {
        return der((byte) 0x04, value);
    }

    private static byte[] derSequence(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            size += value.length;
        }
        BufferData content = BufferData.create(size);
        for (byte[] value : values) {
            content.write(value);
        }
        return der((byte) 0x30, content.readBytes());
    }

    private static byte[] der(byte tag, byte[] value) {
        byte[] length = derLength(value.length);
        byte[] result = new byte[1 + length.length + value.length];
        result[0] = tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(value, 0, result, 1 + length.length, value.length);
        return result;
    }

    private static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }

        byte[] encoded = new byte[Integer.BYTES];
        int copyFrom = encoded.length;
        int remaining = length;
        while (remaining > 0) {
            encoded[--copyFrom] = (byte) (remaining & 0xFF);
            remaining >>>= 8;
        }

        int size = encoded.length - copyFrom;
        byte[] result = new byte[1 + size];
        result[0] = (byte) (0x80 | size);
        System.arraycopy(encoded, copyFrom, result, 1, size);
        return result;
    }

    private static String value(Map<String, String> environment, String name, String defaultValue) {
        return Optional.ofNullable(environment.get(name))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .orElse(defaultValue);
    }

    private static Optional<String> optionalValue(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name))
                .map(String::trim)
                .filter(it -> !it.isEmpty());
    }

    record InteropConfig(String host,
                         int port,
                         Path certChain,
                         Path privateKey,
                         Optional<String> privateKeyPassphrase,
                         Path webRoot,
                         String welcomeFile) {
    }
}
