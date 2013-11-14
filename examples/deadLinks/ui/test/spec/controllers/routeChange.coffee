'use strict'

describe 'Controller: RoutechangeCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  RoutechangeCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    RoutechangeCtrl = $controller 'RoutechangeCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
