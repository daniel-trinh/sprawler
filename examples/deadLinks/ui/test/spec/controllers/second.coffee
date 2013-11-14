'use strict'

describe 'Controller: SecondCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  SecondCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    SecondCtrl = $controller 'SecondCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
