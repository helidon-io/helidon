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

require.config({
  paths: {
    knockout: '../node_modules/knockout/build/output/knockout-latest',
    jquery: 'https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery',
    gapi: '//apis.google.com/js/platform'
  },
  shim: {
    gapi: {
      exports: 'gapi'
    }
  }
});

require([
  'knockout',
  'config/global',
  'viewmodels/todo',
  'util/todoutil',
  'util/rest',
  'gapi',
  'extends/handlers',
  'jquery'
], function (ko, global, TodoViewModel, TodoUtil, rest, gapi, handlers, jquery) {
  'use strict';

  // Initialize ko handler for spinner
  var displayValue = function (element, valueAccessor) {
    var value = ko.utils.unwrapObservable(valueAccessor());
    var isCurrentlyVisible = !(element.style.display == "none");
    if (value && !isCurrentlyVisible) {
      element.style.display = "";
    }
    else if (!value && isCurrentlyVisible) {
      element.style.display = "none";
    }
  };
  ko.bindingHandlers.spinner = {
    'init': function (element, valueAccessor) {
      jquery(element)
        .append(
          '<div class="circle circle1 circle1-1"><div class="circle circle1 circle2-1">' +
          '<div class="circle circle1 circle3-1"></div></div></div>');
      displayValue(element, valueAccessor);
    },
    'update': function (element, valueAccessor) {
      displayValue(element, valueAccessor);
    }
  };

  // Initialize and bind view model
  var viewModel = new TodoViewModel([]);
  ko.applyBindings(viewModel);

  // Function to refresh list of todos given a user token
  var refreshTodos = function (id_token) {
    global.id_token = id_token;
    console.log('id_token', id_token);

    // Get all todos from server
    rest.get("/api/todo")
      .done(function (todos) {
        console.log("todos = " + JSON.stringify(todos));
        viewModel.todos.removeAll();
        todos.forEach(function (todo) {
          viewModel.todos.push(TodoUtil.toObservable(todo))
        });
        viewModel.showSpinner(false);
      })
      .fail(function (err) {
        console.log(err.statusText);
      });
  };

  // Load oauth library and find user token
  gapi.load('auth2', function () {
    gapi.auth2.init({
      client_id: global.GOOGLE_SIGN_IN_CLIENT_ID
    }).then(function () {
      var isSignedIn = gapi.auth2.getAuthInstance().isSignedIn.get();
      if (isSignedIn) {
        var id_token = gapi.auth2.getAuthInstance().currentUser.get().getAuthResponse().id_token;
        refreshTodos(id_token);
      } else {
        viewModel.current("(Use button below to sign in using Google)");
      }
      window.gapi.auth2.getAuthInstance().currentUser.listen(function (user) {
        var id_token = user.getAuthResponse().id_token;
        refreshTodos(id_token);
        viewModel.current("");
      });
    });
  });
});
