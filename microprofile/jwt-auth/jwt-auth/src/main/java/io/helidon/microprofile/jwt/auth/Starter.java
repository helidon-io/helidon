/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.jwt.auth;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.inject.spi.DeploymentException;
import javax.json.*;

import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

/**
 * TODO javadoc.
 */
public class Starter {
    public static final String CONFIG_PUBLIC_KEY = "mp.jwt.verify.publickey";
    public static final String CONFIG_PUBLIC_KEY_PATH = "mp.jwt.verify.publickey.location";
    public static final String CONFIG_ISSUER = "mp.jwt.verify.issuer";

    public static final String JSON_START_MARK = "{";

    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n\\s]+)" +                       // Base64 text
                    "-+END\\s+.*PUBLIC\\s+KEY[^-]*-+",            // Footer
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        Config config = Config.create();
        if (config.get(CONFIG_PUBLIC_KEY_PATH).hasValue()
                && config.get(CONFIG_PUBLIC_KEY).hasValue()) {
            throw new DeploymentException("Both " + CONFIG_PUBLIC_KEY + " and " + CONFIG_PUBLIC_KEY_PATH + " are set! Only one of them should be picked.");
        }
        Optional<PublicKey> key = OptionalHelper
                .from(config.get(CONFIG_PUBLIC_KEY_PATH)
                        .value()
                        .map(Starter::loadPkcs8FromLocation))
                .or(() -> config.get(CONFIG_PUBLIC_KEY)
                        .value()
                        .map(Starter::loadPkcs8))
                .asOptional();
        System.out.println(key.get());
    }

    private static PublicKey loadPkcs8FromLocation(String uri) {
        try {
            Path path = Paths.get(uri);
            if (Files.exists(path)) {
                try (InputStream bufferedInputStream = Files.newInputStream(path)) {
                    return getPublicKeyFromContent(bufferedInputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (InvalidPathException ignored) {
            //invalid path
        }
        URL url = Thread.currentThread().getContextClassLoader().getResource(uri);
        if (url != null) {
            try (InputStream bufferedInputStream = url.openStream()) {
                return getPublicKeyFromContent(bufferedInputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            URL targetURL = new URL(uri);
            return getPublicKeyFromContent(targetURL.openStream());
        } catch (MalformedURLException ignored) {
            //ignored not and valid URL
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static PublicKey getPublicKeyFromContent(InputStream bufferedInputStream) throws IOException {
        byte[] contents = new byte[1024];
        int bytesRead;
        StringBuilder sb = new StringBuilder();
        while ((bytesRead = bufferedInputStream.read(contents)) != -1) {
            sb.append(new String(contents, 0, bytesRead));
        }
        return loadPkcs8(sb.toString());
    }

    private static PublicKey loadPkcs8(String stringContent) {
        Matcher m = PUBLIC_KEY_PATTERN.matcher(stringContent);
        if (m.find()) {
            return loadPlainPublicKey(stringContent);
        } else if (stringContent.startsWith(JSON_START_MARK)) {
            return loadPublicKeyJWK(stringContent);
        } else {
            return loadPublicKeyJWKBase64(stringContent);
        }
    }

    private static PublicKey loadPlainPublicKey(String stringContent) {
        return KeyConfig.pemBuilder()
                .publicKey(Resource.fromContent("public key from PKCS8", stringContent))
                .build()
                .getPublicKey()
                .orElseThrow(() -> new DeploymentException("Failed to load public key from string content"));
    }

    private static PublicKey loadPublicKeyJWKBase64(String base64Encoded) {
        return loadPublicKeyJWK(new String(Base64.getUrlDecoder().decode(base64Encoded)));
    }

    private static PublicKey loadPublicKeyJWK(String jwkJson) {
        if (jwkJson.contains("keys")) {
            return JwkKeys.builder()
                    .resource(Resource.fromContent("public key from PKCS8", jwkJson))
                    .build()
                    .forKeyId("orange-5678")//TODO needs to be changed (KID from header should be here)
                    .map(jwk -> (JwkRSA) jwk)
                    .get()
                    .getPublicKey();
        }
        JsonObject jsonObject = Json.createReader(new StringReader(jwkJson)).readObject();
        /*if (jsonObject.get("kid") == null) {
            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
            jsonObject.entrySet().stream()
                    .forEach(entry -> jsonObjectBuilder.add(entry.getKey(), entry.getValue()));
            jsonObjectBuilder.add("kid", "key");
            jsonObject = jsonObjectBuilder.build();
        }*/
        JwkRSA jwk = (JwkRSA) Jwk.fromJson(jsonObject);
        return jwk.getPublicKey();
    }
}
