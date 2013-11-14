'use strict'

angular.module('zippyApp')
  .controller 'GameCtrl', ($scope, game) ->
    $scope.title = game.title