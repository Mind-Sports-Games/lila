import { EditorState, Selected, Redraw, CastlingToggle, CastlingToggles, CASTLING_TOGGLES } from './interfaces';
import { Api as CgApi } from 'chessground/api';
import { Rules, Square } from 'stratops/types';
import { SquareSet } from 'stratops/squareSet';
// import { Board } from 'stratops/board';
import { Setup, Material, RemainingChecks } from 'stratops/setup';
import { Board, Castles } from 'stratops';
import { makeFen, parseFen, parseCastlingFen } from 'stratops/fen';
import * as fp from 'stratops/fp';
import { defined, prop, Prop } from 'common';
import { variantClass, variantClassFromKey, variantKeyToRules } from 'stratops/variants/util';
import { Variant as CGVariant } from 'chessground/types';
import { VariantKey } from 'stratops/variants/types';

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
  halfmoves: number;
  fullmoves: number;

  rules: Rules;
  variantKey: VariantKey;

  constructor(cfg: Editor.Config, redraw: Redraw) {
    this.cfg = cfg;
    this.options = cfg.options || {};

    this.trans = playstrategy.trans(this.cfg.i18n);

    this.selected = prop('pointer');

    const params = new URLSearchParams(window.location.search);
    this.variantKey = (cfg.variantKey || 'standard') as VariantKey;
    this.rules = variantKeyToRules(this.variantKey);
    this.initialFen = (cfg.fen || params.get('fen') || variantClassFromKey(this.variantKey).getInitialFen());

    this.extraPositions = [
      {
        fen: variantClassFromKey(this.variantKey).getInitialBoardFen(),
        epd: variantClassFromKey(this.variantKey).getInitialEpd(),
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
      this.chessground.set
      redraw();
    });

    this.castlingToggles = { K: false, Q: false, k: false, q: false };

    this.redraw = () => {};
    this.setFen(this.initialFen);
    this.redraw = redraw;
  }

  formatVariantForUrl(): string {
    return this.variantKey === "standard" ? 'chess' : this.variantKey;
  }

  onChange(): void {
    const fen = this.getFenFromSetup();
    if (!this.cfg.embed) {
      const state = { rules: this.rules, variantKey: this.variantKey, fen };
      window.history.replaceState(state, '', this.makeUrl('/editor/' + this.formatVariantForUrl() + '/', fen));
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
    let fen = this.chessground
      ? this.chessground.getFen()
      : this.initialFen || variantClassFromKey(this.variantKey).getInitialFen();

    if (fen.split(' ').length === 1) {

      fen += ' w - - 0 1';
    }
    const board = parseFen(this.rules)(fen).unwrap(
      setup => setup.board,
      _ => {
        console.warn('Invalid FEN:', fen);
        return Board.empty(this.rules);
      },
    );

    return {
      board,
      pockets: this.pockets,
      turn: this.turn,
      unmovedRooks: this.unmovedRooks || parseCastlingFen(board)(this.castlingToggleFen()).unwrap(),
      epSquare: this.epSquare,
      remainingChecks: this.remainingChecks,
      halfmoves: this.halfmoves ?? 0,
      fullmoves: this.fullmoves ?? 1,
    };
  }

  getFenFromSetup(): string {
    return makeFen(this.rules)(this.getSetup(), { promoted: this.rules == 'crazyhouse' }).replace('[', '/').replace(']', '').replace(/ /g, '_');
  }

  private getLegalFen(): string | undefined {
    return this.getFenFromSetup();
  }

  private isPlayable(): boolean {
    return variantClass(this.rules)
      .fromSetup(this.getSetup())
      .unwrap(
        pos => !pos.isEnd(),
        _ => false,
      );
  }

  getState(): EditorState {
    return {
      fen: this.getFenFromSetup(),
      legalFen: this.getLegalFen(),
      playable: this.rules == 'chess' && this.isPlayable(),
    };
  }

  makeAnalysisUrl(legalFen: string): string {
    const variant = this.rules === 'chess' ? '' : this.variantKey + '/';
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
    else return 'p2'; // : this needs to be fixed for games other than LinesOfAction and backgammon
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

  startPosition = () => {
    this.setFen(variantClassFromKey(this.variantKey).getInitialFen());
    this.redraw();
  }

  clearBoard = () => {
    this.setFen(variantClassFromKey(this.variantKey).getEmptyBoardFen());
  }

  loadNewFen(fen: string | 'prompt'): void {
    if (fen === 'prompt') {
      fen = (prompt('Paste FEN position') || '').trim();
      if (!fen) return;
    }
    this.setFen(fen);
  }

  setFen = (fen: string): boolean => {
    const width = variantClassFromKey(this.variantKey).width;
    const height = variantClassFromKey(this.variantKey).height;

    return parseFen(this.rules)(fen).unwrap(
      setup => {
        if (this.chessground) this.chessground.set({
          fen,
          variant: this.variantKey as CGVariant,
          dimensions: { width, height },
        });
        this.turn = setup.turn;
        this.halfmoves = setup.halfmoves;
        this.fullmoves = setup.fullmoves;
        this.pockets = setup.pockets;

        if ([
          'chess',
          'antichess',
          'atomic',
          'crazyhouse',
          'horde',
          'kingofthehill',
          'racingkings',
          'threecheck',
          'fivecheck',
          'monster'
        ].includes(this.rules)) {
          this.unmovedRooks = setup.unmovedRooks;
          this.epSquare = setup.epSquare;
          this.remainingChecks = setup.remainingChecks;
          const castles = Castles.fromSetup(setup);
          this.castlingToggles['K'] = defined(castles.rook.p1.h);
          this.castlingToggles['Q'] = defined(castles.rook.p1.a);
          this.castlingToggles['k'] = defined(castles.rook.p2.h);
          this.castlingToggles['q'] = defined(castles.rook.p2.a);
        }

        this.onChange();
        return true;
      },
      _ =>  {
        console.warn('Invalid FEN:', fen);
        return false;
      }
    );
  };

  setVariantAndRules(variantKey: VariantKey): void {
    this.variantKey = variantKey;
    this.initialFen = variantClassFromKey(variantKey).getInitialFen();
    this.extraPositions = [
      {
        fen: variantClassFromKey(variantKey).getInitialFen(),
        epd: variantClassFromKey(variantKey).getInitialEpd(),
        name: this.trans('startPosition'),
      },
      {
        fen: 'prompt',
        name: this.trans('loadPosition'),
      },
    ];
    this.setRules(variantKeyToRules(variantKey));
  }

  private setRules(rules: Rules): void {
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
