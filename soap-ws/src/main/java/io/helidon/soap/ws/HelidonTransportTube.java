/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.soap.ws;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.NextAction;
import com.sun.xml.ws.api.pipe.TubeCloner;
import com.sun.xml.ws.api.pipe.helper.AbstractTubeImpl;

class HelidonTransportTube extends AbstractTubeImpl {

    @Override
    public AbstractTubeImpl copy(TubeCloner tc) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public NextAction processRequest(Packet packet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public NextAction processResponse(Packet packet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public NextAction processException(Throwable thrwbl) {
        return doThrow(thrwbl);
    }

    @Override
    public void preDestroy() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
