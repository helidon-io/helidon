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

package io.helidon.integrations.oci.vault;

import java.time.Instant;
import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.integrations.oci.connect.OciResponseParser;

/**
 * A secret obtained from the vault. This object does not contain the actual secret content,
 *  please see
 *  {@link OciVaultRx#getSecretBundle(io.helidon.integrations.oci.vault.GetSecretBundle.Request)}
 *  to obtain secret content.
 */
public class Secret extends OciResponseParser {
    private final String compartmentId;
    private final String id;
    private final String lifecycleState;
    private final String name;
    private final Instant created;
    private final String vaultId;
    private final Optional<Integer> currentVersionNumber;
    private final Optional<String> description;
    private final Optional<String> keyId;
    private final Optional<String> lifecycleDetail;
    private final Optional<Instant> versionExpires;
    private final Optional<Instant> deleted;

    private Secret(JsonObject json) {
        this.compartmentId = json.getString("compartmentId");
        this.id = json.getString("id");
        this.lifecycleState = json.getString("lifecycleState");
        this.name = json.getString("secretName");
        this.created = getInstant(json, "timeCreated");
        this.vaultId = json.getString("vaultId");
        this.currentVersionNumber = toInt(json, "currentVersionNumber");
        this.description = toString(json, "description");
        this.keyId = toString(json, "keyId");
        this.lifecycleDetail = toString(json, "lifecycleDetails");
        this.versionExpires = toInstant(json, "timeOfCurrentVersionExpiry");
        this.deleted = toInstant(json, "timeOfDeletion");
    }

    static Secret create(JsonObject json) {
        return new Secret(json);
    }

    /**
     * The OCID of the compartment where the secret was created.
     *
     * @return compartment OCID
     */
    public String compartmentId() {
        return compartmentId;
    }

    /**
     * The OCID of the secret.
     *
     * @return secret OCID
     */
    public String id() {
        return id;
    }

    /**
     * The current lifecycle state of the secret.
     *
     * @return lifecycle state
     */
    public String lifecycleState() {
        return lifecycleState;
    }

    /**
     * The user-friendly name of the secret. Avoid entering confidential information.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * A property indicating when the secret was created.
     *
     * @return creation instant
     */
    public Instant created() {
        return created;
    }

    /**
     * The OCID of the vault where the secret exists.
     *
     * @return vault OCID
     */
    public String vaultId() {
        return vaultId;
    }

    /**
     * The version number of the secret version that's currently in use.
     *
     * @return version number
     */
    public Optional<Integer> currentVersionNumber() {
        return currentVersionNumber;
    }

    /**
     * A brief description of the secret. Avoid entering confidential information.
     *
     * @return description
     */
    public Optional<String> description() {
        return description;
    }

    /**
     * The OCID of the master encryption key that is used to encrypt the secret.
     *
     * @return key OCID
     */
    public Optional<String> keyId() {
        return keyId;
    }

    /**
     * Additional information about the current lifecycle state of the secret.
     *
     * @return lifecycle information
     */
    public Optional<String> lifecycleDetail() {
        return lifecycleDetail;
    }

    /**
     * An optional property indicating when the current secret version will expire.
     *
     * @return when the version expires
     */
    public Optional<Instant> versionExpires() {
        return versionExpires;
    }

    /**
     * An optional property indicating when to delete the secret.
     *
     * @return deletion instant
     */
    public Optional<Instant> deleted() {
        return deleted;
    }
}
