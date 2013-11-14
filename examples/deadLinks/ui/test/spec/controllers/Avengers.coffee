'use strict'

describe 'Controller: AvengersCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  AvengersCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    AvengersCtrl = $controller 'AvengersCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
