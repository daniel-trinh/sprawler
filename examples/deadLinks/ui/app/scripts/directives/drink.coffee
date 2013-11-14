'use strict';

angular.module('choreApp')
  .directive('drink', () ->
    template: '<div>{{flavor}}</div>'
    scope: {
      flavor: "@"
    }
    restrict: "A"
    link: (scope, element, attrs) ->
      scope.flavor = attrs.flavor
  )
