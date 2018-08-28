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

package io.helidon.config.etcd;

import org.mockito.stubbing.OngoingStubbing;

/**
 * This is hack to compile tests to fix following issues:
 * <pre>{@code
 * OngoingStubbing.thenReturn(T,T...) in package org.mockito.stubbing is not accessible
 *                .thenReturn("mock");
 *                ^
 *   (package org.mockito.stubbing is declared in module , which does not export it)
 *   where T is a type-variable:
 *     T extends Object declared in interface OngoingStubbing
 *   1 error
 * }</pre>
 */
public class JigsawUnnamedHack {

    private static final void importClassesThatJavacItselfDoesNotExport() {
        OngoingStubbing ongoingStubbing = null;
    }

}
