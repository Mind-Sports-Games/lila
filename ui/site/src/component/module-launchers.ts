import StrongSocket from './socket';

const li: any = playstrategy;

export default function moduleLaunchers() {
  if (li.userAnalysis) startUserAnalysis(li.userAnalysis);
  else if (li.study || li.practice || li.relay) startAnalyse(li.study || li.practice || li.relay);
}

function startUserAnalysis(cfg: any) {
  cfg.$side = $('.analyse__side').clone();
  startAnalyse(cfg);
}

function startAnalyse(cfg: any) {
  playstrategy.socket = new StrongSocket(cfg.socketUrl || '/analysis/socket/v5', cfg.socketVersion, {
    receive: (t: string, d: any) => analyse.socketReceive(t, d),
  });
  cfg.socketSend = li.socket.send;
  console.log('module-launchers : startAnalyse');
  const analyse = window.PlayStrategyAnalyse(cfg);
}
