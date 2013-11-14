'use strict';

angular.module('zippyApp')
  .directive('clock', () ->
    template: '<div>12:00pm {{timezone}}</div>'
    restrict: 'E'
    scope: 
      timezone: "@"
  )

angular.module('zippyApp')
  .directive('panel', () ->
    restrict: 'E'
    transclude: true
    scope:
      title: "@"
    template: """
      <div style='border: 3px solid #000000'>
        <div class="alert-box">
          {{title}}
        </div>
        <div ng-transclude></div>
      </div>
    """
  )