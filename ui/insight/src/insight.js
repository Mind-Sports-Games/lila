var m = require('mithril');

var ctrl = require('./ctrl');
var view = require('./view');

export function PlayStrategyInsight(element, opts) {
  var controller = new ctrl(opts, element);

  m.module(element, {
    controller: function () {
      return controller;
    },
    view: view,
  });

  return controller;
}

window.PlayStrategyInsight = PlayStrategyInsight; // esbuild
