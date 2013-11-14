'use strict'

describe 'Directive: kid', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<kid></kid>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the kid directive'
