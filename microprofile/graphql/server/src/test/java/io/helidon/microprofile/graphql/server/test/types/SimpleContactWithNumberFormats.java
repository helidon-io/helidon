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

import javax.json.bind.annotation.JsonbNumberFormat;

import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Type;

/**
 * Defines a simple contact which contains number formats.
 */
@Type
public class SimpleContactWithNumberFormats {
    private Integer id;
    private String name;

    @NumberFormat("0 'years old'")
    private Integer age;

    @JsonbNumberFormat(value = "¤ 000.00", locale = "en-AU")
    private Float bankBalance;

    @JsonbNumberFormat(value = "000") // this should be ignored
    @NumberFormat("0 'value'")       // this should be applied
    private Integer value;

    private Long longValue;

    public Integer getFormatMethod(@NumberFormat("0 'years old'") int age) {
        return age;
    }

    public SimpleContactWithNumberFormats(Integer id,
                                          String name, Integer age,
                                          Float bankBalance,
                                         int value, Long longValue) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.bankBalance = bankBalance;
        this.value = value;
        this.longValue = longValue;
    }

    @NumberFormat("0 'id'")
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Float getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(Float bankBalance) {
        this.bankBalance = bankBalance;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @NumberFormat("Long-#############")
    public Long getLongValue() {
        return longValue;
    }

    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }
}
