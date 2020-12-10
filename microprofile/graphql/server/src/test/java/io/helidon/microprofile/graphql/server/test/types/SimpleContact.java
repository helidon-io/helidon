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

import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;

import java.util.Objects;

import javax.json.bind.annotation.JsonbProperty;

/**
 * Defines a simple contact.
 */
public class SimpleContact implements Comparable<SimpleContact> {
    private String id;
    private String name;
    private int age;
    private EnumTestWithEnumName tShirtSize;

    public SimpleContact(String id, String name, int age, EnumTestWithEnumName tShirtSize) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.tShirtSize = tShirtSize;
    }

    public SimpleContact() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @JsonbProperty("tShirtSize")
    public EnumTestWithEnumName getTShirtSize() {
        return tShirtSize;
    }

    @JsonbProperty("tShirtSize")
    public void setTShirtSize(EnumTestWithEnumName tShirtSize) {
        this.tShirtSize = tShirtSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleContact contact = (SimpleContact) o;
        return age == contact.age &&
                Objects.equals(id, contact.id) &&
                Objects.equals(name, contact.name) &&
                tShirtSize == contact.tShirtSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, age, tShirtSize);
    }

    @Override
    /**
     * Simple comparison by age.
     */
    public int compareTo(SimpleContact other) {
        return Integer.valueOf(age).compareTo(Integer.valueOf(other.getAge()));
    }
}
