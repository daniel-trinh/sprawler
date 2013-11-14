'use strict';

angular.module('choreApp')
  .directive('kid', () ->
    template: """
      <input type="text" ng-model="chore"> 
        {{chore}}
      <div class="btn" ng-click="done({chore: chore})">
        I'm done!
      </div>
    """
    restrict: 'E'
    scope: {
      done: "&"
    }
  )