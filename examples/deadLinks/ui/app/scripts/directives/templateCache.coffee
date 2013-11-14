'use strict'

app = angular.module('zippyApp')

app.run ($templateCache) ->
  $templateCache.put("zippy.html", '<div class="derp" ng-transclude>this is a template cache</div>')

app.directive('templateCache', ($templateCache) ->
    console.log($templateCache.get("zippy.html"))
    {
      templateUrl: "zippy.html"
      scope:       {}
      transclude:  true
      restrict:    'E'
    }
  )
