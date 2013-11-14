'use strict'

describe 'Controller: PromisesCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  PromisesCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    PromisesCtrl = $controller 'PromisesCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
