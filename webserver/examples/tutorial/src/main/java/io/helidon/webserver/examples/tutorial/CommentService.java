/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.tutorial;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.ContentWriters;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.examples.tutorial.user.User;


/**
 * Basic service for comments.
 */
public class CommentService implements Service {

    private static final String ROOM_PATH_ID = "room-id";

    private final ConcurrentHashMap<String, List<Comment>> data = new ConcurrentHashMap<>();

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.get((req, res) -> {
                    // Register a publisher for comment
                    res.registerWriter(List.class, MediaType.TEXT_PLAIN.withCharset("UTF-8"), this::publish);
                    req.next();
                })
                    .get("/{" + ROOM_PATH_ID + "}", this::getComments)
                    .post("/{" + ROOM_PATH_ID + "}", this::addComment);
    }

    Flow.Publisher<DataChunk> publish(List<Comment> comments) {
        String str = comments.stream()
                .map(Comment::toString)
                .collect(Collectors.joining("\n"));
        return ContentWriters.charSequenceWriter(StandardCharsets.UTF_8)
                             .apply(str + "\n");
    }

    private void getComments(ServerRequest req, ServerResponse resp) {
        String roomId = req.path().param(ROOM_PATH_ID);
        //resp.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
        List<Comment> comments = getComments(roomId);
        resp.send(comments);
    }

    /**
     * Returns all comments for the room or an empty list if room doesn't exist.
     *
     * @param roomId a room ID
     * @return a list of comments
     */
    public List<Comment> getComments(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return Collections.emptyList();
        }

        List<Comment> result = data.get(roomId);
        return result == null ? Collections.emptyList() : result;
    }

    private void addComment(ServerRequest req, ServerResponse resp) {
        String roomId = req.path().param(ROOM_PATH_ID);
        User user = req.context()
                       .get(User.class)
                       .orElse(User.ANONYMOUS);
        req.content()
           .as(String.class)
           .thenAccept(msg -> addComment(roomId, user, msg))
           .thenRun(resp::send)
           .exceptionally(t -> {
               req.next(t);
               return null;
           });
    }

    /**
     * Adds new comment into the comment-room.
     *
     * @param roomName a name of the comment-room
     * @param user     a user who provides the comment
     * @param message  a comment message
     */
    public void addComment(String roomName, User user, String message) {
        if (user == null) {
            user = User.ANONYMOUS;
        }
        List<Comment> comments = data.computeIfAbsent(roomName, k -> Collections.synchronizedList(new ArrayList<>()));
        comments.add(new Comment(user, message));
    }

    /**
     * Represents a single comment.
     */
    public static class Comment {
        private final User user;
        private final String message;

        Comment(User user, String message) {
            this.user = user;
            this.message = message;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (user != null) {
                result.append(user.getAlias());
            }
            result.append(": ");
            result.append(message);
            return result.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Comment)) {
                return false;
            }

            Comment comment = (Comment) o;

            if (user != null ? !user.equals(comment.user) : comment.user != null) {
                return false;
            }
            return message != null ? message.equals(comment.message) : comment.message == null;
        }

        @Override
        public int hashCode() {
            int result = user != null ? user.hashCode() : 0;
            result = 31 * result + (message != null ? message.hashCode() : 0);
            return result;
        }
    }
}
