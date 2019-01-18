/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

define([
  'knockout',
  'util/rest'
], function (ko, rest) {
  'use strict';

  var TodoUtil = {
    // Map todo object to observable
    toObservable: function (todo) {
      var obs = {
        id: ko.observable(todo.id),
        title: ko.observable(todo.title),
        completed: ko.observable(todo.completed),
        editing: ko.observable(false)
      };

      // Subscribe to changes on completed and update server
      obs.completed.subscribe(function () {
        rest.put("/api/todo/" + obs.id(), TodoUtil.fromObservable(obs))
          .fail(function (err) {
            console.log(err.statusText);
          });
      });

      return obs;
    },

    // Map observable to todo object
    fromObservable: function (obs) {
      var todo = {
        title: obs.title(),
        completed: obs.completed()
      };
      if (obs.id()) {
        todo.id = obs.id();
      }
      return todo;
    }
  };

  return TodoUtil;
});
