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

import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Type;

/**
 * POJO to test default values.
 */
@Type
public class DefaultValuePOJO {

    @DefaultValue("ID-123")
    private String id;
    
    private int value;

    @DefaultValue("1978-07-03")
    private LocalDate dateObject;

    @DefaultValue("false")
    boolean booleanValue;

    public DefaultValuePOJO() {
    }

    public DefaultValuePOJO(String id, int value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getValue() {
        return value;
    }

    @DefaultValue("111222")
    public void setValue(int value) {
        this.value = value;
    }

    public LocalDate getDateObject() {
        return dateObject;
    }

    public void setDateObject(LocalDate dateObject) {
        this.dateObject = dateObject;
    }

    public boolean isBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }
}
