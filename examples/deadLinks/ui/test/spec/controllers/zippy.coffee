'use strict'

describe 'Controller: ZippyCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  ZippyCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    ZippyCtrl = $controller 'ZippyCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
