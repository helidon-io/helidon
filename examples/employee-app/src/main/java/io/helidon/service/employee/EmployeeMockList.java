/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.employee;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

public class EmployeeMockList {

	private static final CopyOnWriteArrayList<Employee> eList = new CopyOnWriteArrayList<Employee>();


	private EmployeeMockList() {
		JsonbConfig config = new JsonbConfig().withFormatting(Boolean.TRUE);

    	Jsonb jsonb = JsonbBuilder.create(config);

		eList.addAll(jsonb.fromJson(EmployeeMockList.class.getResourceAsStream("/employees.json"), new CopyOnWriteArrayList<Employee>(){}.getClass().getGenericSuperclass()));
	}

	// Get thread safe ArrayList from here
	public static CopyOnWriteArrayList<Employee> getInstance() {
		EmployeeMockList employeeMockList = new EmployeeMockList();
		return eList;
	}

}
