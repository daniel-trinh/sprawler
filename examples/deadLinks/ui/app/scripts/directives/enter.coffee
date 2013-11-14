'use strict';

angular.module('uiApp')
  .directive('enter', () ->
    (scope, element, attrs) ->
      element.bind("mouseenter", -> 
        scope.$apply(attrs.enter)
      )
  )

angular.module('uiApp')
  .directive('leave', () ->
    (scope, element, attrs) ->
      element.bind("mouseleave", ->
        scope.$apply(attrs.leave) 
      )
  )