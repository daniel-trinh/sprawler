'use strict'

describe 'Directive: container', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<container></container>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the container directive'
