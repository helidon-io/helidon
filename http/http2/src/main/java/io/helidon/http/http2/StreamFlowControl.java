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
package io.helidon.http.http2;

import java.util.function.BiConsumer;

/**
 * Stream specific HTTP/2 flow control.
 * <br/>
 * Manages:
 * <ul>
 *     <li>Inbound - counts the received data and sends WINDOWS_UPDATE frames to the other side when needed.</li>
 *     <li>Outbound - counts the sent data, monitors requested data by received WINDOWS_UPDATE frames
 *     and blocks the sending until enough data is requested.</li>
 * </ul>
 *
 * Each, inbound and inbound keeps 2 separate credit windows, connection one which is common to all streams
 * and stream window.
 */
public class StreamFlowControl {
    private final FlowControl.Outbound outboundFlowControl;
    private final FlowControl.Inbound inboundFlowControl;

    StreamFlowControl(ConnectionFlowControl.Type type,
                      int streamId,
                      int initialWindowSize,
                      int maxFrameSize,
                      ConnectionFlowControl connectionFlowControl,
                      BiConsumer<Integer, Http2WindowUpdate> writeUpdate) {

        outboundFlowControl =
                new FlowControlImpl.Outbound(type,
                                             streamId,
                                             connectionFlowControl);

        inboundFlowControl =
                new FlowControlImpl.Inbound(type,
                                            streamId,
                                            initialWindowSize,
                                            maxFrameSize,
                                            connectionFlowControl.inbound(),
                                            writeUpdate);
    }

    /**
     * Outbound flow control, ensures that no more than requested
     * amount of data is sent to the other side.
     *
     * @return outbound flow control
     */
    public FlowControl.Outbound outbound() {
        return outboundFlowControl;
    }

    /**
     * Inbound flow control, monitors received but by handler unconsumed data and requests
     * more when there is enough space in the buffer.
     *
     * @return inbound flow control
     */
    public FlowControl.Inbound inbound() {
        return inboundFlowControl;
    }
}
