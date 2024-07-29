
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
