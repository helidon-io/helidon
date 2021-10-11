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

import java.util.Date;
import java.util.Random;

import io.helidon.common.LazyValue;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredential;
import org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredentialRefreshConfig;
import org.apache.kafka.common.security.oauthbearer.internals.expiring.ExpiringCredentialRefreshingLogin;
import org.slf4j.Logger;

@TargetClass(ExpiringCredentialRefreshingLogin.class)
@SuppressWarnings("checkstyle:StaticVariableName")
final class ExpiringCredentialRefreshingLoginSubstitution {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static Random RNG = null;

    @Alias
    private ExpiringCredential expiringCredential;

    @Alias
    private static Logger log;

    @Alias
    private static long DELAY_SECONDS_BEFORE_NEXT_RETRY_WHEN_RELOGIN_FAILS;

    @Alias
    private ExpiringCredentialRefreshConfig expiringCredentialRefreshConfig;

    @Inject
    private final LazyValue<Random> lazySeededRandom = LazyValue.create(Random::new);

    @Substitute
    private Long refreshMs(long relativeToMs) {
        if (expiringCredential == null) {
            /*
             * Re-login failed because our login() invocation did not generate a credential
             * but also did not generate an exception. Try logging in again after some delay
             * (it seems likely to be a bug, but it doesn't hurt to keep trying to refresh).
             */
            long retvalNextRefreshMs = relativeToMs + DELAY_SECONDS_BEFORE_NEXT_RETRY_WHEN_RELOGIN_FAILS * 1000L;
            log.warn("[Principal={}]: No Expiring credential found: will try again at {}", principalLogText(),
                    new Date(retvalNextRefreshMs));
            return retvalNextRefreshMs;
        }
        long expireTimeMs = expiringCredential.expireTimeMs();
        if (relativeToMs > expireTimeMs) {
            boolean logoutRequiredBeforeLoggingBackIn = isLogoutRequiredBeforeLoggingBackIn();
            if (logoutRequiredBeforeLoggingBackIn) {
                log.error(
                        "[Principal={}]: Current clock: {} is later than expiry {}. This may indicate a clock skew problem."
                                + " Check that this host's and remote host's clocks are in sync. Exiting refresh thread.",
                        principalLogText(), new Date(relativeToMs), new Date(expireTimeMs));
                return null;
            } else {
                /*
                 * Since the current soon-to-expire credential isn't logged out until we have a
                 * new credential with a refreshed lifetime, it is possible that the current
                 * credential could expire if the re-login continually fails over and over again
                 * making us unable to get the new credential. Therefore keep trying rather than
                 * exiting.
                 */
                long retvalNextRefreshMs = relativeToMs + DELAY_SECONDS_BEFORE_NEXT_RETRY_WHEN_RELOGIN_FAILS * 1000L;
                log.warn("[Principal={}]: Expiring credential already expired at {}: will try to refresh again at {}",
                        principalLogText(), new Date(expireTimeMs), new Date(retvalNextRefreshMs));
                return retvalNextRefreshMs;
            }
        }
        Long absoluteLastRefreshTimeMs = expiringCredential.absoluteLastRefreshTimeMs();
        if (absoluteLastRefreshTimeMs != null && absoluteLastRefreshTimeMs.longValue() < expireTimeMs) {
            log.warn("[Principal={}]: Expiring credential refresh thread exiting because the"
                            + " expiring credential's current expiration time ({}) exceeds the latest possible refresh time ({})."
                            + " This process will not be able to authenticate new SASL connections after that"
                            + " time (for example, it will not be able to authenticate a new connection with a Kafka Broker).",
                    principalLogText(), new Date(expireTimeMs), new Date(absoluteLastRefreshTimeMs.longValue()));
            return null;
        }
        Long optionalStartTime = expiringCredential.startTimeMs();
        long startMs = optionalStartTime != null ? optionalStartTime.longValue() : relativeToMs;
        log.info("[Principal={}]: Expiring credential valid from {} to {}", expiringCredential.principalName(),
                new java.util.Date(startMs), new java.util.Date(expireTimeMs));

        double pct = expiringCredentialRefreshConfig.loginRefreshWindowFactor()
                + (expiringCredentialRefreshConfig.loginRefreshWindowJitter() * lazySeededRandom.get().nextDouble());
        /*
         * Ignore buffer times if the credential's remaining lifetime is less than their
         * sum.
         */
        long refreshMinPeriodSeconds = expiringCredentialRefreshConfig.loginRefreshMinPeriodSeconds();
        long clientRefreshBufferSeconds = expiringCredentialRefreshConfig.loginRefreshBufferSeconds();
        if (relativeToMs + 1000L * (refreshMinPeriodSeconds + clientRefreshBufferSeconds) > expireTimeMs) {
            long retvalRefreshMs = relativeToMs + (long) ((expireTimeMs - relativeToMs) * pct);
            log.warn(
                    "[Principal={}]: Expiring credential expires at {}, so buffer times of {} and {} seconds"
                            + " at the front and back, respectively, cannot be accommodated.  We will refresh at {}.",
                    principalLogText(), new Date(expireTimeMs), refreshMinPeriodSeconds, clientRefreshBufferSeconds,
                    new Date(retvalRefreshMs));
            return retvalRefreshMs;
        }
        long proposedRefreshMs = startMs + (long) ((expireTimeMs - startMs) * pct);
        // Don't let it violate the requested end buffer time
        long beginningOfEndBufferTimeMs = expireTimeMs - clientRefreshBufferSeconds * 1000;
        if (proposedRefreshMs > beginningOfEndBufferTimeMs) {
            log.info("[Principal={}]: Proposed refresh time of {} extends into the desired buffer time of {} "
                            + "seconds before expiration, so refresh it at the desired buffer begin point, at {}",
                    expiringCredential.principalName(), new Date(proposedRefreshMs), clientRefreshBufferSeconds,
                    new Date(beginningOfEndBufferTimeMs));
            return beginningOfEndBufferTimeMs;
        }
        // Don't let it violate the minimum refresh period
        long endOfMinRefreshBufferTime = relativeToMs + 1000 * refreshMinPeriodSeconds;
        if (proposedRefreshMs < endOfMinRefreshBufferTime) {
            log.info(
                    "[Principal={}]: Expiring credential re-login thread time adjusted from {} to {} since the former is sooner "
                            + "than the minimum refresh interval ({} seconds from now).",
                    principalLogText(), new Date(proposedRefreshMs), new Date(endOfMinRefreshBufferTime),
                    refreshMinPeriodSeconds);
            return endOfMinRefreshBufferTime;
        }
        // Proposed refresh time doesn't violate any constraints
        return proposedRefreshMs;
    }

    @Alias
    private native String principalLogText();

    @Alias
    private native boolean isLogoutRequiredBeforeLoggingBackIn();
}
