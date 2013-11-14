'use strict'

describe 'Service: game', () ->

  # load the service's module
  beforeEach module 'uiApp'

  # instantiate service
  game = {}
  beforeEach inject (_game_) ->
    game = _game_

  it 'should do something', () ->
    expect(!!game).toBe true
