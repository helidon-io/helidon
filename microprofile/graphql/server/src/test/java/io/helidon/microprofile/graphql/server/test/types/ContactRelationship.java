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

/**
 * Defines a relationship between two {@link SimpleContact}s. This is to test
 * generation of nested InputTypes if this type is used as a parameter.
 */
public class ContactRelationship {
    private SimpleContact contact1;
    private SimpleContact contact2;
    private String relationship;

    public ContactRelationship(SimpleContact contact1,
                               SimpleContact contact2, String relationship) {
        this.contact1 = contact1;
        this.contact2 = contact2;
        this.relationship = relationship;
    }

    public ContactRelationship() {
    }

    public SimpleContact getContact1() {
        return contact1;
    }

    public void setContact1(SimpleContact contact1) {
        this.contact1 = contact1;
    }

    public SimpleContact getContact2() {
        return contact2;
    }

    public void setContact2(SimpleContact contact2) {
        this.contact2 = contact2;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContactRelationship that = (ContactRelationship) o;
        return Objects.equals(contact1, that.contact1) &&
                Objects.equals(contact2, that.contact2) &&
                Objects.equals(relationship, that.relationship);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contact1, contact2, relationship);
    }
}
