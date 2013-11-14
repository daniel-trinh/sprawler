'use strict'
app = angular.module('zippyApp')
app.controller 'PromisesCtrl', ($scope, $route, $q) ->
    
    console.log($route)

    defer = $q.defer()
    defer.promise
      .then (weapon) ->
        alert("Thanks for giving me your " + weapon)
        return "bow"
      .then (weapon) ->
        alert("WAPON! " + weapon)
        return "axe"
      .then (weapon) ->
        alert("And my " + weapon)
    defer.resolve("sword")

    $scope.model = 
      message: "this is my app"

app.controller "RouteChangeWatcherCtrl", ($rootScope) ->
