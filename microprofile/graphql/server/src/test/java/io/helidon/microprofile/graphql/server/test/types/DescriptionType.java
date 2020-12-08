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

import java.time.LocalDate;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Type;

/**
 * POJO to test descriptions.
 */
@Type
public class DescriptionType {

    @Description("this is the description")
    private String id;
    private int value;

    @NumberFormat("L-########")
    private Long longValue1;

    @NumberFormat(value = "###,###", locale = "en-AU")
    @Description("Description")
    private Long longValue2;

    private LocalDate localDate;

    public DescriptionType(String id, int value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Description("description of value")
    public int getValue() {
        return value;
    }

    @Description("description on set for input")
    public void setValue(int value) {
        this.value = value;
    }

    public Long getLongValue1() {
        return longValue1;
    }

    public void setLongValue1(Long longValue1) {
        this.longValue1 = longValue1;
    }

    public Long getLongValue2() {
        return longValue2;
    }

    public void setLongValue2(Long longValue2) {
        this.longValue2 = longValue2;
    }
}
