import { EditorState, Selected, Redraw, CastlingToggle, CastlingToggles, CASTLING_TOGGLES } from './interfaces';
import { Api as CgApi } from 'chessground/api';
import { Rules, Square } from 'stratops/types';
import { SquareSet } from 'stratops/squareSet';
import { Setup, Material, RemainingChecks } from 'stratops/setup';
import { Board, Castles } from 'stratops';
import { makeFen, parseCastlingFen } from 'stratops/fen';
import * as fp from 'stratops/fp';
import { defined, prop, Prop } from 'common';
import { replacePocketsInFen } from 'common/editor';
import throttle from 'common/throttle';
import { variantClass, variantClassFromKey, variantKeyToRules } from 'stratops/variants/util';
import { Variant as CGVariant } from 'chessground/types';
import type { VariantKey } from 'stratops/variants/types';

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
  standardInitialPosition: boolean;
  variantKey: VariantKey;

  replaceState: (state: any, url: string) => void;

  constructor(cfg: Editor.Config, redraw: Redraw) {
    this.cfg = cfg;
    this.options = cfg.options || {};

    this.trans = playstrategy.trans(this.cfg.i18n);

    this.selected = prop('pointer');

    const params = new URLSearchParams(window.location.search);
    this.variantKey = (cfg.variantKey || 'standard') as VariantKey;
    const variant = variantClassFromKey(this.variantKey);
    this.rules = variantKeyToRules(this.variantKey);
    this.initialFen = cfg.fen || params.get('fen') || variant.getInitialFen();
    this.standardInitialPosition = cfg.standardInitialPosition;
    this.turn = cfg.playerIndex || 'p1';

    this.extraPositions = [
      {
        fen: variant.getInitialBoardFen(),
        epd: `${variant.getInitialBoardFen} ${variant.getInitialEpd()}`,
        name: this.trans('startPosition'),
      },
      {
        fen: 'prompt',
        name: this.trans('loadPosition'),
      },
    ];

    this.replaceState = throttle(500, (state: any, url: string) => {
      try {
        window.history.replaceState(state, '', url);
      } catch (e) {
        console.error('Failed to update history state:', e);
      }
    });

    if (cfg.positions) {
      cfg.positions.forEach(p => (p.epd = p.fen.split(' ').splice(0, 4).join(' ')));
    }

    window.Mousetrap.bind('f', () => {
      if (this.chessground) this.chessground.toggleOrientation();
      redraw();
    });

    this.castlingToggles = { K: false, Q: false, k: false, q: false };

    this.redraw = () => {};
    this.setFen(this.initialFen);
    this.redraw = redraw;
  }

  private castlingToggleFen(): string {
    let fen = '';
    for (const toggle of CASTLING_TOGGLES) {
      if (this.castlingToggles[toggle]) fen += toggle;
    }
    return fen;
  }

  private getSetup(): Setup {
    let fen;
    const variant = variantClassFromKey(this.variantKey);
    if (this.chessground)
      fen = this.chessground.getFen(); // @Note: chessground.getFen() returns the board part of the FEN
    else fen = this.initialFen || variant.getInitialFen();

    if (fen.split(' ').length === 1) fen += ` ${variant.getInitialEpd()} ${variant.getInitialMovesFen()}`;

    const board = variant.parseFen(fen).unwrap(
      setup => setup.board,
      _ => Board.empty(this.rules), // @Note: chessground.getFen() might be slow to update its FEN (slower than this.variantKey is updated).
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

  private getLegalFen(): string | undefined {
    return variantClass(this.rules)
      .fromSetup(this.getSetup())
      .unwrap(
        pos => {
          return makeFen(this.rules)(pos.toSetup(), { promoted: pos.rules == 'crazyhouse' });
        },
        _ => undefined,
      );
  }

  private isPlayable(): boolean {
    return variantClass(this.rules)
      .fromSetup(this.getSetup())
      .unwrap(
        pos => !pos.isEnd(),
        _ => false,
      );
  }

  onChange(): void {
    const variant = variantClassFromKey(this.variantKey);
    let fen = this.getFenFromSetup();

    if (variant.allowEnPassant()) {
      // @ts-expect-error TS2339
      fen = variant.fixFenForEp(fen);
      if (fen === this.getFenFromSetup()) {
        this.epSquare = undefined;
      }
    }
    this.standardInitialPosition = this.isVariantStandardInitialPosition();
    if (!this.cfg.embed) {
      this.replaceState({ rules: this.rules, variantKey: this.variantKey, fen }, this.makeUrl('/editor/', fen));
    }
    this.options.onChange && this.options.onChange(fen);
    this.redraw();
  }

  getState(): EditorState {
    const legalFen = this.getLegalFen();
    const variant = variantClassFromKey(this.variantKey);
    const enPassantOptions =
      legalFen && variant.allowEnPassant()
        ? // @ts-expect-error TS2339
          variant.getEnPassantOptions(legalFen)
        : [];
    return {
      fen: this.getFenFromSetup(),
      legalFen,
      playable: this.rules === 'chess' && this.isPlayable(),
      enPassantOptions,
    };
  }

  getFenFromSetup(): string {
    return replacePocketsInFen(makeFen(this.rules)(this.getSetup(), { promoted: this.rules == 'crazyhouse' }));
  }

  makeAnalysisUrl(legalFen: string): string {
    return (
      `/analysis/${this.variantKey}/` +
      encodeURIComponent(replacePocketsInFen(legalFen)).replace(/%20/g, '_').replace(/%2F/g, '/')
    );
  }

  makeUrl(baseUrl: string, fen: string): string {
    return (
      baseUrl +
      encodeURIComponent(fen).replace(/%20/g, '_').replace(/%2F/g, '/') +
      `${this.variantKey === 'standard' ? '' : '?variant=' + this.variantKey}`
    );
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
    this.epSquare = undefined;
    this.onChange();
  }

  setEnPassant(epSquare: Square | undefined): void {
    this.epSquare = epSquare;
    this.onChange();
  }

  startPosition = () => {
    this.setFen(variantClassFromKey(this.variantKey).getInitialFen());
  };

  clearBoard = () => {
    this.setFen(variantClassFromKey(this.variantKey).getEmptyFen());
  };

  loadNewFen(fen: string | 'prompt'): void {
    if (fen === 'prompt') {
      fen = (prompt('Paste FEN position') || '').trim();
      if (!fen) return;
    }
    this.setFen(fen);
  }

  setFen = (fen: string): boolean => {
    const variant = variantClassFromKey(this.variantKey);
    const width = variant.width;
    const height = variant.height;

    return variant.parseFen(fen).unwrap(
      setup => {
        if (this.chessground)
          this.chessground.set({
            fen,
            variant: this.variantKey as CGVariant,
            dimensions: { width, height },
          });
        this.halfmoves = setup.halfmoves;
        this.fullmoves = setup.fullmoves;
        this.pockets = setup.pockets;

        if (variant.family === 'chess') {
          this.unmovedRooks = setup.unmovedRooks;
          this.epSquare = setup.epSquare;
          this.remainingChecks = setup.remainingChecks;
          const castles = Castles.fromSetup(setup);
          this.castlingToggles['K'] = defined(castles.rook.p1.h);
          this.castlingToggles['Q'] = defined(castles.rook.p1.a);
          this.castlingToggles['k'] = defined(castles.rook.p2.h);
          this.castlingToggles['q'] = defined(castles.rook.p2.a);
        }

        this.initialFen = fen;
        this.onChange();
        return true;
      },
      _ => {
        console.warn('Invalid FEN:', fen);
        return false;
      },
    );
  };

  changeVariant(variantKey: VariantKey): void {
    this.variantKey = variantKey;
    const variant = variantClassFromKey(variantKey);
    this.turn = 'p1';
    this.initialFen = variant.getInitialFen();
    this.extraPositions = [
      {
        fen: variant.getInitialFen(),
        epd: `${variant.getInitialBoardFen()} ${variant.getInitialEpd()}`,
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

  isVariantStandardInitialPosition(): boolean {
    return variantClassFromKey(this.variantKey).standardInitialPosition;
  }

  setOrientation(o: PlayerIndex): void {
    this.options.orientation = o;
    if (this.chessground!.state.orientation !== o) this.chessground!.toggleOrientation();
    this.redraw();
  }
}
