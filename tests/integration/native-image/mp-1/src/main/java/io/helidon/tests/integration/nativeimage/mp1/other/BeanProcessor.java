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

package io.helidon.tests.integration.nativeimage.mp1.other;

public final class BeanProcessor {

    public static String getProducedName(ProducedBean bean) {
        Class<?> sampleClass = ProducedBean.class;
        Class<?> proxyClass = bean.getClass();

        Package samplePackage = sampleClass.getPackage();
        Package proxyPackage = proxyClass.getPackage();

        System.out.println(samplePackage);
        System.out.println(proxyPackage);
        System.out.println("Equals: " + samplePackage.equals(proxyPackage));

        String name = bean.getName();
        return name;
    }

}
