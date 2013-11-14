'use strict'

describe 'Directive: drink', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<drink></drink>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the drink directive'
