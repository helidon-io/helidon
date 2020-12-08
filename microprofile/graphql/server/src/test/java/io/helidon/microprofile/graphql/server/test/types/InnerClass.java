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

/**
 * POJO to test inner classes
 */
@Type
public class InnerClass {

    private String id;
    private InnerClass innerClass;

    public InnerClass(String id, InnerClass innerClass) {
        this.id = id;
        this.innerClass = innerClass;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public InnerClass getInnerClass() {
        return innerClass;
    }

    public void setInnerClass(InnerClass innerClass) {
        this.innerClass = innerClass;
    }

    public class AnInnerClass {
        private int innerClassId;

        public AnInnerClass(int innerClassId) {
            this.innerClassId = innerClassId;
        }

        public int getInnerClassId() {
            return innerClassId;
        }

        public void setInnerClassId(int innerClassId) {
            this.innerClassId = innerClassId;
        }
    }

}
