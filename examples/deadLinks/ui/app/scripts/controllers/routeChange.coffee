'use strict'

angular.module('zippyApp')
  .controller 'RoutechangeCtrl', ($scope, loadData, $template) ->
    console.log($scope, loadData, $template)