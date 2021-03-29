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

package io.helidon.integrations.oci.connect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

/**
 * OCI configuration required to connect to a service over REST API.
 */
public class OciConfigProfile implements OciConfigProvider {
    private final String userOcid;
    private final String tenancyOcid;
    private final String keyFingerprint;
    private final String region;
    private final String privateKey;
    private final Map<String, String> fullConfig;
    private final OciSignatureData signatureData;

    private OciConfigProfile(Builder builder) {
        this.userOcid = builder.userOcid;
        this.tenancyOcid = builder.tenancyOcid;
        this.keyFingerprint = builder.keyFingerprint;
        this.region = builder.region;
        this.privateKey = builder.privateKey;
        this.fullConfig = Map.copyOf(builder.fullConfig);
        this.signatureData = builder.signatureData;
    }

    /**
     * Create configuration from the default location {@code ~/.oci/config} and
     * default profile {@code DEFAULT}.
     *
     * @return a new configuration loaded from default location
     */
    public static OciConfigProfile create() {
        return builder().fromOciConfig().build();
    }

    /**
     * A new fluent API builder to configure a new profile config.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration
     * @return a new profile configuration
     */
    public static OciConfigProfile create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public OciSignatureData signatureData() {
        return signatureData;
    }

    /**
     * OCID of the user. Uses key {@code user} in OCI config file and in config.
     *
     * @return user OCID
     */
    public String userOcid() {
        return userOcid;
    }


    /**
     * OCID of the tenancy. Uses key {@code tenancy} in OCI config file and in config.
     *
     * @return tenancy OCID
     */
    @Override
    public String tenancyOcid() {
        return tenancyOcid;
    }

    /**
     * Fingerprint of the key.
     * Uses key {@code fingerprint} in OCI config file and in config.
     *
     * @return key fingerprint
     */
    public String keyFingerprint() {
        return keyFingerprint;
    }

    /**
     * Region to use when connecting to OCI. This is used to resolve the host to connect to.
     * Uses key {@code region} in OCI config file and in config.
     *
     * @return OCI region, such as {@code eu-frankfurt-1}
     */
    @Override
    public String region() {
        return region;
    }

    /**
     * Private key (PEM encoded) used to sign requests.
     * Uses {@code key-file} in OCI config to locate the PEM file, uses {@code key-pem} in config.
     *
     * @return private key to use for signing requests
     */
    public String privateKey() {
        return privateKey;
    }

    /**
     * A property defined either in OCI config file or in config.
     *
     * @param key name of the property
     * @return value if present in configuration
     */
    public Optional<String> property(String key) {
        return Optional.ofNullable(fullConfig.get(key));
    }

    /**
     * Full map of configuration properties either from OCI config file or from config.
     *
     * @return map of key/value pairs
     */
    public Map<String, String> fullConfig() {
        return fullConfig;
    }

    /**
     * Fluent API builder for {@link OciConfigProfile}.
     */
    public static class Builder implements io.helidon.common.Builder<OciConfigProfile> {
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
        private static final String DEFAULT_PROFILE = "DEFAULT";

        private final Map<String, String> fullConfig = new HashMap<>();

        private String userOcid;
        private String tenancyOcid;
        private String keyFingerprint;
        private String region;
        private String privateKey;
        private OciSignatureData signatureData;
        private RSAPrivateKey rsaPrivateKey;

        private Builder() {
        }

        @Override
        public OciConfigProfile build() {
            if (rsaPrivateKey == null) {
                this.rsaPrivateKey = KeyConfig.pemBuilder()
                        .key(Resource.create("PEM encoded private key from profile", privateKey))
                        .build()
                        .privateKey()
                        .map(RSAPrivateKey.class::cast)
                        .orElseThrow(() -> new OciApiException("Could not load private key from PEM encoded"));
            }

            validate().checkValid();

            signatureData = OciSignatureData.create(tenancyOcid
                                                            + "/" + userOcid
                                                            + "/" + keyFingerprint,
                                                    rsaPrivateKey);

            return new OciConfigProfile(this);
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config located on the node of OCI configuration
         * @return updated builder
         */
        public Builder config(Config config) {
            config.asMap().ifPresent(this.fullConfig::putAll);

            String profile = config.get("profile-name").asString().orElse(DEFAULT_PROFILE);
            config.get("profile-file").as(Path.class)
                    .ifPresentOrElse(value -> fromOciConfig(value, profile), () -> {
                        // config-file is not present, let's see if we can load defaults
                        if (config.get("config-file-enabled").asBoolean().orElse(true)) {
                            fromOciConfig(profile);
                        }
                    });

            config.get("user").asString().ifPresent(this::userOcid);
            config.get("fingerprint").asString().ifPresent(this::keyFingerprint);
            config.get("tenancy").asString().ifPresent(this::tenancyOcid);
            config.get("region").asString().ifPresent(this::region);
            config.get("key-pem").asString().ifPresent(this::privateKey);

            return this;
        }

        /**
         * User OCID.
         *
         * @param userOcid OCID of the user
         * @return updated builder
         */
        public Builder userOcid(String userOcid) {
            this.userOcid = userOcid;
            this.fullConfig.put("user", userOcid);

            return this;
        }

        /**
         * Tenancy OCID.
         *
         * @param tenancyOcid OCID of the tenancy
         * @return updated builder
         */
        public Builder tenancyOcid(String tenancyOcid) {
            this.tenancyOcid = tenancyOcid;
            this.fullConfig.put("tenancy", tenancyOcid);

            return this;
        }

        /**
         * Key fingerprint.
         *
         * @param keyFingerprint key fingerprint
         * @return updated builder
         */
        public Builder keyFingerprint(String keyFingerprint) {
            this.keyFingerprint = keyFingerprint;
            this.fullConfig.put("fingerprint", keyFingerprint);

            return this;
        }

        /**
         * OCI region.
         *
         * @param region region
         * @return updated builder
         */
        public Builder region(String region) {
            this.region = region;
            this.fullConfig.put("region", region);

            return this;
        }

        /**
         * PEM encoded private key.
         *
         * @param privateKey private key
         * @return updated builder
         */
        public Builder privateKey(String privateKey) {
            this.privateKey = privateKey;
            this.fullConfig.put("key-pem", privateKey);

            return this;
        }

        public Builder privateKey(RSAPrivateKey privateKey) {
            this.rsaPrivateKey = privateKey;

            return this;
        }

        /**
         * Add property as defined in OCI config file.
         *
         * @param key key
         * @param value value
         */
        public void property(String key, String value) {
            switch (key) {
            case "user":
                userOcid(value);
                break;
            case "fingerprint":
                keyFingerprint(value);
                break;
            case "tenancy":
                tenancyOcid(value);
                break;
            case "region":
                region(value);
                break;
            case "key_file":
                keyFile(value);
                break;
            default:
                break;
            }

            fullConfig.put(key, value);
        }

        /**
         * Update this builder from OCI configuration on default path with default profile.
         *
         * @return updated builder
         */
        public Builder fromOciConfig() {
            return fromOciConfig(defaultPath(), DEFAULT_PROFILE);
        }

        /**
         * Update this builder from OCI configuration on default path with custom profile.
         *
         * @param profile name of the profile
         * @return updated builder
         */
        public Builder fromOciConfig(String profile) {
            return fromOciConfig(defaultPath(), profile);
        }

        /**
         * Update this builder from OCI configuration on custom path with default profile.
         *
         * @param path path to the profile
         * @return updated builder
         */
        public Builder fromOciConfig(Path path) {
            return fromOciConfig(path, DEFAULT_PROFILE);
        }

        /**
         * Attempts to read the profile from the OCI config file.
         * This method does not fail in case the path is invalid.
         *
         * @param path path of the OCI config (may not exist)
         * @param profile profile to read
         * @return updated builder instance
         *
         * @throws OciRestException in case the file exists, but the profile name is not valid
         */
        public Builder fromOciConfig(Path path, String profile) {
            if (!Files.exists(path)) {
                LOGGER.fine(() -> "OCI config on path " + path.toAbsolutePath() + " does not exist");
                return this;
            }

            if (Files.isDirectory(path)) {
                LOGGER.warning("OCI config on path " + path.toAbsolutePath() + " is a directory");
                return this;
            }

            if (!Files.isReadable(path)) {
                LOGGER.warning("OCI config on path " + path.toAbsolutePath() + " is not readable");
                return this;
            }

            // now we can process the file
            try {
                readProfile(path, profile);
            } catch (IOException e) {
                throw new OciApiException("Failed to read OCI config on path " + path.toAbsolutePath(), e);
            }

            return this;
        }

        /**
         * Whether this builder has the required configuration.
         *
         * @return {@code true} if this builder is valid
         */
        public boolean configured() {
            return validate().isValid();
        }

        private void readProfile(Path path, String profile) throws IOException {
            List<String> allLines = Files.readAllLines(path);

            boolean foundProfile = false;

            for (String fileLine : allLines) {
                String line = fileLine.trim();
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    String fileProfile = line.substring(1, line.length() - 1);
                    if (foundProfile) {
                        // next profile
                        break;
                    }
                    if (fileProfile.equals(profile)) {
                        foundProfile = true;
                    }
                    continue;
                }
                // ignore other profiles
                if (foundProfile) {
                    // this is (let's hope) a key
                    int equals = line.indexOf('=');
                    if (equals < 1) {
                        LOGGER.warning("Failed to correctly process OCI config " + path
                                .toAbsolutePath() + ", line " + line + " is not understood");
                        continue;
                    }
                    String key = line.substring(0, equals).trim();
                    String value = line.substring(equals + 1).trim();
                    property(key, value);
                }
            }
            if (!foundProfile) {
                throw new OciApiException("Did not find profile " + profile + " in OCI config " + path.toAbsolutePath());
            }
        }

        private Path defaultPath() {
            Path location = Paths.get("~/.oci/config");
            if (Files.exists(location)) {
                return location;
            }
            // maybe ~ does not work
            location = Paths.get(System.getProperty("user.home"));
            location = location.resolve(".oci").resolve("config");

            return location;
        }

        private void keyFile(String fileName) {
            try {
                Path path = Paths.get(fileName);
                if (Files.exists(path)) {
                    LOGGER.fine(() -> "Reading private key from file: " + path.toAbsolutePath());
                    privateKey(Files.readString(path));
                    return;
                }
                if (fileName.startsWith("~")) {
                    String homeDir = System.getProperty("user.home");
                    String newFileName = homeDir + fileName.substring(1);
                    Path userHomePath = Paths.get(newFileName);
                    if (Files.exists(userHomePath)) {
                        LOGGER.fine(() -> "Reading private key from file: " + userHomePath.toAbsolutePath());
                        privateKey(Files.readString(userHomePath));
                        return;
                    }
                }
                throw new IllegalArgumentException("Private key file " + fileName + " does not exist");
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read private key file " + fileName, e);
            }
        }

        private Errors validate() {
            Errors.Collector collector = Errors.collector();

            if (userOcid == null) {
                collector.fatal("\"user\" key not defined. It should contain the OCID of user");
            }

            if (tenancyOcid == null) {
                collector.fatal("\"tenancy\" key not defined. It should contain the OCID of tenancy");
            }

            if (keyFingerprint == null) {
                collector.fatal("\"fingerprint\" key not defined. It should contain the key fingerprint");
            }

            if (region == null) {
                collector.fatal("\"region\" key not defined. It should contain the OCI region");
            }

            if (privateKey == null && rsaPrivateKey == null) {
                collector.fatal("\"key_file\" key not defined. It should provide location of the private key PEM file,"
                                        + "or an RSA private key should be explicitly configured");
            }

            return collector.collect();
        }
    }
}
