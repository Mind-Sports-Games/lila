import { AnalyseOpts } from './interfaces';
import { PlayStrategyAnalyse } from './analysisBoard';

export default function (cfg: AnalyseOpts) {
  playstrategy.socket = new playstrategy.StrongSocket(cfg.data.url.socket, cfg.data.player.version, {
    params: {
      userTv: cfg.data.userTv && cfg.data.userTv.id,
    },
    receive(t: string, d: any) {
      analyse.socketReceive(t, d);
    },
  });
  cfg.$side = $('.analyse__side').clone();
  cfg.$underboard = $('.analyse__underboard').clone();
  cfg.socketSend = playstrategy.socket.send;
  const analyse = PlayStrategyAnalyse(cfg);
}
