/* Copyright 2018 Oracle and/or its affiliates. All rights reserved. */

var app = angular.module('demo', [])

.controller('Employees', function($scope, $http) {
	$scope.urlService = "/employees/";
	$scope.showListEmployes = true;
	$scope.showPhoto = false;
	$scope.photo = false;
	$scope.showDivs = function(employeeList){	
		if (employeeList){
			$scope.showNewForm = false;
			$scope.showListEmployes = true;			
		}else{
			$scope.formTitle = "Add New Employee"
			$scope.id = "";
			$scope.firstName = "";
			$scope.lastName = "";
			$scope.birthDate = "";
			$scope.phone = "";
			$scope.email = "";
			$scope.title = "";
			$scope.department = "";
			$scope.showPhoto = false;
			angular.element(document.getElementById("pic")).val(null);
			$scope.showNewForm = true;
			$scope.showListEmployes = false;		
		}
	}
	
	$scope.reset = function(form) {
    if (form) {
      form.$setPristine();
      form.$setUntouched();
    }
  };
  
	//Get employee list
	$scope.getEmployees = function(){
		$http.get($scope.urlService).
			then(function(response) {
				$scope.employees = response.data;				
			});
		$scope.searchText = "";
	}
	
	//Get employee by ID
	$scope.getEmployeeById = function(id){
		$http.get($scope.urlService+"/"+id).
			then(function(response) {
				$scope.employee = response.data;
				$scope.showDetail();				
			});
		$scope.searchText = "";		
	}
	
	//Add/Update Employee	
	$scope.submitEmployee = function(){	
		var addEmployee={
				  id:$scope.id,
                  firstName:$scope.firstName,
                  lastName:$scope.lastName,
                  email:$scope.email,
                  phone:$scope.phone,
                  birthDate:$scope.birthDate,
                  title:$scope.title,
                  department:$scope.department
                };
		
		var res;
		if ($scope.id ==""){		
			res = $http.post($scope.urlService, JSON.stringify(addEmployee), {				
				headers: { 'Content-Type': 'application/json','Accept': 'application/json'}
				});
		}else{
			res = $http.put($scope.urlService+$scope.id, JSON.stringify(addEmployee), {
				headers: { 'Content-Type': 'application/json','Accept': 'application/json'}});
		}
		res.success(function(data, status, headers, config) {
			$scope.message = data;
			$scope.getEmployees();
			$scope.showDivs(true);
			
		});
		res.error(function(data, status, headers, config) {
			alert( "failure message: " + JSON.stringify({data: data}));
		});	
	
	}
	
	//Delete employee
	$scope.deleteEmployee = function() {
		res = $http.delete($scope.urlService+$scope.id,{headers: { 'Content-Type': 'application/json','Accept': 'application/json'}});
		res.success(function(data, status, headers, config) {
			$scope.getEmployees();
			$scope.showDivs(true);
			
		});
		res.error(function(data, status, headers, config) {
			alert( "failure message: " + JSON.stringify({data: data}));
		});	
	}
	
	//Search Employees
	$scope.searchEmployees = function (){
		$http.get($scope.urlService+$scope.searchType+"/"+$scope.searchText).
			then(function(response) {
				$scope.employees = response.data;
			});
	}
	
	$scope.showDetail = function(){		
		$scope.showDivs(false);
		$scope.showPhoto = true;
		$scope.formTitle = "Update Employee"
		$scope.id = $scope.employee.id;
		$scope.photo = $scope.employee.photo;
		$scope.firstName = $scope.employee.firstName;
		$scope.lastName = $scope.employee.lastName;
		$scope.birthDate = $scope.employee.birthDate;
		$scope.phone = $scope.employee.phone;
		$scope.email = $scope.employee.email;
		$scope.title = $scope.employee.title;
		$scope.department = $scope.employee.department;
	}
	

});

