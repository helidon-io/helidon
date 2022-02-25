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
package io.helidon.examples.istio;

import javax.persistence.*;

/**
 * Person JPA entity.
 */
@Entity
@NamedStoredProcedureQueries({
        @NamedStoredProcedureQuery(
                name = "GetAllPersons",
                procedureName = "getAllPersons",
                resultClasses = {Person.class}
        )
})
public class Person {

    @Id
    @Column(columnDefinition = "VARCHAR(32)", nullable = false)
    private String nick;

    @Column
    private String name;

    public Person() {
    }

    public Person(String nick, String name) {
        this.nick = nick;
        this.name = name;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Person [nick=" + nick + ", name=" + name + "]";
    }

}
