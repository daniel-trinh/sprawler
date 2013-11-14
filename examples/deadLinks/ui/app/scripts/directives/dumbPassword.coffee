'use strict';

angular.module('zippyApp')
  .directive('dumbPassword', -> 
    validElement = angular.element("<div>{{password.input}}</div>")
    lastValue = ""
    link = (scope, elem) ->
      scope.$watch("password.input", (value) ->
        if value == "password"
          lastValue = value
          validElement.toggleClass("alert-box alert")
        else if lastValue == "password" && value != "password"
          lastValue = value
          validElement.toggleClass("alert-box alert")
      )

    {
      template: """
        <div>
          <input type="text" ng-model="password.input" class="ng-valid ng-dirty">
          <div class="ng-binding"></div>
        </div>
      """
      scope: {}
      restrict: 'E'
      compile: (tElem) -> 
        tElem.append(validElement)
        link
    }
)