'use strict'

angular.module('uiApp')
  .controller 'TwitterCtrl', ($scope) ->
    $scope.loadMoreTweets = () ->
      alert("Loading Tweets!")
    $scope.deleteTweets = () ->
      alert("Deleting Tweets!")