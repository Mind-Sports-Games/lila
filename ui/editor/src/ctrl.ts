import { EditorState, Selected, Redraw, CastlingToggle, CastlingToggles, CASTLING_TOGGLES } from './interfaces';
import { Api as CgApi } from 'chessground/api';
import { Rules, Square } from 'stratops/types';
import { SquareSet } from 'stratops/squareSet';
import { Board } from 'stratops/board';
import { Setup, Material, RemainingChecks } from 'stratops/setup';
import { Castles } from 'stratops';
import { playstrategyVariants } from 'stratops/compat';
import { makeFen, parseFen, parseCastlingFen, INITIAL_FEN, EMPTY_FEN, INITIAL_EPD } from 'stratops/fen';
import * as fp from 'stratops/fp';
import { defined, prop, Prop } from 'common';
import { getClassFromRules } from 'stratops/variants/utils';

export default class EditorCtrl {
  cfg: Editor.Config;
  options: Editor.Options;
  trans: Trans;
  extraPositions: Editor.OpeningPosition[];
  chessground: CgApi | undefined;
  redraw: Redraw;

  selected: Prop<Selected>;

  initialFen: string;
  pockets: Material | undefined;
  turn: PlayerIndex;
  unmovedRooks: SquareSet | undefined;
  castlingToggles: CastlingToggles<boolean>;
  epSquare: fp.Option<Square>;
  remainingChecks: fp.Option<RemainingChecks>;
  rules: Rules;
  halfmoves: number;
  fullmoves: number;

  constructor(cfg: Editor.Config, redraw: Redraw) {
    this.cfg = cfg;
    this.options = cfg.options || {};

    this.trans = playstrategy.trans(this.cfg.i18n);

    this.selected = prop('pointer');

    this.extraPositions = [
      {
        fen: INITIAL_FEN,
        epd: INITIAL_EPD,
        name: this.trans('startPosition'),
      },
      {
        fen: 'prompt',
        name: this.trans('loadPosition'),
      },
    ];

    if (cfg.positions) {
      cfg.positions.forEach(p => (p.epd = p.fen.split(' ').splice(0, 4).join(' ')));
    }

    window.Mousetrap.bind('f', () => {
      if (this.chessground) this.chessground.toggleOrientation();
      redraw();
    });

    this.castlingToggles = { K: false, Q: false, k: false, q: false };
    const params = new URLSearchParams(location.search);
    this.rules =
      !this.cfg.embed && window.history.state && window.history.state.rules ? window.history.state.rules : 'chess';
    this.initialFen = (cfg.fen || params.get('fen') || INITIAL_FEN).replace(/_/g, ' ');

    this.redraw = () => {};
    this.setFen(this.initialFen);
    this.redraw = redraw;
  }

  onChange(): void {
    const fen = this.getFen();
    if (!this.cfg.embed) {
      const state = { rules: this.rules };
      if (fen == INITIAL_FEN) window.history.replaceState(state, '', '/editor');
      else window.history.replaceState(state, '', this.makeUrl('/editor/', fen));
    }
    this.options.onChange && this.options.onChange(fen);
    this.redraw();
  }

  private castlingToggleFen(): string {
    let fen = '';
    for (const toggle of CASTLING_TOGGLES) {
      if (this.castlingToggles[toggle]) fen += toggle;
    }
    return fen;
  }

  private getSetup(): Setup {
    const boardFen = this.chessground ? this.chessground.getFen() : this.initialFen;
    const board = parseFen(this.rules)(boardFen).unwrap(
      setup => setup.board,
      _ => Board.empty(this.rules),
    );
    return {
      board,
      pockets: this.pockets,
      turn: this.turn,
      unmovedRooks: this.unmovedRooks || parseCastlingFen(board)(this.castlingToggleFen()).unwrap(),
      epSquare: this.epSquare,
      remainingChecks: this.remainingChecks,
      halfmoves: this.halfmoves,
      fullmoves: this.fullmoves,
    };
  }

  getFen(): string {
    return makeFen('chess')(this.getSetup(), { promoted: this.rules == 'crazyhouse' });
  }

  private getLegalFen(): string | undefined {
    return getClassFromRules(this.rules)
      .fromSetup(this.getSetup())
      .unwrap(
        pos => {
          return makeFen('chess')(pos.toSetup(), { promoted: pos.rules == 'crazyhouse' });
        },
        _ => undefined,
      );
  }

  private isPlayable(): boolean {
    return getClassFromRules(this.rules)
      .fromSetup(this.getSetup())
      .unwrap(
        pos => !pos.isEnd(),
        _ => false,
      );
  }

  getState(): EditorState {
    return {
      fen: this.getFen(),
      legalFen: this.getLegalFen(),
      playable: this.rules == 'chess' && this.isPlayable(),
    };
  }

  makeAnalysisUrl(legalFen: string): string {
    const variant = this.rules === 'chess' ? '' : playstrategyVariants(this.rules) + '/';
    return this.makeUrl(`/analysis/${variant}`, legalFen);
  }

  makeUrl(baseUrl: string, fen: string): string {
    return baseUrl + encodeURIComponent(fen).replace(/%20/g, '_').replace(/%2F/g, '/');
  }

  bottomPlayerIndex(): PlayerIndex {
    const orientation = this.chessground ? this.chessground.state.orientation : this.options.orientation;
    //TODO: rework this function
    if (orientation === undefined) return 'p1';
    else if (orientation === 'p1' || orientation === 'p2') return orientation;
    else return 'p2'; // TODO: this needs to be fixed for games other than LinesOfAction and backgammon
  }

  setCastlingToggle(id: CastlingToggle, value: boolean): void {
    if (this.castlingToggles[id] != value) this.unmovedRooks = undefined;
    this.castlingToggles[id] = value;
    this.onChange();
  }

  setTurn(turn: PlayerIndex): void {
    this.turn = turn;
    this.onChange();
  }

  startPosition = () => this.setFen(makeFen('chess')(getClassFromRules(this.rules).default().toSetup()));

  clearBoard = () => this.setFen(EMPTY_FEN);

  loadNewFen(fen: string | 'prompt'): void {
    if (fen === 'prompt') {
      fen = (prompt('Paste FEN position') || '').trim();
      if (!fen) return;
    }
    this.setFen(fen);
  }

  setFen = (fen: string): boolean => {
    return parseFen('chess')(fen).unwrap(
      setup => {
        if (this.chessground) this.chessground.set({ fen });
        this.pockets = setup.pockets;
        this.turn = setup.turn;
        this.unmovedRooks = setup.unmovedRooks;
        this.epSquare = setup.epSquare;
        this.remainingChecks = setup.remainingChecks;
        this.halfmoves = setup.halfmoves;
        this.fullmoves = setup.fullmoves;

        const castles = Castles.fromSetup(setup);
        this.castlingToggles['K'] = defined(castles.rook.p1.h);
        this.castlingToggles['Q'] = defined(castles.rook.p1.a);
        this.castlingToggles['k'] = defined(castles.rook.p2.h);
        this.castlingToggles['q'] = defined(castles.rook.p2.a);

        this.onChange();
        return true;
      },
      _ => false,
    );
  };

  setRules(rules: Rules): void {
    this.rules = rules;
    if (rules != 'crazyhouse') this.pockets = undefined;
    else if (!this.pockets) this.pockets = Material.empty();
    if (rules != '3check' && rules != '5check') this.remainingChecks = undefined;
    else if (!this.remainingChecks) {
      if (rules == '5check') this.remainingChecks = RemainingChecks.fiveCheck();
      else this.remainingChecks = RemainingChecks.default();
    }
    this.onChange();
  }

  setOrientation(o: PlayerIndex): void {
    this.options.orientation = o;
    if (this.chessground!.state.orientation !== o) this.chessground!.toggleOrientation();
    this.redraw();
  }
}
