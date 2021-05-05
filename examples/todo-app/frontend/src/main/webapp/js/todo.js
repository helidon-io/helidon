/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

// Author: Santiago Pericas-Geertsen <santiago.pericasgeertsen@oracle.com>

const ko = require('knockout');
const global = require('./global.js');
const rest = require('./rest.js');
const TodoUtil = require('./todoutil.js');

// Create view model for TODO app
var viewmodel = function (todos) {
    var self = this;
    self.todos = ko.observableArray(ko.utils.arrayMap(todos, function (todo) {
        return TodoUtil.toObservable(todo);
    }));
    self.current = ko.observable();
    self.showSpinner = ko.observable(true);
    // Add a new todo
    self.add = function () {
        var current = self.current().trim();
        if (current) {
            var newTodo = {title: current, completed: false};
            rest.post(window.location.pathname + "api/todo", newTodo)
                    .done(function (todo) {
                        self.todos.push(TodoUtil.toObservable(todo));
                        self.current('');
                    })
                    .fail(function (err) {
                        console.log(err.statusText)
                    });
        }
    };
    // Remove an existing todo using its server-generated ID
    self.remove = function (todo) {
        rest.delete(window.location.pathname + "api/todo/" + todo.id())
                .done(function () {
                    self.todos.remove(todo);
                })
                .fail(function (err) {
                    console.log(err.statusText);
                });
    };
    // Remove all completed todos
    self.removeCompleted = function () {
        ko.utils.arrayForEach(self.todos(), function (todo) {
            if (todo.completed()) {
                self.remove(todo);
            }
        });
    };
    // Begin editing an item in the UI
    self.editItem = function (todo) {
        todo.editing(true);
        todo.previousTitle = todo.title();
    };
    // Stop editing an item in the UI
    self.stopEditing = function (todo) {
        todo.editing(false);
        var title = todo.title();
        var trimmedTitle = title.trim();
        if (!trimmedTitle) {
            self.remove(todo);
        } else {
            if (title !== trimmedTitle) {
                todo.title(trimmedTitle);
            }

            rest.put(window.location.pathname + "api/todo/" + todo.id(), TodoUtil.fromObservable(todo))
                    .fail(function (err) {
                        console.log(err.statusText);
                    });
        }
    };
    // Cancel editing an item in the UI
    self.cancelEditing = function (todo) {
        todo.editing(false);
        todo.title(todo.previousTitle);
    };
    // Returns the number of todos completed from cache
    self.completedCount = ko.computed(function () {
        return ko.utils.arrayFilter(self.todos(), function (todo) {
            return todo.completed();
        }).length;
    });
    // Returns the number of todos not completed from cache
    self.remainingCount = ko.computed(function () {
        return self.todos().length - self.completedCount();
    });
    // Handle all completed toggle
    self.allCompleted = ko.computed({
        read: function () {
            return !self.remainingCount();
        },
        write: function (newValue) {
            ko.utils.arrayForEach(self.todos(), function (todo) {
                todo.completed(newValue);
                rest.put(window.location.pathname + "api/todo/" + todo.id(), TodoUtil.fromObservable(todo))
                        .fail(function (err) {
                            console.log(err.statusText);
                        });
            });
        }
    });
    // Utility function
    self.getLabel = function (count) {
        return ko.utils.unwrapObservable(count) === 1 ? 'item' : 'items';
    };
};

module.exports = viewmodel;
