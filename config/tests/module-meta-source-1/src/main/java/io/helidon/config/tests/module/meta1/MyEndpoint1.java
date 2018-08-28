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

package io.helidon.config.tests.module.meta1;

/**
 * Testing implementation of config source endpoint.
 */
public class MyEndpoint1 {
    private final String myProp1;
    private final int myProp2;

    /**
     * Initialize endpoint.
     *
     * @param myProp1 prop1
     * @param myProp2 prop2
     */
    public MyEndpoint1(String myProp1, int myProp2) {
        this.myProp1 = myProp1;
        this.myProp2 = myProp2;
    }

    public String getMyProp1() {
        return myProp1;
    }

    public int getMyProp2() {
        return myProp2;
    }

    @Override
    public String toString() {
        return "MyEndpoint1{"
                + "myProp1='" + myProp1 + '\''
                + ", myProp2=" + myProp2
                + '}';
    }

}
