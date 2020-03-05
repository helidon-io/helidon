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

import javax.json.bind.annotation.JsonbProperty;

import org.eclipse.microprofile.graphql.Name;

/**
 * Class to test {@link JsonbProperty} annotation on field.
 */
public class TypeWithNameAndJsonbProperty {

    @JsonbProperty("newFieldName1")
    private String name1;

    @Name("newFieldName2")
    private String name2;

    // @Name annotation should take precedence
    @Name("newFieldName3")
    @JsonbProperty("thisShouldNotBeUsed")
    private String name3;
    
    private String name4;
    private String name5;
    private String name6;

    public TypeWithNameAndJsonbProperty(String name1, String name2, String name3, String name4, String name5, String name6) {
        this.name1 = name1;
        this.name2 = name2;
        this.name3 = name3;
        this.name4 = name4;
        this.name5 = name5;
        this.name6 = name6;
    }

    public String getName1() {
        return name1;
    }

    public String getName2() {
        return name2;
    }

    public String getName3() {
        return name3;
    }

    @Name("newFieldName4")
    public String getName4() {
        return name4;
    }

    @JsonbProperty("newFieldName5")
    public String getName5() {
        return name5;
    }

    // @Name annotation should take precedence
    @Name("newFieldName6")
    @JsonbProperty("thisShouldNotBeUsed")
    public String getName6() {
        return name6;
    }
}
