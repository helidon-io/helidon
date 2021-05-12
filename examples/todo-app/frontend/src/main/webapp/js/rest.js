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

const jquery = require('jquery');
const global = require('./global.js');

function init() {
    var rest = {};
    jquery.each(["post", "get", "put", "delete"], function (i, method) {
        rest[method] = function (url, data, callback) {
            var settings = {
                url: url,
                type: method,
                dataType: "json",
                data: JSON.stringify(data),
                headers: {
                    Accept: "application/json",
                    "Authorization": "Bearer " + global.id_token
                },
                success: callback
            };
            if (method === "post" || method === "put") {
                settings.contentType = "application/json";
            }
            return jquery.ajax(settings);
        };
    });
    return rest;
}
module.exports = init();
