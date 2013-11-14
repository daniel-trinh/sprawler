'use strict'

angular.module('uiApp')
  .controller 'AvengersCtrl', ($scope, Avengers) ->
    $scope.avengers = Avengers