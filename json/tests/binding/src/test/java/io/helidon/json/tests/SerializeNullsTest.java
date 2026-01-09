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

package io.helidon.json.tests;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testing.Test
public class SerializeNullsTest {

    private final JsonBinding jsonBinding;

    SerializeNullsTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testJsonNullableOnRecord() {
        JsonNullableOnRecord instance = new JsonNullableOnRecord(null, null);
        assertEquals("{\"someField\":null,\"someField2\":null}", jsonBinding.serialize(instance));
    }

    @Test
    public void testJsonNullableOnRecordComponent() {
        JsonNullableOnRecordComponent instance = new JsonNullableOnRecordComponent(null, null);
        assertEquals("{\"someField\":null}", jsonBinding.serialize(instance));
    }

    @Test
    public void testJsonNullableOverrideOnField() {
        NullableOverrideOnField instance = new NullableOverrideOnField();
        assertEquals("{\"field2\":null}", jsonBinding.serialize(instance));
    }

    @Test
    public void testJsonNullableOverrideOnMethod() {
        NullableOverrideOnMethod instance = new NullableOverrideOnMethod();
        assertEquals("{\"field2\":null}", jsonBinding.serialize(instance));
    }

    @Test
    public void testJsonNullableFromParent() {
        NullableChild instance = new NullableChild();
        assertEquals("{\"field\":null}", jsonBinding.serialize(instance));
    }

    @Test
    public void testJsonNullableFromParentOverride() {
        NonNullableChild instance = new NonNullableChild();
        assertEquals("{}", jsonBinding.serialize(instance));
    }

    @Json.Entity
    @Json.SerializeNulls
    record JsonNullableOnRecord(String someField, String someField2) {
    }

    @Json.Entity
    record JsonNullableOnRecordComponent(@Json.SerializeNulls String someField, String someField2) {
    }

    @Json.Entity
    @Json.SerializeNulls
    static class NullableOverrideOnField {
        @Json.SerializeNulls(false)
        String field = null;
        String field2 = null;
    }

    @Json.Entity
    @Json.SerializeNulls
    static class NullableOverrideOnMethod {
        String field = null;
        String field2 = null;

        @Json.SerializeNulls(false)
        public String field() {
            return field;
        }
    }

    @Json.SerializeNulls
    static class NullableParent {
    }

    @Json.Entity
    static class NullableChild extends NullableParent {
        String field = null;
    }

    @Json.Entity
    @Json.SerializeNulls(false)
    static class NonNullableChild extends NullableParent {
        String field = null;
    }

}
