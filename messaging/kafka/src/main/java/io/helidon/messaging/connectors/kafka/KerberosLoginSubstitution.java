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

import javax.security.auth.kerberos.KerberosTicket;

import io.helidon.common.LazyValue;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.utils.Time;
import org.slf4j.Logger;

@TargetClass(org.apache.kafka.common.security.kerberos.KerberosLogin.class)
@SuppressWarnings("checkstyle:StaticVariableName")
final class KerberosLoginSubstitution {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static Random RNG = null;

    @Alias
    private static Logger log;

    @Alias
    private String principal;

    @Alias
    private double ticketRenewWindowFactor;

    @Alias
    private double ticketRenewJitter;

    @Alias
    private Time time;

    @Inject
    private final LazyValue<Random> lazySeededRandom = LazyValue.create(Random::new);

    @Substitute
    private long getRefreshTime(KerberosTicket tgt) {
        long start = tgt.getStartTime().getTime();
        long expires = tgt.getEndTime().getTime();
        log.info("[Principal={}]: TGT valid starting at: {}", principal, tgt.getStartTime());
        log.info("[Principal={}]: TGT expires: {}", principal, tgt.getEndTime());
        long proposedRefresh = start + (long) ((expires - start)
                * (ticketRenewWindowFactor + (ticketRenewJitter * lazySeededRandom.get().nextDouble())));

        if (proposedRefresh > expires) {
            // proposedRefresh is too far in the future: it's after ticket expires: simply return now.
            return currentWallTime();
        } else {
            return proposedRefresh;
        }
    }

    @Alias
    private native long currentWallTime();

}
