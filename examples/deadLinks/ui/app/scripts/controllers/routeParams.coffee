'use strict'

app = angular.module('zippyApp')

app.controller 'RouteparamsCtrl', ($scope, $routeParams) ->
  $scope.address =
    message: "Address: #{$routeParams.country} #{$routeParams.state} #{$routeParams.city}"
