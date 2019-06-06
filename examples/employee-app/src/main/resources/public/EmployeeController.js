/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
var server = "/";

function search (){
    var searchTerm = $("#searchText").val().trim();
    if (searchTerm != "") {
        $("#people").show();
        $("#people").html("SEARCHING...");
        $.ajax({
            url: server + "employees/" +
                $("#searchType").val() + "/" +
                encodeURIComponent(searchTerm),
            method: "GET"
        }).done(
            function(data) {
                $("#people").empty();
                $("#people").hide();
                if (data.length == 0) {
                    $("#people").html("");
                    $("#notFound").show();
                    $("#notFound").html("No people found matching your search criteria");
                } else {
                    showResults(data);
                }
                $("#people").show(400, "swing");
            });
    } else {
        loadEmployees();
    }
}

$(function() {   
    $("#searchText").on("keyup", function(e) {
        if (e.keyCode == 13) {
            search ();
        }
    });
});

function showResults(data){
    $("#people").hide();
    $("#people").empty();
    $("#notFound").hide();
    data.forEach(function(employee) {
        var item = $(renderEmployees(employee));
        item.on("click", function() {
            var detailItem = $(renderDetailEmployee(employee));
            $("#home").hide();
            $("#detail").empty();                                   
            $("#notFound").hide();
            $("#detail").append(detailItem);
            $("#people").hide(
                400,
                "swing",
                function() {
                    $("#detail").show()
                });
        });
        $("#people").append(item);
    });
}

function showEmployeeForm() {
    $("#notFound").hide();
    $("#editForm").hide();
    $("#deleteButton").hide();
    $("#employeeForm").show();
    $("#formTitle").text("Add Employee");
    $("#home").hide();
    $("#people").hide();
}

function loadEmployees() {
    $("#notFound").hide();
    $("#searchText").val("");
    $("#employeeForm").hide();
    $("#editForm").hide();
    $("#home").show();
    $("#people").show();
    $("#people").html("LOADING...");
    $.ajax({
        dataType: "json",
        url: server + "employees",
        method: "GET"
    }).done(function(data) {
        showResults(data); 
        $("#people").show(400, "swing");
    });
}


function renderEmployees(employee){
    var template = $('#employees_tpl').html();
    Mustache.parse(template);
    var rendered = Mustache.render(template, {
        "firstName" : employee.firstName,
        "lastName" : employee.lastName,
        "title" : employee.title,
        "department" : employee.department
    });
    return rendered;
}

function renderDetailEmployee(employee){
    var template = $('#detail_tpl').html();
    Mustache.parse(template);
    var rendered = Mustache.render(template,{
        "id" : employee.id,
        "firstName" : employee.firstName,
        "lastName" : employee.lastName,
        "email" : employee.email,
        "birthDate" : employee.birthDate,
        "phone" : employee.phone,
        "title" : employee.title,
        "department" : employee.department
    });
    return rendered;
}

function save() {
    var employee = {
        id: "",
        firstName: $("#firstName").val(),
        lastName: $("#lastName").val(),
        email: $("#email").val(),
        phone: $("#phone").val(),
        birthDate: $("#birthDate").val(),
        title: $("#title").val(),
        department: $("#department").val()
    };
    $.ajax({
        url: server + "employees",
        method: "POST",
        data: JSON.stringify(employee)
    }).done(function(data) {
        $("#detail").hide();
        $("#firstName").val("");
        $("#lastName").val("");
        $("#email").val("");
        $("#phone").val("");
        $("#birthDate").val("");
        $("#title").val("");
        $("#department").val("");
        loadEmployees();
    });

}

function updateEmployee() {
    var employee = {
        id: $("#editId").val(),
        firstName: $("#editFirstName").val(),
        lastName: $("#editLastName").val(),
        email: $("#editEmail").val(),
        phone: $("#editPhone").val(),
        birthDate: $("#editBirthDate").val(),
        title: $("#editTitle").val(),
        department: $("#editDepartment").val()
    };
    $("#detail").html("UPDATING...");
    $.ajax({
        url: server + "employees/" + employee.id,
        method: "PUT",
        data: JSON.stringify(employee)
    }).done(function(data) {
        $("#detail").hide();
        loadEmployees();
    });
}

function deleteEmployee() {
    var employee = {
        firstName: $("#editFirstName").val(),
        lastName: $("#editLastName").val(),
        id: $("#editId").val()
    };
    $('<div></div>').dialog({
        modal: true,
        title: "Confirm Delete",
        open: function() {
            var markup = 'Are you sure you want to delete ' +
                employee.firstName + ' ' + employee.lastName +
                " employee?";
            $(this).html(markup);
        },
        buttons: {
            Ok: function() {
                $("#detail").html("DELETING...");
                $(this).dialog("close");
                $.ajax({
                    url: server + "employees/" + employee.id,
                    method: "DELETE"
                }).done(function(data) {
                    $("#detail").hide();
                    loadEmployees();
                });
            },
            Cancel: function() {
                $(this).dialog("close");
            }
        }
    });

}