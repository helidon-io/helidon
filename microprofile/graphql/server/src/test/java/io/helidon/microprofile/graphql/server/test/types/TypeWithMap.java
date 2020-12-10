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

import org.eclipse.microprofile.graphql.Type;
import java.util.Map;

/**
 * POJO to test a {@link Map} as a value.
 */
@Type
public class TypeWithMap {

    private String id;
    private Map<Integer, String> mapValues;
    private Map<String, SimpleContact> mapContacts;

    public TypeWithMap(String id,
                       Map<Integer, String> mapValues,
                       Map<String, SimpleContact> mapContacts) {
        this.id = id;
        this.mapValues = mapValues;
        this.mapContacts = mapContacts;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<Integer, String> getMapValues() {
        return mapValues;
    }

    public void setMapValues(Map<Integer, String> mapValues) {
        this.mapValues = mapValues;
    }

    public Map<String, SimpleContact> getMapContacts() {
        return mapContacts;
    }

    public void setMapContacts(Map<String, SimpleContact> mapContacts) {
        this.mapContacts = mapContacts;
    }

    @Override
    public String toString() {
        return "TypeWithMap{" +
                "id='" + id + '\'' +
                ", mapValues=" + mapValues +
                ", mapContacts=" + mapContacts +
                '}';
    }
}
