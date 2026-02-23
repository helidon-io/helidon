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
import static org.hamcrest.MatcherAssert.assertThat;

/*
Some examples of FNV1a collisions

String A        | String B      | Hash (hex)
----------------|---------------|------------
"costarring"    | "liquid"      | 0x89C62E45
"declinate"     | "macallums"   | 0x0BF8B80D
"altarage"      | "zinke"       | 0x8CB35C2A
"altarages"     | "zinkes"      | 0x8CB35C2B
"apes"          | "rebus"       | 0x20770360
"knish"         | "rapine"      | 0x0A93EB20
 */
@Testing.Test
public class HashCollisionTest {

    private final JsonBinding jsonBinding;

    HashCollisionTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testNamingCollision() {
        NamingCollision namingCollision = new NamingCollision("value1", "value2");
        String json = jsonBinding.serialize(namingCollision);
        assertThat(json, is("{\"costarring\":\"value1\",\"liquid\":\"value2\"}"));

        NamingCollision deserialized = jsonBinding.deserialize(json, NamingCollision.class);
        assertThat(deserialized, is(namingCollision));
    }

    @Test
    public void testMixNamingCollisionWithNormal() {
        MixNamingCollisionWithNormal namingCollision = new MixNamingCollisionWithNormal("value1", "value2", "value3");
        String json = jsonBinding.serialize(namingCollision);
        assertThat(json, is("{\"costarring\":\"value1\",\"liquid\":\"value2\",\"name\":\"value3\"}"));

        MixNamingCollisionWithNormal deserialized = jsonBinding.deserialize(json, MixNamingCollisionWithNormal.class);
        assertThat(deserialized, is(namingCollision));
    }

    @Test
    public void testTwoNamingCollisions() {
        TwoNamingsCollisions namingCollision = new TwoNamingsCollisions("value1", "value2", "value3", "value4");
        String json = jsonBinding.serialize(namingCollision);
        assertThat(json,
                   is("{\"costarring\":\"value1\",\"liquid\":\"value2\",\"declinate\":\"value3\",\"macallums\":\"value4\"}"));

        TwoNamingsCollisions deserialized = jsonBinding.deserialize(json, TwoNamingsCollisions.class);
        assertThat(deserialized, is(namingCollision));
    }

    @Test
    public void testNamingCollisionWithPropertyNameChanged() {
        NamingCollisionWithPropertyNameChanged namingCollision = new NamingCollisionWithPropertyNameChanged("value1", "value2");
        String json = jsonBinding.serialize(namingCollision);
        assertThat(json, is("{\"costarring\":\"value1\",\"liquid\":\"value2\"}"));

        NamingCollisionWithPropertyNameChanged deserialized =
                jsonBinding.deserialize(json,
                                        NamingCollisionWithPropertyNameChanged.class);
        assertThat(deserialized, is(namingCollision));
    }

    @Test
    public void testNamingCollisionWithFailOnUnknown() {
        NamingCollisionWithFailOnUnknown namingCollision = new NamingCollisionWithFailOnUnknown("value1", "value2");
        String json = jsonBinding.serialize(namingCollision);
        assertThat(json, is("{\"costarring\":\"value1\",\"liquid\":\"value2\"}"));

        NamingCollisionWithFailOnUnknown deserialized = jsonBinding.deserialize(json, NamingCollisionWithFailOnUnknown.class);
        assertThat(deserialized, is(namingCollision));
    }

    @Json.Entity
    record NamingCollision(String costarring, String liquid) {
    }

    @Json.Entity
    record MixNamingCollisionWithNormal(String costarring, String liquid, String name) {
    }

    @Json.Entity
    record TwoNamingsCollisions(String costarring, String liquid, String declinate, String macallums) {
    }

    @Json.Entity
    record NamingCollisionWithPropertyNameChanged(String costarring, @Json.Property("liquid") String value2) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record NamingCollisionWithFailOnUnknown(String costarring, String liquid) {
    }

}
