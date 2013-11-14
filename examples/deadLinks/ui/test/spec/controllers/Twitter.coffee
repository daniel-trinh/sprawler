'use strict'

describe 'Controller: TwitterCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  TwitterCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    TwitterCtrl = $controller 'TwitterCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe
