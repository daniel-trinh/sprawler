'use strict';

angular.module('zippyApp')
  .directive('zippy', () ->
    # transclusion appends the contents within the usage of this directive 
    transclude: true
    template: """
      <div>
        <h3 ng-click="toggleContent()">{{title}}</h3>
      </div>
      <div ng-show="isContentVisible" ng-transclude></div>
    """
    restrict: 'E'
    link: (scope) ->
      scope.isContentVisible = false
      scope.toggleContent = () ->
        scope.isContentVisible = !scope.isContentVisible
    scope: {
      title: "@"
    }
  )