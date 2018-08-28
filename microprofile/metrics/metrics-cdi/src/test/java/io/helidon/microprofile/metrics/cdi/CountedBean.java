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

package io.helidon.microprofile.metrics.cdi;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.metrics.annotation.Counted;

/**
 * Class CountedBean.
 */
@Dependent
@Counted(monotonic = true)
public class CountedBean {

    @Counted(monotonic = true)
    public void method1() {
    }

    // Inherits annotations from class
    public void method2() {
    }
}
