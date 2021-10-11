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

import io.helidon.common.LazyValue;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@SuppressWarnings("checkstyle:StaticVariableName")
final class SaslClientAuthSubstitution {

    @TargetClass(org.apache.kafka.common.security.authenticator.SaslClientAuthenticator.class)
    static final class SaslClientAuthenticatorSubstitution {

        @Alias
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
        private static Random RNG = null;
    }

    @TargetClass(className = "org.apache.kafka.common.security.authenticator.SaslClientAuthenticator$ReauthInfo")
    static final class ReauthInfoSubstitution {

        @Alias
        private long authenticationEndNanos;

        @Alias
        private Long positiveSessionLifetimeMs;

        @Alias
        private Long clientSessionReauthenticationTimeNanos;

        @Inject
        private final LazyValue<Random> lazySeededRandom = LazyValue.create(Random::new);

        @Substitute
        public void setAuthenticationEndAndSessionReauthenticationTimes(long nowNanos) {
            authenticationEndNanos = nowNanos;
            long sessionLifetimeMsToUse = 0;
            if (positiveSessionLifetimeMs != null) {
                // pick a random percentage between 85% and 95% for session re-authentication
                double pctWindowFactorToTakeNetworkLatencyAndClockDriftIntoAccount = 0.85;
                double pctWindowJitterToAvoidReauthenticationStormAcrossManyChannelsSimultaneously = 0.10;
                double pctToUse = pctWindowFactorToTakeNetworkLatencyAndClockDriftIntoAccount
                        + lazySeededRandom.get().nextDouble()
                        * pctWindowJitterToAvoidReauthenticationStormAcrossManyChannelsSimultaneously;
                sessionLifetimeMsToUse = (long) (positiveSessionLifetimeMs * pctToUse);
                clientSessionReauthenticationTimeNanos = authenticationEndNanos + 1000 * 1000 * sessionLifetimeMsToUse;
            }
        }
    }
}
