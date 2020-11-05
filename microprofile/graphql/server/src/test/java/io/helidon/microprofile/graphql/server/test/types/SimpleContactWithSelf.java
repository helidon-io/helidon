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

import java.util.Objects;

import org.eclipse.microprofile.graphql.Type;

/**
 * Defines a simple contact which also has a reference with self.
 */
@Type
public class SimpleContactWithSelf {
    private String id;
    private String name;
    private int age;
    private SimpleContactWithSelf spouse;

    public SimpleContactWithSelf(String id,
                                 String name,
                                 int age) {
        this.id = id;
        this.name = name;
        this.age = age;
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

    public SimpleContactWithSelf getSpouse() {
        return spouse;
    }

    public void setSpouse(SimpleContactWithSelf spouse) {
        this.spouse = spouse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleContactWithSelf that = (SimpleContactWithSelf) o;
        return age == that.age
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(spouse, that.spouse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, age, spouse);
    }
}
