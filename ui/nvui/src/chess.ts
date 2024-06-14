import { h, VNode } from 'snabbdom';
import { Pieces, Rank, File, files } from 'chessground/build/types';
import { invRanks, allKeys } from 'chessground/build/util';
import { Setting, makeSetting } from './setting';
import { parseFen } from 'stratops/build/fen';
import { Chess } from 'stratops/build/chess';
import { chessgroundDests } from 'stratops/build/compat';
import { SquareName } from 'stratops/build/types';

export type Style = 'uci' | 'san' | 'literate' | 'nato' | 'anna';
export type PieceStyle = 'letter' | 'white uppercase letter' | 'name' | 'white uppercase name';
export type PrefixStyle = 'letter' | 'name' | 'none';
export type PositionStyle = 'before' | 'after' | 'none';
export type BoardStyle = 'plain' | 'table';

const nato: { [letter: string]: string } = {
  a: 'alpha',
  b: 'bravo',
  c: 'charlie',
  d: 'delta',
  e: 'echo',
  f: 'foxtrot',
  g: 'golf',
  h: 'hotel',
};
const anna: { [letter: string]: string } = {
  a: 'anna',
  b: 'bella',
  c: 'cesar',
  d: 'david',
  e: 'eva',
  f: 'felix',
  g: 'gustav',
  h: 'hector',
};
const roles: { [letter: string]: string } = {
  P: 'pawn',
  R: 'rook',
  N: 'knight',
  B: 'bishop',
  Q: 'queen',
  K: 'king',
  L: 'loachecker',
};
//const letters = { pawn: 'p', rook: 'r', knight: 'n', bishop: 'b', queen: 'q', king: 'k', loachecker: 'l' };

const chessGroundRoleToLetter: { [pieceTypes: string]: string } = {
  'k-piece': 'k',
  'q-piece': 'q',
  'r-piece': 'r',
  'b-piece': 'b',
  'n-piece': 'n',
  'p-piece': 'p',
  'l-piece': 'l',
};

const chessGroundRoleToLilaRole: { [pieceTypes: string]: string } = {
  'k-piece': 'king',
  'q-piece': 'queen',
  'r-piece': 'rook',
  'b-piece': 'bishop',
  'n-piece': 'knight',
  'p-piece': 'pawn',
  'l-piece': 'loachecker',
};

const letterPiece: { [letter: string]: string } = {
  p: 'p',
  r: 'r',
  n: 'n',
  b: 'b',
  q: 'q',
  k: 'k',
  P: 'p',
  R: 'r',
  N: 'n',
  B: 'b',
  Q: 'q',
  K: 'k',
};
const whiteUpperLetterPiece: { [letter: string]: string } = {
  p: 'p',
  r: 'r',
  n: 'n',
  b: 'b',
  q: 'q',
  k: 'k',
  P: 'P',
  R: 'R',
  N: 'N',
  B: 'B',
  Q: 'Q',
  K: 'K',
};
const namePiece: { [letter: string]: string } = {
  p: 'pawn',
  r: 'rook',
  n: 'knight',
  b: 'bishop',
  q: 'queen',
  k: 'king',
  l: 'loachecker',
  P: 'pawn',
  R: 'rook',
  N: 'knight',
  B: 'bishop',
  Q: 'queen',
  K: 'king',
  L: 'loachecker',
};
const whiteUpperNamePiece: { [letter: string]: string } = {
  p: 'pawn',
  r: 'rook',
  n: 'knight',
  b: 'bishop',
  q: 'queen',
  k: 'king',
  l: 'loachecker',
  P: 'Pawn',
  R: 'Rook',
  N: 'Knight',
  B: 'Bishop',
  Q: 'Queen',
  K: 'King',
  L: 'LOAChecker',
};
const skipToFile: { [letter: string]: string } = {
  '!': 'a',
  '@': 'b',
  '#': 'c',
  $: 'd',
  '%': 'e',
  '^': 'f',
  '&': 'g',
  '*': 'h',
};

export function symbolToFile(char: string) {
  return skipToFile[char] ?? '';
}

export function supportedVariant(key: string) {
  return [
    'standard',
    'chess960',
    'kingOfTheHill',
    'threeCheck',
    'fiveCheck',
    'noCastling',
    'linesOfAction',
    'scrambledEggs',
    'fromPosition',
  ].includes(key);
}

export function boardSetting(): Setting<BoardStyle> {
  return makeSetting<BoardStyle>({
    choices: [
      ['plain', 'plain: layout with no semantic rows or columns'],
      ['table', 'table: layout using a table with rank and file columns and row headers'],
    ],
    default: 'plain',
    storage: playstrategy.storage.make('nvui.boardLayout'),
  });
}

export function styleSetting(): Setting<Style> {
  return makeSetting<Style>({
    choices: [
      ['san', 'SAN: Nxf3'],
      ['uci', 'UCI: g1f3'],
      ['literate', 'Literate: knight takes f 3'],
      ['anna', 'Anna: knight takes felix 3'],
      ['nato', 'Nato: knight takes foxtrot 3'],
    ],
    default: 'anna', // all the rage in OTB blind chess tournaments
    storage: playstrategy.storage.make('nvui.moveNotation'),
  });
}

export function pieceSetting(): Setting<PieceStyle> {
  return makeSetting<PieceStyle>({
    choices: [
      ['letter', 'Letter: p, p'],
      ['white uppercase letter', 'White uppecase letter: P, p'],
      ['name', 'Name: pawn, pawn'],
      ['white uppercase name', 'White uppercase name: Pawn, pawn'],
    ],
    default: 'letter',
    storage: playstrategy.storage.make('nvui.pieceStyle'),
  });
}

export function prefixSetting(): Setting<PrefixStyle> {
  return makeSetting<PrefixStyle>({
    choices: [
      ['letter', 'Letter: w/b'],
      ['name', 'Name: white/black'],
      ['none', 'None'],
    ],
    default: 'letter',
    storage: playstrategy.storage.make('nvui.prefixStyle'),
  });
}

export function positionSetting(): Setting<PositionStyle> {
  return makeSetting<PositionStyle>({
    choices: [
      ['before', 'before: c2: wp'],
      ['after', 'after: wp: c2'],
      ['none', 'none'],
    ],
    default: 'before',
    storage: playstrategy.storage.make('nvui.positionStyle'),
  });
}
const renderPieceStyle = (piece: string, pieceStyle: PieceStyle) => {
  switch (pieceStyle) {
    case 'letter':
      return letterPiece[piece];
    case 'white uppercase letter':
      return whiteUpperLetterPiece[piece];
    case 'name':
      return namePiece[piece];
    case 'white uppercase name':
      return whiteUpperNamePiece[piece];
  }
};
const renderPrefixStyle = (playerIndex: PlayerIndex, prefixStyle: PrefixStyle) => {
  switch (prefixStyle) {
    case 'letter':
      return playerIndex === 'p1' ? 'w' : 'b';
    case 'name':
      return playerIndex === 'p1' ? 'white ' : 'black ';
    case 'none':
      return '';
  }
};

export function lastCaptured(movesGenerator: () => string[], pieceStyle: PieceStyle, prefixStyle: PrefixStyle): string {
  const moves = movesGenerator();
  const oldFen = moves[moves.length - 2];
  const newFen = moves[moves.length - 1];
  if (!oldFen || !newFen) {
    return 'none';
  }
  const oldSplitFen = oldFen.split(' ')[0];
  const newSplitFen = newFen.split(' ')[0];
  for (const p of 'kKqQrRbBnNpP') {
    const diff = oldSplitFen.split(p).length - 1 - (newSplitFen.split(p).length - 1);
    const pPlayerIndex = p.toUpperCase() === p ? 'p1' : 'p2';
    if (diff === 1) {
      const prefix = renderPrefixStyle(pPlayerIndex, prefixStyle);
      const piece = renderPieceStyle(p, pieceStyle);
      return prefix + piece;
    }
  }
  return 'none';
}

export function renderSan(san: San, uci: Uci | undefined, style: Style) {
  if (!san) return '';
  let move: string;
  if (san.includes('O-O-O')) move = 'long castling';
  else if (san.includes('O-O')) move = 'short castling';
  else if (style === 'san') move = san.replace(/[\+#]/, '');
  else if (style === 'uci') move = uci || san;
  else {
    move = san
      .replace(/[\+#]/, '')
      .split('')
      .map(c => {
        if (c == 'x') return 'takes';
        if (c == '+') return 'check';
        if (c == '#') return 'checkmate';
        if (c == '=') return 'promotion';
        const code = c.charCodeAt(0);
        if (code > 48 && code < 58) return c; // 1-8
        if (code > 96 && code < 105) return renderFile(c, style); // a-g
        return roles[c] || c;
      })
      .join(' ');
  }
  if (san.includes('+')) move += ' check';
  if (san.includes('#')) move += ' checkmate';
  return move;
}

export function renderPieces(pieces: Pieces, style: Style): VNode {
  return h(
    'div',
    ['p1', 'p2'].map(playerIndex => {
      const lists: any = [];
      ['king', 'queen', 'rook', 'bishop', 'knight', 'pawn', 'loachecker'].forEach(role => {
        const keys = [];
        for (const [key, piece] of pieces) {
          if (piece.playerIndex === playerIndex && chessGroundRoleToLilaRole[piece.role] === role) keys.push(key);
        }
        if (keys.length) lists.push([`${role}${keys.length > 1 ? 's' : ''}`, ...keys]);
      });
      return h('div', [
        h('h3', `${playerIndex} pieces`),
        ...lists
          .map(
            (l: any) =>
              `${l[0]}: ${l
                .slice(1)
                .map((k: string) => renderKey(k, style))
                .join(', ')}`
          )
          .join(', '),
      ]);
    })
  );
}

export function renderPieceKeys(pieces: Pieces, p: string, style: Style): string {
  const name = `${p === p.toUpperCase() ? 'white' : 'black'} ${roles[p.toUpperCase()]}`;
  const res: Key[] = [];
  for (const [k, piece] of pieces) {
    if (piece && `${piece.playerIndex} ${chessGroundRoleToLilaRole[piece.role]}` === name) res.push(k as Key);
  }
  return `${name}: ${res.length ? res.map(k => renderKey(k, style)).join(', ') : 'none'}`;
}

export function renderPiecesOn(pieces: Pieces, rankOrFile: string, style: Style): string {
  const res: string[] = [];
  for (const k of allKeys()) {
    if (k.includes(rankOrFile)) {
      const piece = pieces.get(k);
      if (piece) res.push(`${renderKey(k, style)} ${piece.playerIndex} ${chessGroundRoleToLilaRole[piece.role]}`);
    }
  }
  return res.length ? res.join(', ') : 'blank';
}

export function renderBoard(
  pieces: Pieces,
  pov: PlayerIndex,
  pieceStyle: PieceStyle,
  prefixStyle: PrefixStyle,
  positionStyle: PositionStyle,
  boardStyle: BoardStyle
): VNode {
  const doRankHeader = (rank: Rank): VNode => {
    return h('th', { attrs: { scope: 'row' } }, rank);
  };
  const doFileHeaders = (): VNode => {
    const ths = files.map(file => h('th', { attrs: { scope: 'col' } }, file));
    if (pov === 'p2') ths.reverse();
    return h('tr', [h('td'), ...ths, h('td')]);
  };
  const renderPositionStyle = (rank: Rank, file: File, orig: string) => {
    switch (positionStyle) {
      case 'before':
        return file.toUpperCase() + rank + ' ' + orig;
      case 'after':
        return orig + ' ' + file.toUpperCase() + rank;
      case 'none':
        return orig;
    }
  };
  const doPieceButton = (
    rank: Rank,
    file: File,
    letter: string,
    playerIndex: PlayerIndex | 'none',
    text: string
  ): VNode => {
    return h(
      'button',
      {
        attrs: { rank: rank, file: file, piece: letter.toLowerCase(), playerIndex: playerIndex },
      },
      text
    );
  };
  const doPiece = (rank: Rank, file: File): VNode => {
    const key = (file + rank) as Key;
    const piece = pieces.get(key);
    const pieceWrapper = boardStyle === 'table' ? 'td' : 'span';
    if (piece) {
      const role = chessGroundRoleToLetter[piece.role];
      const pieceText = renderPieceStyle(piece.playerIndex === 'p1' ? role.toUpperCase() : role, pieceStyle);
      const prefix = renderPrefixStyle(piece.playerIndex, prefixStyle);
      const text = renderPositionStyle(rank, file, prefix + pieceText);
      return h(pieceWrapper, doPieceButton(rank, file, role, piece.playerIndex, text));
    } else {
      const letter = (key.charCodeAt(0) + key.charCodeAt(1)) % 2 ? '-' : '+';
      const text = renderPositionStyle(rank, file, letter);
      return h(pieceWrapper, doPieceButton(rank, file, letter, 'none', text));
    }
  };
  const doRank = (pov: PlayerIndex, rank: Rank): VNode => {
    const rankElements = [];
    if (boardStyle === 'table') rankElements.push(doRankHeader(rank));
    rankElements.push(...files.map(file => doPiece(rank, file)));
    if (boardStyle === 'table') rankElements.push(doRankHeader(rank));
    if (pov === 'p2') rankElements.reverse();
    return h(boardStyle === 'table' ? 'tr' : 'div', rankElements);
  };
  const ranks: VNode[] = [];
  if (boardStyle === 'table') ranks.push(doFileHeaders());
  ranks.push(...invRanks.map(rank => doRank(pov, rank)));
  if (boardStyle === 'table') ranks.push(doFileHeaders());
  if (pov === 'p2') ranks.reverse();
  return h(boardStyle === 'table' ? 'table.board-wrapper' : 'div.board-wrapper', ranks);
}

export function renderFile(f: string, style: Style): string {
  return style === 'nato' ? nato[f] : style === 'anna' ? anna[f] : f;
}

export function renderKey(key: string, style: Style): string {
  return `${renderFile(key[0], style)} ${key[1]}`;
}

export function castlingFlavours(input: string): string {
  switch (input.toLowerCase().replace(/[-\s]+/g, '')) {
    case 'oo':
    case '00':
      return 'o-o';
    case 'ooo':
    case '000':
      return 'o-o-o';
  }
  return input;
}

/* Listen to interactions on the chessboard */
export function positionJumpHandler() {
  return (ev: KeyboardEvent) => {
    const $btn = $(ev.target as HTMLElement);
    const $file = $btn.attr('file') ?? '';
    const $rank = $btn.attr('rank') ?? '';
    let $newRank = '';
    let $newFile = '';
    if (ev.key.match(/^[1-8]$/)) {
      $newRank = ev.key;
      $newFile = $file;
    } else if (ev.key.match(/^[!@#$%^&*]$/)) {
      $newRank = $rank;
      $newFile = symbolToFile(ev.key);
      // if not a valid key for jumping
    } else {
      return true;
    }
    const newBtn = document.querySelector(
      '.board-wrapper button[rank="' + $newRank + '"][file="' + $newFile + '"]'
    ) as HTMLElement;
    if (newBtn) {
      newBtn.focus();
      return false;
    }
    return true;
  };
}

export function pieceJumpingHandler(wrapSound: () => void, errorSound: () => void) {
  return (ev: KeyboardEvent) => {
    if (!ev.key.match(/^[kqrbnp]$/i)) return true;
    const $currBtn = $(ev.target as HTMLElement);

    // TODO: decouple from promotion attribute setting in selectionHandler
    if ($currBtn.attr('promotion') === 'true') {
      const $moveBox = $('input.move');
      const $boardLive = $('.boardstatus');
      const $promotionPiece = ev.key.toLowerCase();
      const $form = $moveBox.parent().parent();
      if (!$promotionPiece.match(/^[qnrb]$/)) {
        $boardLive.text('Invalid promotion piece. q for queen, n for knight, r for rook, b for bisho');
        return false;
      }
      $moveBox.val($moveBox.val() + $promotionPiece);
      $currBtn.removeAttr('promotion');
      const $sendForm = new Event('submit', {
        cancelable: true,
        bubbles: true,
      });
      $form.trigger($sendForm);
      return false;
    }

    const $myBtnAttrs = '.board-wrapper [rank="' + $currBtn.attr('rank') + '"][file="' + $currBtn.attr('file') + '"]';
    const $allPieces = $('.board-wrapper [piece="' + ev.key.toLowerCase() + '"], ' + $myBtnAttrs);
    const $myPieceIndex = $allPieces.index($myBtnAttrs);
    const $next = ev.key.toLowerCase() === ev.key;
    const $prevNextPieces = $next ? $allPieces.slice($myPieceIndex + 1) : $allPieces.slice(0, $myPieceIndex);
    const $piece = $next ? $prevNextPieces.get(0) : $prevNextPieces.get($prevNextPieces.length - 1);
    if ($piece) {
      $piece.focus();
      // if detected any matching piece; one is the pice being clicked on,
    } else if ($allPieces.length >= 2) {
      const $wrapPiece = $next ? $allPieces.get(0) : $allPieces.get($allPieces.length - 1);
      $wrapPiece?.focus();
      wrapSound();
    } else {
      errorSound();
    }
    return false;
  };
}

export function arrowKeyHandler(pov: PlayerIndex, borderSound: () => void) {
  return (ev: KeyboardEvent) => {
    const $currBtn = $(ev.target as HTMLElement);
    const $isWhite = pov === 'p1';
    let $file = $currBtn.attr('file') ?? ' ';
    let $rank = Number($currBtn.attr('rank'));
    if (ev.key === 'ArrowUp') {
      $rank = $isWhite ? ($rank += 1) : ($rank -= 1);
    } else if (ev.key === 'ArrowDown') {
      $rank = $isWhite ? ($rank -= 1) : ($rank += 1);
    } else if (ev.key === 'ArrowLeft') {
      $file = String.fromCharCode($isWhite ? $file.charCodeAt(0) - 1 : $file.charCodeAt(0) + 1);
    } else if (ev.key === 'ArrowRight') {
      $file = String.fromCharCode($isWhite ? $file.charCodeAt(0) + 1 : $file.charCodeAt(0) - 1);
    } else {
      return true;
    }
    const $newSq = document.querySelector('.board-wrapper [file="' + $file + '"][rank="' + $rank + '"]') as HTMLElement;
    if ($newSq) {
      $newSq.focus();
    } else {
      borderSound();
    }
    ev.preventDefault();
    return false;
  };
}

export function selectionHandler(opponentPlayerIndex: PlayerIndex, selectSound: () => void) {
  return (ev: MouseEvent) => {
    // this depends on the current document structure. This may not be advisable in case the structure wil change.
    const $evBtn = $(ev.target as HTMLElement);
    const $rank = $evBtn.attr('rank');
    const $pos = ($evBtn.attr('file') ?? '') + $rank;
    const $boardLive = $('.boardstatus');
    const $promotionRank = opponentPlayerIndex === 'p2' ? '8' : '1';
    const $moveBox = $(document.querySelector('input.move') as HTMLInputElement);
    if (!$moveBox) return false;

    // if no move in box yet
    if ($moveBox.val() === '') {
      // if user selects anothers' piece first
      if ($evBtn.attr('playerIndex') === opponentPlayerIndex) return false;
      // as long as the user is selecting a piece and not a blank tile
      if ($evBtn.text().match(/^[^\-+]+/g)) {
        $moveBox.val($pos);
        selectSound();
      }
    } else {
      // if user selects their own piece second
      if ($evBtn.attr('playerIndex') === (opponentPlayerIndex === 'p2' ? 'p1' : 'p2')) return false;

      const $first = $moveBox.val();
      const $firstPiece = $('.board-wrapper [file="' + $first[0] + '"][rank="' + $first[1] + '"]');
      $moveBox.val($moveBox.val() + $pos);
      // this is coupled to pieceJumpingHandler() noticing that the attribute is set and acting differently. TODO: make cleaner
      // if pawn promotion
      if ($rank === $promotionRank && $firstPiece.attr('piece')?.toLowerCase() === 'p') {
        $evBtn.attr('promotion', 'true');
        $boardLive.text('Promote to? q for queen, n for knight, r for rook, b for bishop');
        return false;
      }
      // this section depends on the form being the granparent of the input.move box.
      const $form = $moveBox.parent().parent();
      const $event = new Event('submit', {
        cancelable: true,
        bubbles: true,
      });
      $form.trigger($event);
    }
    return false;
  };
}

export function boardCommandsHandler() {
  return (ev: KeyboardEvent) => {
    const $currBtn = $(ev.target as HTMLElement);
    const $boardLive = $('.boardstatus');
    const $position = ($currBtn.attr('file') ?? '') + ($currBtn.attr('rank') ?? '');
    if (ev.key === 'o') {
      $boardLive.text();
      $boardLive.text($position);
      return false;
    } else if (ev.key === 'l') {
      const $lastMove = $('p.lastMove').text();
      $boardLive.text();
      $boardLive.text($lastMove);
      return false;
    } else if (ev.key === 't') {
      $boardLive.text();
      $boardLive.text($('.nvui .botc').text() + ', ' + $('.nvui .topc').text());
      return false;
    }
    return true;
  };
}
export function lastCapturedCommandHandler(steps: () => string[], pieceStyle: PieceStyle, prefixStyle: PrefixStyle) {
  return (ev: KeyboardEvent) => {
    const $boardLive = $('.boardstatus');
    if (ev.key === 'c') {
      $boardLive.text();
      $boardLive.text(lastCaptured(steps, pieceStyle, prefixStyle));
      return false;
    }
    return true;
  };
}

export function possibleMovesHandler(playerIndex: PlayerIndex, fen: () => string, pieces: () => Pieces) {
  return (ev: KeyboardEvent) => {
    if (ev.key !== 'm' && ev.key !== 'M') return true;
    const $boardLive = $('.boardstatus');
    const $pieces = pieces();
    const myTurnFen = playerIndex === 'p1' ? 'w' : 'b';
    const opponentTurnFen = playerIndex === 'p2' ? 'b' : 'w';

    const $btn = $(ev.target as HTMLElement);
    const $pos = (($btn.attr('file') ?? '') + $btn.attr('rank')) as SquareName;

    // possible ineffecient to reparse fen; but seems to work when it is AND when it is not the users' turn.
    const possibleMoves = chessgroundDests('chess')(
      Chess.fromSetup(
        parseFen('chess')(fen().replace(' ' + opponentTurnFen + ' ', ' ' + myTurnFen + ' ')).unwrap()
      ).unwrap()
    )
      .get($pos)
      ?.map(i => {
        const p = $pieces.get(i);
        return p ? i + ' captures ' + p.role : i;
      })
      .filter(i => ev.key === 'm' || i.includes('captures'));
    if (!possibleMoves) {
      $boardLive.text('None');
      // if filters out non-capturing moves
    } else if (possibleMoves.length === 0) {
      $boardLive.text('No captures');
    } else {
      $boardLive.text(possibleMoves.join(', '));
    }
    return false;
  };
}
