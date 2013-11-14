'use strict'

describe 'Directive: templateCache', () ->

  # load the directive's module
  beforeEach module 'uiApp'

  scope = {}

  beforeEach inject ($controller, $rootScope) ->
    scope = $rootScope.$new()

  it 'should make hidden element visible', inject ($compile) ->
    element = angular.element '<template-cache></template-cache>'
    element = $compile(element) scope
    expect(element.text()).toBe 'this is the templateCache directive'
