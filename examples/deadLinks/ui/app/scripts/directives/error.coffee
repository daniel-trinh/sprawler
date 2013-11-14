'use strict';

angular.module('zippyApp')
  # Must be used in html that is already loaded into the page.
  # If $routeChangeError is triggered and <error></error> is used
  # in the templateUrl specified in the route, it will load load and
  # therefore not display the error div when the event is triggered.
  .directive('error', ($rootScope) ->
    template: """
      <div class="alert-box alert" ng-show="isError">
        Error!!!!!!!  
      </div>
      """
    restrict: 'E'
    link: (scope, element, attrs) ->
      $rootScope.$on "$routeChangeError", (event, current, previous, rejection) ->
        scope.isError = true
  )