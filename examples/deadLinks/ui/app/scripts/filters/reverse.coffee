'use strict';

angular.module('uiApp')
  .filter 'reverse', () ->
    (text) ->
      text.split("").reverse().join("")