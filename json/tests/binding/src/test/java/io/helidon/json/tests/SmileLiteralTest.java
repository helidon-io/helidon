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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for Smile format literal values (null, true, false, empty string).
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
@Testing.Test
public class SmileLiteralTest {

    private final JsonBinding jsonBinding;

    SmileLiteralTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: empty String, null, false, and true are dedicated value-mode literal tokens and must round-trip unchanged.
     */
    @Test
    public void testNullValue() {
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, null);
        Object result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, Object.class);

        assertThat(result, is(nullValue()));
    }

    @Test
    public void testBooleanTrue() {
        BooleanModel model = new BooleanModel(true);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BooleanModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BooleanModel.class);

        assertThat(result.value(), is(true));
    }

    @Test
    public void testBooleanFalse() {
        BooleanModel model = new BooleanModel(false);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BooleanModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BooleanModel.class);

        assertThat(result.value(), is(false));
    }

    @Test
    public void testEmptyString() {
        StringModel model = new StringModel("");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);

        assertThat(result.value(), is(""));
    }

    @Test
    public void testNullString() {
        StringModel model = new StringModel(null);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);

        assertThat(result.value(), is(nullValue()));
    }

    @Json.Entity
    record BooleanModel(boolean value) {
    }

    @Json.Entity
    record StringModel(String value) {
    }
}
