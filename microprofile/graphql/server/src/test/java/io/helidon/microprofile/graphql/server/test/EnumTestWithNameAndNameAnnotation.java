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
 */

package io.helidon.microprofile.graphql.server.test;

import org.eclipse.microprofile.graphql.Enum;
import org.eclipse.microprofile.graphql.Name;

/**
 * Class to test enum discovery with enum name and name annotation.
 * The enum name should win.
 */
@Enum("ThisShouldWin")
@Name("TShirtSize")
public enum EnumTestWithNameAndNameAnnotation {
    S,
    M,
    L,
    XL,
    XXL,
    XXXL
}
