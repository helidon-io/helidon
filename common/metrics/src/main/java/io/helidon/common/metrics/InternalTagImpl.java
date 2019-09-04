/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.common.metrics;

import java.util.Objects;

/**
 * Version-neutral implementation of {@code Tag} expressing a name/value pair.
 * <p>
 * To create a new instance of this class, use the
 * {@link InternalBridge.Tag#newTag(java.lang.String, java.lang.String)} method.
 */
class InternalTagImpl implements InternalBridge.Tag {

    private final String name;
    private final String value;

    /**
     * Creates a new tag.
     *
     * @param name used for the tag
     * @param value used for the tag
     */
    InternalTagImpl(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     *
     * @return the name of the tag
     */
    @Override
    public String getTagName() {
        return name;
    }

    /**
     *
     * @return the value of the tag
     */
    @Override
    public String getTagValue() {
        return value;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.name);
        hash = 59 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final InternalTagImpl other = (InternalTagImpl) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.value, other.value)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("Tag{%s=%s}", name, value);
    }

}
