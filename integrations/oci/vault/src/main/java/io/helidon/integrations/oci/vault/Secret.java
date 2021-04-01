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

    public static Secret create(JsonObject json) {
        return new Secret(json);
    }

    public String compartmentId() {
        return compartmentId;
    }

    public String id() {
        return id;
    }

    public String lifecycleState() {
        return lifecycleState;
    }

    public String name() {
        return name;
    }

    public Instant created() {
        return created;
    }

    public String vaultId() {
        return vaultId;
    }

    public Optional<Integer> currentVersionNumber() {
        return currentVersionNumber;
    }

    public Optional<String> description() {
        return description;
    }

    public Optional<String> keyId() {
        return keyId;
    }

    public Optional<String> lifecycleDetail() {
        return lifecycleDetail;
    }

    public Optional<Instant> versionExpires() {
        return versionExpires;
    }

    public Optional<Instant> deleted() {
        return deleted;
    }
}
