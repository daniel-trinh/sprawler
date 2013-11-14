'use strict'

describe 'Controller: GameCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  GameCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    GameCtrl = $controller 'GameCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
