'use strict'

describe 'Controller: WebcrawlerCtrl', () ->

  # load the controller's module
  beforeEach module 'uiApp'

  WebcrawlerCtrl = {}
  scope = {}

  # Initialize the controller and a mock scope
  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()
    WebcrawlerCtrl = $controller 'WebcrawlerCtrl', {
      $scope: scope
    }

  it 'should attach a list of awesomeThings to the scope', () ->
    expect(scope.awesomeThings.length).toBe 3
