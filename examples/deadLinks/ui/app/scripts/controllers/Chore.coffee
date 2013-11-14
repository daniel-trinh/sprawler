'use strict'

angular.module('choreApp')
  .controller 'ChoreCtrl', ($scope) ->
    $scope.logChore = (chore) ->
      alert("#{chore} completed")
