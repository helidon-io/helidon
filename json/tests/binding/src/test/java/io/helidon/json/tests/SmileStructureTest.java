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

import java.util.List;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for Smile format structure values (objects, arrays).
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
@Testing.Test
public class SmileStructureTest {

    private final JsonBinding jsonBinding;

    SmileStructureTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /*
     * Spec: "High-level format".
     * Rule: Smile content is a properly nested token sequence, so empty arrays and objects still emit matching
     * start/end markers.
     */
    @Test
    public void testEmptyObject() {
        EmptyModel model = new EmptyModel();
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        EmptyModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, EmptyModel.class);
        assertThat(result, is(model));
    }

    @Test
    public void testEmptyArray() {
        EmptyArrayModel model = new EmptyArrayModel(List.of());
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        EmptyArrayModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, EmptyArrayModel.class);
        assertThat(result.items().isEmpty(), is(true));
    }

    /*
     * Spec: "Tokens: key mode".
     * Rule: object content alternates between key-mode name tokens and value-mode payload tokens until `END_OBJECT`.
     */
    @Test
    public void testSimpleObjectWithPrimitives() {
        SimpleObjectModel model = new SimpleObjectModel("hello", 42, true, null);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        SimpleObjectModel result =
                SmileBindingSupport.deserializeSmile(jsonBinding, smileData, SimpleObjectModel.class);
        assertThat(result.string(), is("hello"));
        assertThat(result.number(), is(42));
        assertThat(result.bool(), is(true));
        assertThat(result.nil(), is(nullValue()));
    }

    @Test
    public void testNestedObject() {
        NestedModel nested = new NestedModel("value", 5);
        ContainerModel model = new ContainerModel("test", nested);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        ContainerModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, ContainerModel.class);
        assertThat(result.simple(), is("test"));
        assertThat(result.nested().deep(), is("value"));
        assertThat(result.nested().count(), is(5));
    }

    /*
     * Spec: "High-level format".
     * Rule: arrays are likewise properly nested token sequences and can contain heterogeneous scalar values or nested
     * arrays.
     */
    @Test
    public void testSimpleArrayWithPrimitives() {
        PrimitiveArrayModel model = new PrimitiveArrayModel(List.of("hello", 42, true));
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        PrimitiveArrayModel result =
                SmileBindingSupport.deserializeSmile(jsonBinding, smileData, PrimitiveArrayModel.class);
        assertThat(result.values().size(), is(3));
        assertThat(result.values().get(0), is("hello"));
        assertThat(result.values().get(1), is(42.0));
        assertThat(result.values().get(2), is(true));
    }

    @Test
    public void testNestedArrays() {
        NestedArrayModel model = new NestedArrayModel(List.of(List.of(1, 2, 3), List.of(4, 5, 6)));
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        NestedArrayModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, NestedArrayModel.class);
        assertThat(result.matrix().size(), is(2));
        assertThat(result.matrix().get(0).size(), is(3));
        assertThat(result.matrix().get(0).getFirst(), is(1));
        assertThat(result.matrix().get(1).get(2), is(6));
    }

    /*
     * Spec: "High-level format".
     * Rule: the nesting and token framing rules apply uniformly regardless of collection size.
     */
    @Test
    public void testLargeArray() {
        List<Integer> largeList = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeList.add(i);
        }
        LargeArrayModel model = new LargeArrayModel(largeList);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        LargeArrayModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, LargeArrayModel.class);
        assertThat(result.numbers().size(), is(1000));
        for (int i = 0; i < 1000; i++) {
            assertThat(result.numbers().get(i), is(i));
        }
    }

    @Json.Entity
    record EmptyModel() {
    }

    @Json.Entity
    record EmptyArrayModel(List<Object> items) {
    }

    @Json.Entity
    record SimpleObjectModel(String string, int number, boolean bool, Object nil) {
    }

    @Json.Entity
    record NestedModel(String deep, int count) {
    }

    @Json.Entity
    record ContainerModel(String simple, NestedModel nested) {
    }

    @Json.Entity
    record PrimitiveArrayModel(List<Object> values) {
    }

    @Json.Entity
    record NestedArrayModel(List<List<Integer>> matrix) {
    }

    @Json.Entity
    record LargeArrayModel(List<Integer> numbers) {
    }
}
