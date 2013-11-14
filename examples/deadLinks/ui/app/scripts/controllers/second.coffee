'use strict'

angular.module('uiApp')
  .controller 'SecondCtrl', ($scope, Data) ->
    $scope.awesomeThings = [
      'HTML5 Boilerplate'
      'AngulaasdfrJS'
      'Karma'
    ]
    $scope.data = Data
    # $scope.reversedMessage = (message) ->
    # 	$scope.data.message.split("").reverse().join("")