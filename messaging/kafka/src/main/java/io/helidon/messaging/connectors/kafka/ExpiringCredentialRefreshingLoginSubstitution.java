/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.kafka;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredentialRefreshingLogin;

@TargetClass(ExpiringCredentialRefreshingLogin.class)
@SuppressWarnings("checkstyle:StaticVariableName")
final class ExpiringCredentialRefreshingLoginSubstitution {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static Random RNG = ThreadLocalRandom.current();
}
