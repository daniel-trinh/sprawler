'use strict'

describe 'Directive: enter', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<enter></enter>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the enter directive'
