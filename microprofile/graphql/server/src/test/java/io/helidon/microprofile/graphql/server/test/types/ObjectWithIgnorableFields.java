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

import javax.json.bind.annotation.JsonbTransient;

import org.eclipse.microprofile.graphql.Ignore;

/**
 * Defines an object that has fields that should be ignored.
 */
public class ObjectWithIgnorableFields {

    private String id;

    @Ignore
    private String pleaseIgnore;

    @JsonbTransient
    private int ignoreThisAsWell;

    private boolean dontIgnore;

    public ObjectWithIgnorableFields(String id, String pleaseIgnore, int ignoreThisAsWell, boolean dontIgnore) {
        this.id = id;
        this.pleaseIgnore = pleaseIgnore;
        this.ignoreThisAsWell = ignoreThisAsWell;
        this.dontIgnore = dontIgnore;
    }

    public ObjectWithIgnorableFields() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPleaseIgnore() {
        return pleaseIgnore;
    }

    public void setPleaseIgnore(String pleaseIgnore) {
        this.pleaseIgnore = pleaseIgnore;
    }

    public int getIgnoreThisAsWell() {
        return ignoreThisAsWell;
    }

    public void setIgnoreThisAsWell(int ignoreThisAsWell) {
        this.ignoreThisAsWell = ignoreThisAsWell;
    }

    public boolean isDontIgnore() {
        return dontIgnore;
    }

    public void setDontIgnore(boolean dontIgnore) {
        this.dontIgnore = dontIgnore;
    }
}
