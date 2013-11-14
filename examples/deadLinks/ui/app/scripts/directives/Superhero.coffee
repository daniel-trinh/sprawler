'use strict';

angular.module('uiApp')
  .directive('superman', () ->
    template: '<div>Here I am to save the day</div>'
    restrict: 'A'
    link: (scope, element, attrs) ->
      alert("I'm working stronger")
      element.text 'this is the superman directive'
  )


angular.module('uiApp')
  .directive('flash', () ->
    restrict: 'A'
    link: (scope, element, attrs) ->
      alert("I'm working faster")
      element.text 'this is the flash directive'
  )


angular.module('uiApp')
  .directive('superhero', () ->
    restrict: 'E'
    scope: {}
    controller: ($scope) ->
      $scope.abilities = []

      @addStrength = () ->
        $scope.abilities.push("strength")

      @addSpeed = () ->
        $scope.abilities.push("speed")
        
      @addFlight = () ->
        $scope.abilities.push("flight")
        
    link: (scope, element, attrs) ->
      element.bind("mouseenter", () ->
        console.log(scope.abilities)
      )
  )

angular.module("uiApp")
  .directive('strength', () ->
    require: 'superhero'
    link: (scope, element, attrs, heroCtrl) -> 
      heroCtrl.addStrength()
  )

angular.module("uiApp")
  .directive('speed', () ->
    require: 'superhero'
    link: (scope, element, attrs, heroCtrl) -> 
      heroCtrl.addSpeed()
  )

angular.module("uiApp")
  .directive('flight', () ->
    require: 'superhero'
    link: (scope, element, attrs, heroCtrl) -> 
      heroCtrl.addFlight()
  )