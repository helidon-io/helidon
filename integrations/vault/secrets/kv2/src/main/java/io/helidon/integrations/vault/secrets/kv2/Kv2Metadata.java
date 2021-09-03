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

package io.helidon.integrations.vault.secrets.kv2;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiJsonParser;

/**
 * Metadata of a KV version 2 secret.
 */
public class Kv2Metadata extends ApiJsonParser {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    private final Instant createdTime;
    private final int version;
    private final Optional<Instant> deletedTime;
    private final boolean destroyed;

    private Kv2Metadata(JsonObject metadata) {
        this.createdTime = Instant.from(FORMATTER.parse(metadata.getString("created_time")));
        this.version = metadata.getInt("version");
        this.deletedTime = toInstant(metadata, "deletion_time", FORMATTER);
        this.destroyed = metadata.getBoolean("destroyed");
    }

    /**
     * Create metadata from API JSON.
     *
     * @param metadata API JSON
     * @return metadata
     */
    static Kv2Metadata create(JsonObject metadata) {
        return new Kv2Metadata(metadata);
    }

    /**
     * Created timestamp.
     *
     * @return timestamp
     */
    public Instant createdTime() {
        return createdTime;
    }

    /**
     * Deleted timestamp for deleted secrets.
     *
     * @return timestamp or empty if not deleted
     */
    public Optional<Instant> deletedTime() {
        return deletedTime;
    }

    /**
     * Version of the secret.
     *
     * @return version
     */
    public int version() {
        return version;
    }

    /**
     * Whether the secret is deleted (can be undeleted).
     *
     * @return {@code true} if the secret is deleted, {@code false} otherwise
     */
    public boolean deleted() {
        return deletedTime.isPresent();
    }

    /**
     * Whether the secret is destroyed (cannot be undeleted).
     *
     * @return {@code true} if the secret is destroyed, {@code false} otherwise
     */
    public boolean destroyed() {
        return destroyed;
    }

    @Override
    public String toString() {
        return "Created: " + createdTime()
                + ", version: " + version
                + ", deleted: " + deletedTime
                + ", destroyed: " + destroyed;
    }
}
