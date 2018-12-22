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

(function () {
  // new module with no dependencies
  var app = angular.module('g', []);

  app.controller('GoogleController', ['$http', function ($http) {
    this.callBackend = function () {
      var accessToken = document.getElementById('gat_input').value;

      if (accessToken === "") {
        console.log("No access token, not calling backend");
        alert("Please login before calling backend service");
        return;
      }
      console.log("Submit attempt to server: " + accessToken);

      $http({
        method: 'GET',
        url: '/rest/profile',
        headers: {
          'Authorization': "Bearer " + accessToken
        }
      }).success(function (data, status, headers, config) {
        console.log('Successfully sent data to backend, received' + data);
        alert(data);
      }).error(function (data, status, headers, config) {
        console.log('Failed to send data to backend. Status: ' + status);
        alert(status + ", auth header: " + headers('WWW-Authenticate') + ", error: " + data);
      });
    }
  }]);
})();
