'use strict'

describe 'Controller: ChoreCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  ChoreCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    ChoreCtrl = $controller 'ChoreCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
