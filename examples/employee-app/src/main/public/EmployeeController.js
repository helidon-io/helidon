
      function bindDetail(element, employee){
    	 alert("here");
        element.find(".backButton").on("click", function(){
          $("#detail").hide(400, "swing", function(){ $("#people").show(400, "swing")});
        });
        element.find(".deleteButton").on("click", function(){
          $('<div></div>').dialog({
            modal: true,
            title: "Confirm Delete",
            open: function() {
              var markup = 'Are you sure you want to delete '+employee.firstName+' ' + employee.lastName +"?";
              $(this).html(markup);
            },
            buttons: {
              Ok: function() {
                $("#detail").html("DELETING...");
                $( this ).dialog( "close" );
                $.ajax({
                  url:server +"employees/"+employee.id,
                  method:"DELETE"
                  }).done(function(data){
                    $("#detail").hide();
                    loadEmployees();
                  });
              },
              Cancel: function(){
                $( this ).dialog( "close" );
              }
            }
          });
        });
        element.find(".editButton").on("click",function(){
          $("#editFirstName").val(employee.firstName);
          $("#editLastName").val(employee.lastName);
          $("#editEmail").val(employee.email);
          $("#editPhone").val(employee.phone);
          $("#editBirthDate").val(employee.birthDate);
          $("#editTitle").val(employee.title);
          $("#editDept").val(employee.department);

          $('#editDialog').dialog({
            modal:true,
            title: employee.firstName+' ' + employee.lastName,
            buttons: {
              "Update": function(){
                var editEmployee={
                  firstName:$("#editFirstName").val(),
                  lastName:$("#editLastName").val(),
                  email:$("#editEmail").val(),
                  phone:$("#editPhone").val(),
                  birthDate:$("#editBirthDate").val(),
                  title:$("#editTitle").val(),
                  dept:$("#editDept").val()
                };
                $("#detail").html("UPDATING...");
                $( this ).dialog( "close" );
                $.ajax({
                  url:server +"employees/"+employee.id,
                  method:"PUT",
                  data:JSON.stringify(editEmployee),
                  contentType: 'application/json',
                  }).done(function(data){
                    $("#detail").hide();
                    loadEmployees();
                });
              },
              Cancel: function() {
                $(this).dialog( "close" );
              }
            }
          });
        });
      }

      $("#addButton").button().on("click", function(){

          $("#addFirstName").val("");
          $("#addLastName").val("");
          $("#addEmail").val("");
          $("#addPhone").val("");
          $("#addBirthDate").val("");
          $("#addTitle").val("");
          $("#addDept").val("");

        $("#addDialog").dialog({
            modal:true,
            title: "Add new employee",
            buttons:{
              "Add":function(){
                var addEmployee={
                  firstName:$("#addFirstName").val(),
                  lastName:$("#addLastName").val(),
                  email:$("#addEmail").val(),
                  phone:$("#addPhone").val(),
                  birthDate:$("#addBirthDate").val(),
                  title:$("#addTitle").val(),
                  dept:$("#addDept").val()
                };
                $("#detail").html("ADDING...");
                $( this ).dialog( "close" );
                $.ajax({
                  url:server +"employees",
                  method:"POST",
                  data:JSON.stringify(addEmployee),
                  contentType: 'application/json',
                  }).done(function(data){
                    $("#detail").hide();
                    loadEmployees();
                });
              },
              "Cancel":function(){
                $(this).dialog( "close" );
              }
            }
        });
      });

      $("#searchButton").button().on("click", function(){
        var searchTerm =$("#searchText").val().trim();
        if(searchTerm!=""){
          $("#people").show();
          $("#people").html("SEARCHING...");
          $.ajax({
            url:server+"employees/"+ $("#searchType").val()+"/"+encodeURIComponent(searchTerm),
            method:"GET"
          }).done(function(data){
            $("#people").empty();
            $("#people").hide();
            if(data.length==0){
              $("#people").html("No results found...");
            }else{
              data.forEach(function(employee){
                var item = $(peopleTemplate.render(employee));
                item.on("click", function(){
                  var detailItem = $(detailTemplate.render(employee));
                  $("#detail").empty();
                  $("#detail").append(detailItem);
                  bindDetail(detailItem, employee);
                  $("#people").hide(400, "swing", function(){ $("#detail").show(400, "swing")});
                });
                $("#people").append(item);
              });
            }
            $("#people").show(400, "swing");
          });
        }else{
          loadEmployees();
        }
      });
      $("#searchText").on("keyup", function(e){
        if(e.keyCode == 13){
          $("#searchButton").trigger("click");
        }
      });

      function loadEmployees(){
          $("#people").show();
          $("#people").html("LOADING...");
          $.ajax({
  		  dataType: "json",
            url:server +"employees",
            method:"GET"
          }).done(function(data){
            $("#people").hide();
            $("#people").empty();
            data.forEach(function(employee){
              var item = $(peopleTemplate.render(employee));
              item.on("click", function(){
                var detailItem = $(detailTemplate.render(employee));
                $("#detail").empty();
                $("#detail").append(detailItem);
                bindDetail(detailItem, employee);
                $("#people").hide(400, "swing", function(){ $("#detail").show(400, "swing")});
              });
              $("#people").append(item);
            })
            $("#people").show(400, "swing");
          });
        }
      

   