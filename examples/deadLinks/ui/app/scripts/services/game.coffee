'use strict';
app = angular.module('zippyApp')

app.provider "game", ->
  type = ""
  setType: (value) ->
    type = value
  $get: ->
    title: type + "Craft"

app.config (gameProvider) ->
  gameProvider.setType("Google")
