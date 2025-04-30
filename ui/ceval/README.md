Stockfish documentation : https://official-stockfish.github.io/docs/stockfish-wiki/Home.html (see for example _UCI & Commands_ section)

### Our implementation

We use https://github.com/fairy-stockfish/fairy-stockfish.wasm.

Ceval interacts with [AnalyseCtrl.getNode()](https://github.com/Mind-Sports-Games/lila/blob/e3a33345b612b0ba4b9197ae492e5009f8d0dfa6/ui/analyse/src/ctrl.ts#L269) to access infos of the current state of the analysis board from the DOM.  
[ui/analysis/autoshape.ts](https://github.com/Mind-Sports-Games/lila/blob/e3a33345b612b0ba4b9197ae492e5009f8d0dfa6/ui/analyse/src/autoShape.ts#L47) is used to express its suggestions using draw functions on chessground (e.g. draw arrows for a piece moving).  
The node interface is defined in [ui/@types/playstrategy/index.d.ts](https://github.com/Mind-Sports-Games/lila/blob/e3a33345b612b0ba4b9197ae492e5009f8d0dfa6/ui/%40types/playstrategy/index.d.ts#L597), we can see the ceval property interface is defined in the same file.  
In that [ClientEval](https://github.com/Mind-Sports-Games/lila/blob/e3a33345b612b0ba4b9197ae492e5009f8d0dfa6/ui/%40types/playstrategy/index.d.ts#L556) interface, "pvs" stands for "principal variations" (containing a series of moves).  
When hovering the notation of the move, renderPvBoard is invoking chessground to render a mini board.

### Overriding variants.ini

Minibreakthrough is disabled for now, in theory it is possible to load a variant conf file (using `load <file>` or `setoption name VariantPath value <file>`).  
After sending `d`, we can see the board starts from the top left : `Fen: ppppp3/ppppp3/8/PPPPP3/PPPPP3/8/8/8 w - - 0 1`.

### Debug

In case you want to debug anything, this might be useful to know there is a const `MAX_NUM_MOVES` in the file `view.ts` that allows reducing the length of the path computed.
