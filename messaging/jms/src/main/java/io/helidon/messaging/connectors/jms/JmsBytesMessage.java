/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageEOFException;

import io.helidon.messaging.MessagingException;

public class JmsBytesMessage extends AbstractJmsMessage<byte[]> {

    private final javax.jms.BytesMessage msg;

    JmsBytesMessage(javax.jms.BytesMessage msg, Executor executor, SessionMetadata sharedSessionEntry) {
        super(executor, sharedSessionEntry);
        this.msg = msg;
    }

    @Override
    public Message getJmsMessage() {
        return msg;
    }

    /**
     * Return InputStream which is able to read JMS ByteMessage.
     *
     * @return InputStream supplying bytes from received JMS ByteMessage.
     */
    public InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    return msg.readByte();
                } catch (MessageEOFException e) {
                    return -1;
                } catch (JMSException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    @Override
    public byte[] getPayload() {
        try {
            byte[] bytes = new byte[(int) msg.getBodyLength()];
            msg.readBytes(bytes);
            return bytes;
        } catch (JMSException e) {
            throw new MessagingException("Error when reading BytesMessage", e);
        }
    }

}
