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

/**
 * Basic security provider, supporting "basic" and "digest" authentication schemes with role support.
 */
@Features({
    @Feature("Security/Providers/Basic-Auth"),
    @Feature("Security/Providers/Digest-Auth")
})
@Flavor({MP, SE})
package io.helidon.security.providers.httpauth;

import io.helidon.common.Feature;
import io.helidon.common.Features;
import io.helidon.common.Flavor;

import static io.helidon.common.HelidonFlavor.MP;
import static io.helidon.common.HelidonFlavor.SE;