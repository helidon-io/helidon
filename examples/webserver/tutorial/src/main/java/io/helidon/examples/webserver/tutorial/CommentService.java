/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.http.HttpMediaTypes;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Basic service for comments.
 */
class CommentService implements HttpService {

    private final ConcurrentHashMap<String, List<Comment>> data = new ConcurrentHashMap<>();

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{room-id}", this::getComments)
                .post("/{room-id}", this::addComment);
    }

    private void getComments(ServerRequest req, ServerResponse resp) {
        String roomId = req.path().pathParameters().value("room-id");
        resp.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
        resp.send(getComments(roomId));
    }

    /**
     * Returns all comments for the room or an empty list if room doesn't exist.
     *
     * @param roomId a room ID
     * @return a list of comments
     */
    List<Comment> getComments(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return Collections.emptyList();
        }

        List<Comment> result = data.get(roomId);
        return result == null ? Collections.emptyList() : result;
    }

    private void addComment(ServerRequest req, ServerResponse res) {
        String roomId = req.path().pathParameters().value("room-id");
        User user = req.context().get(User.class).orElse(User.ANONYMOUS);
        String msg = req.content().as(String.class);
        addComment(roomId, user, msg);
        res.send();
    }

    /**
     * Adds new comment into the comment-room.
     *
     * @param roomName a name of the comment-room
     * @param user     a user who provides the comment
     * @param message  a comment message
     */
    void addComment(String roomName, User user, String message) {
        if (user == null) {
            user = User.ANONYMOUS;
        }
        List<Comment> comments = data.computeIfAbsent(roomName, k -> Collections.synchronizedList(new ArrayList<>()));
        comments.add(new Comment(user, message));
    }

}
