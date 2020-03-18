/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Objects;

import javax.json.bind.annotation.JsonbNumberFormat;

import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Type;

/**
 * Defines a simple contact which contains number formats.
 */
@Type
public class SimpleContactWithNumberFormats {
    private String id;
    private String name;

    @NumberFormat("0 'years old'")
    private int age;

    @JsonbNumberFormat(value = "¤ 000.00", locale = "en-AU")
    private float bankBalance;

    @JsonbNumberFormat(value = "000") // this should be ignored
    @NumberFormat("0 'value'")       // this should be applied
    private int value;

    public int getFormatMethod(@NumberFormat("0 'years old'") int age) {
        return age;
    }

    public SimpleContactWithNumberFormats(String id,
                                          String name,
                                          int age, float bankBalance, int value) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.bankBalance = bankBalance;
        this.value = value;
    }

    @NumberFormat("0 'id'")
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

    public float getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(float bankBalance) {
        this.bankBalance = bankBalance;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleContactWithNumberFormats that = (SimpleContactWithNumberFormats) o;
        return age == that.age
                && Float.compare(that.bankBalance, bankBalance) == 0
                && Objects.equals(id, that.id)
                && Objects.equals(value, that.value)
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, age, bankBalance, value);
    }

    @Override
    public String toString() {
        return "SimpleContactWithNumberFormats{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", age=" + age
                + ", value" + value
                + ", bankBalance=" + bankBalance + '}';
    }
}
