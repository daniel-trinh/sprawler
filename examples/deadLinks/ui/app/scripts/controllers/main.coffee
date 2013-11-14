'use strict'

angular.module('zippyApp')
  .controller 'MainCtrl', ($rootScope, $scope, $route, $location) ->
    $rootScope.$on "$routeChangeError", (event, current, previous, rejection) ->
      console.log(event, current, previous, rejection)
      console.log($rootScope, $scope, $route, $location)
    $rootScope.$on "$routeChangeStart", (event, current, previous, rejection) ->
      console.log(event, current, previous, rejection)
      console.log($rootScope, $scope, $route, $location)
    $rootScope.$on "$routeChangeSuccess", (event, current, previous, rejection) ->
      console.log(event, current, previous, rejection)
      console.log($rootScope, $scope, $route, $location)