/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.reflection;

/**
 * TODO javadoc.
 */
class PackageTester extends Tester {
    public static String publicFieldStatic;
    protected static String protectedFieldStatic;
    static String packageFieldStatic;
    private static String privateFieldStatic;
    public String publicField;
    protected String protectedField;
    String packageField;
    private String privateField;

    public PackageTester() {
    }

    PackageTester(String packageConstructor) {
    }

    protected PackageTester(int protectedConstructor) {
    }

    private PackageTester(boolean privateConstructor) {
    }

    public static void testPublicStatic() {
    }

    protected static void testProtectedStatic() {
    }

    private static void testPrivateStatic() {
    }

    static void testPackageStatic() {
    }

    public void testPublic() {
    }

    protected void testProtected() {
    }

    private void testPrivate() {
    }

    void testPackage() {
    }
}
