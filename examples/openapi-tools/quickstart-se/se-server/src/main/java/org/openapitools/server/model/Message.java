/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package org.openapitools.server.model;

/**
 * An message for the user
 */
public class Message {

    private String message;
    private String greeting;

    /**
     * Default constructor.
     */
    public Message() {
        // JSON-B / Jackson
    }

    /**
     * Create Message.
     *
     * @param message  message
     * @param greeting greeting
     */
    public Message(
            String message,
            String greeting
    ) {
        this.message = message;
        this.greeting = greeting;
    }


    /**
     * Get message
     *
     * @return message
     */
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Get greeting
     *
     * @return greeting
     */
    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    /**
     * Create a string representation of this pojo.
     **/
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Message {\n");

        sb.append("    message: ").append(toIndentedString(message)).append("\n");
        sb.append("    greeting: ").append(toIndentedString(greeting)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private static String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

