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

package io.helidon.examples.integrations.neo4j.mp.domain;

/**
 * The Person class.
 */
public class Person {

    private final String name;

    private Integer born;

    /**
     * Person constructor.
     *
     * @param born
     * @param name
     */
    public Person(Integer born, String name) {
        this.born = born;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Integer getBorn() {
        return born;
    }

    public void setBorn(Integer born) {
        this.born = born;
    }

    @SuppressWarnings("checkstyle:OperatorWrap")
    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", born=" + born +
                '}';
    }
}
