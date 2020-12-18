/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server.test.types;

import java.util.UUID;

import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Type;

/**
 * Class to test multiple ID fields.
 */
@Type
public class TypeWithIDs {

    @Id
    private int intId;

    @Id
    private Integer integerId;

    @Id
    private String stringId;

    @Id
    private Long longId;

    @Id
    private long longPrimitiveId;

    @Id
    private UUID uuidId;

    public TypeWithIDs() {
    }
    
    public TypeWithIDs(int intId, Integer integerId, String stringId, Long longId, long longPrimitiveId, UUID uuidId) {
        this.intId = intId;
        this.integerId = integerId;
        this.stringId = stringId;
        this.longId = longId;
        this.longPrimitiveId = longPrimitiveId;
        this.uuidId = uuidId;
    }

    public int getIntId() {
        return intId;
    }

    public void setIntId(int intId) {
        this.intId = intId;
    }

    public Integer getIntegerId() {
        return integerId;
    }

    public void setIntegerId(Integer integerId) {
        this.integerId = integerId;
    }

    public String getStringId() {
        return stringId;
    }

    public void setStringId(String stringId) {
        this.stringId = stringId;
    }

    public Long getLongId() {
        return longId;
    }

    public void setLongId(Long longId) {
        this.longId = longId;
    }

    public long getLongPrimitiveId() {
        return longPrimitiveId;
    }

    public void setLongPrimitiveId(long longPrimitiveId) {
        this.longPrimitiveId = longPrimitiveId;
    }

    public UUID getUuidId() {
        return uuidId;
    }

    public void setUuidId(UUID uuidId) {
        this.uuidId = uuidId;
    }
}
