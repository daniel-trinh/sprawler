'use strict'

describe 'Filter: reverse', () ->

  # load the filter's module
  beforeEach module 'uiApp'

  # initialize a new instance of the filter before each test
  reverse = {}
  beforeEach inject ($filter) ->
    reverse = $filter 'reverse'

  it 'should reverse the input', () ->
    text           = 'angularjs'
    reversed       = 'sjralugna'
    expect(reverse text).toBe (reversed)
    expect(reverse reversed).toBe (text)