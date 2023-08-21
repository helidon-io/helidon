/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.tls;

import io.helidon.builder.api.Prototype;

class TlsConfigDecorator implements Prototype.BuilderDecorator<TlsConfig.BuilderBase<?, ?>> {

    @Override
    public void decorate(TlsConfig.BuilderBase<?, ?> target) {
        TlsManager theManager = target.manager().orElse(null);
        if (theManager == null) {
            theManager = new ConfiguredTlsManager(target);
            target.manager(theManager);
        }

        Tls tls = theManager.tls();
        if (tls != null) {
            target.sslParameters(tls.sslParameters());
            target.sslContext(tls.sslContext());
            target.tlsInfo(
                    new TlsInternalInfo(false,
                                        tls.reloadableComponents(), tls.originalTrustManager(), tls.originalKeyManager()));
        }
    }

}
