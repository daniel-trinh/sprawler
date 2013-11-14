'use strict'

describe 'Controller: EggheadtutorialCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  EggheadtutorialCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    EggheadtutorialCtrl = $controller 'EggheadtutorialCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
