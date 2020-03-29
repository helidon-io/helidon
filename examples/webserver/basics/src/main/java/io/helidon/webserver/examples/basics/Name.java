/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.basics;

/**
 * Represents a simple entity - the name.
 */
public class Name {

    private final String firstName;
    private final String middleName;
    private final String lastName;

    /**
     * An naive implementation of name parser.
     *
     * @param fullName a full name
     */
    public Name(String fullName) {
        String[] split = fullName.split(" ");
        switch (split.length) {
        case 0:
            throw new IllegalArgumentException("An empty name");
        case 1:
            firstName = null;
            middleName = null;
            lastName = split[0];
            break;
        case 2:
            firstName = split[0];
            middleName = null;
            lastName = split[1];
            break;
        case 3:
            firstName = split[0];
            middleName = split[1];
            lastName = split[2];
            break;
        default:
            throw new IllegalArgumentException("To many name parts!");
        }
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (firstName != null) {
            result.append(firstName);
        }
        if (middleName != null) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(middleName);
        }
        if (lastName != null) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(lastName);
        }
        return result.toString();
    }
}
