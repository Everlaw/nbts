/// <reference path="../build/typescriptServices.d.ts"/>

// Declarations for internal APIs not included in typescriptServices.d.ts
declare namespace ts {
    function bindSourceFile(file: SourceFile, options: CompilerOptions): void;
    function getNodeModifiers(node: Node): string;
    function getNewLineCharacter(options: CompilerOptions): string;
    function hasModifier(node: Node, flags: ModifierFlags): boolean;
    interface Node {
        symbol?: Symbol;
        locals?: SymbolTable;
    }
    const optionDeclarations: any[];
    function skipTrivia(text: string, pos: number): number;
    interface Symbol {
        parent?: Symbol;
        nbtsDeprecated?: boolean; // Not part of TypeScript; added by patch below
    }
    interface TransientSymbol extends Symbol {
        target?: Symbol;
    }
}

function loadServices(dir: string) {
    const tsFile = dir + '/typescript.js';
    let tsCode: string = require('fs').readFileSync(tsFile, 'utf8');

    // https://github.com/Microsoft/TypeScript/issues/13647 - Include types in completion list
    tsCode = tsCode.replace(new RegExp([
        ' kind: ts\\.SymbolDisplay\\.getSymbolKind\\(typeChecker, symbol, location\\),',
        ' +kindModifiers: ts\\.SymbolDisplay\\.getSymbolModifiers\\(symbol\\),',
        ' +sortText: "0",'
    ].join('\n')), '$& type: typeChecker.typeToString(typeChecker.getTypeOfSymbolAtLocation(symbol, location)),');

    // https://github.com/Microsoft/TypeScript/issues/22467 - In go-to-definition list, distinguish
    // the different kinds in a merged declaration.
    tsCode = tsCode.replace(new RegExp([
        '( function createDefinitionInfo\\(declaration, checker, symbol, node\\) \\{',
        ' +var symbolName = checker.symbolToString\\(symbol\\); //.*?',
        ' +var symbolKind = )ts.SymbolDisplay.getSymbolKind\\(checker, symbol, node\\);'
    ].join('\n')), '$1ts.getNodeKind(declaration)');

    // Run patched JS. Don't use eval(); eval code has measurably worse performance (~10% slower).
    const mod = { exports: {} };
    require('vm').runInThisContext("(function(require,module,__filename,__dirname){" + tsCode + "\n})", {
        filename: tsFile
    })(require, mod, tsFile, dir);
    global.ts = mod.exports;

    // https://github.com/Microsoft/TypeScript/issues/390
    const { bindSourceFile, getNodeModifiers } = ts;
    ts.bindSourceFile = function(file, options) {
        bindSourceFile(file, options);
        let next = -1;
        // Find all declarations with a preceding @deprecated comment and mark their symbols as such
        ts.forEachChild(file, function visit(node: ts.Node) {
            if (next >= node.end) return;
            if (next < node.pos) {
                next = file.text.indexOf("@deprecated", node.pos) >>> 0;
            }
            if (next < ts.skipTrivia(file.text, node.pos)) {
                if (node.kind === ts.SyntaxKind.ModuleDeclaration) {
                    while ((<ts.ModuleDeclaration>node).body &&
                        (<ts.ModuleDeclaration>node).body.kind === ts.SyntaxKind.ModuleDeclaration) {
                         node = (<ts.ModuleDeclaration>node).body;
                    }
                } else if (node.kind === ts.SyntaxKind.VariableStatement) {
                    (<ts.VariableStatement>node).declarationList.declarations.forEach(decl => {
                        decl.symbol.nbtsDeprecated = true;
                    });
                }
                if (node.symbol) node.symbol.nbtsDeprecated = true;
                next = file.text.indexOf("@deprecated", next + 11) >>> 0;
                if (next >= node.end) return;
            }
            ts.forEachChild(node, visit);
        });
    };
    ts.getNodeModifiers = function(node) {
        let result = getNodeModifiers(node);
        if (node.symbol && node.symbol.nbtsDeprecated) {
            result += (result && ",") + "deprecated";
        }
        return result;
    };
}
