'use strict'

webcrawlerApp = angular.module("webcrawlerApp", [])
  .config ($routeProvider) ->
    $routeProvider
      .when '/',
        templateUrl: 'views/webcrawler.html'
        controller: 'WebcrawlerCtrl'
        
uiApp = angular.module('uiApp', [
  'ngCookies',
  'ngResource',
  'ngSanitize',
  'ngRoute'
])
  .config ($routeProvider) ->
    $routeProvider
      .when '/',
        templateUrl: 'views/eggheadTutorial.html'
        controller: 'EggheadtutorialCtrl'
      .when '/superhero',
        templateUrl: 'views/superhero.html'
        controller: 'EggheadtutorialCtrl'
      .when '/twitter',
        templateUrl: 'views/twitter.html'
        controller: 'TwitterCtrl'
      .otherwise
        redirectTo: '/'

uiApp.factory('Data', ->
  { message: "I am data from a services i like to shot web!!!!!!!" }
)

uiApp.factory('Avengers', ->
  Avengers = {}
  
  Avengers.cast = [{
    name: "Steve",
    character: "Johnny"
  }, {
    name: "Donut"
    character: "Meat"
  }, {
    name: "Abby",
    character: "Son Goku"
  }]
  Avengers
)

choreApp = angular.module('choreApp', [])
  .config ($routeProvider) ->
    $routeProvider
      .when '/',
        templateUrl: 'views/chore.html'
        controller: 'ChoreCtrl'
      .otherwise
        redirectTo: '/'


zippyApp = angular.module('zippyApp', [])
  .config ($routeProvider) ->
    $routeProvider
      .when '/',
        templateUrl: 'views/zippy.html'
        controller:  'ZippyCtrl'
      .when '/map/:country/:state/:city',
        templateUrl: "promises.html"
        controller:  "RouteparamsCtrl"
      .when '/routeChange',
        templateUrl: "views/routeChange.html"
        controller: "RoutechangeCtrl"
        resolve: {
          loadData: ($q, $timeout) ->
            defer = $q.defer()
            $timeout () ->
              defer.resolve("some data")
            , 500
            defer.promise
        }
      .when '/container',
        templateUrl: 'views/container.html'
        controller: "GameCtrl"
      .when '/game',
        templateUrl: "views/game.html"
      .when "/promises",
        templateUrl: "views/promises.html"
        controller: "PromisesCtrl"
        resolve:
          prepData: ($q, $timeout) ->
            defer = $q.defer()
            $timeout () ->
              defer.resolve("prepData")
            , 500
            defer.promise
          loadData: ($q, $timeout) ->
            defer = $q.defer()
            $timeout () ->
              defer.reject("Your network is down!")
            , 500
            defer.promise
      .otherwise
        redirectTo: '/'


zippyApp.config ($logProvider) ->
  $logProvider.logEnabled(false)

zippyApp.run ($rootScope, $log) ->
  $rootScope.$log = $log
  $log.log("WTF")

zippyApp.run ($templateCache) ->
  $templateCache.put("routeParams.html", """
    <div class="derp">{{address.message}} hi</div>
  """
  )