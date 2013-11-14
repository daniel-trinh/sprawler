'use strict'

describe 'Directive: dumbPassword', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<dumb-password></dumb-password>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the dumbPassword directive'
