'use strict'

describe 'Controller: RouteparamsCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  RouteparamsCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    RouteparamsCtrl = $controller 'RouteparamsCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
