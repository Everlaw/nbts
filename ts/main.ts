/// <reference path="services/services.ts"/>
import SK = ts.SyntaxKind;
import SEK = ts.ScriptElementKind;

// Node.js stuff
declare var require: any;
declare module process { var stdin: any, stdout: any, stderr: any; }

class HostImpl implements ts.LanguageServiceHost {
    files: {[name: string]: {version: string; isOpen: boolean; snapshot: ts.IScriptSnapshot}} = {};
    log(s: string) {
        process.stderr.write(s + '\n');
    }
    getCompilationSettings(): ts.CompilerOptions {
        return {
            noImplicitAny: true,
            noLib: true
        };
    }
    getScriptFileNames() {
        return Object.keys(this.files);
    }
    getScriptVersion(fileName: string) {
        return this.files[fileName].version;
    }
    getScriptIsOpen(fileName: string) {
        return this.files[fileName].isOpen;
    }
    getScriptSnapshot(fileName: string) {
        return this.files[fileName].snapshot;
    }
    getLocalizedDiagnosticMessages(): any {
        return null;
    }
    getCancellationToken(): ts.CancellationToken {
        return null;
    }
    getCurrentDirectory() {
        return "curdir"; //TODO
    }
    getDefaultLibFilename(options: ts.CompilerOptions): string {
        return null; //TODO
    }
}

class SnapshotImpl implements ts.IScriptSnapshot {
    private _lineStartPositions: number[];
    constructor(private text: string) {}
    getText(start: number, end: number) {
        return this.text.substring(start, end);
    }
    getLength() {
        return this.text.length;
    }
    getLineStartPositions() {
        if (! this._lineStartPositions) {
            this._lineStartPositions = ts.computeLineStarts(this.text);
        }
        return this._lineStartPositions;
    }
    getChangeRange(oldSnapshot: SnapshotImpl) {
        var newText = this.text, oldText = oldSnapshot.text;
        var newEnd = newText.length, oldEnd = oldText.length;
        while (newEnd > 0 && oldEnd > 0 && newText.charCodeAt(newEnd) === oldText.charCodeAt(oldEnd)) {
            newEnd--;
            oldEnd--;
        }
        var start = 0, start = 0;
        while (start < oldEnd && start < newEnd && newText.charCodeAt(start) === oldText.charCodeAt(start)) {
            start++;
        }
        return new ts.TextChangeRange(new ts.TextSpan(start, oldEnd - start), newEnd - start);
    }
}

class Program {
    nextVersionId = 0;
    host = new HostImpl();
    service = ts.createLanguageService(this.host, ts.createDocumentRegistry());
    updateFile(fileName: string, newText: string, modified: boolean) {
        this.host.files[fileName] = {
            version: String(this.nextVersionId++),
            isOpen: modified,
            snapshot: new SnapshotImpl(newText)
        };
    }
    deleteFile(fileName: string) {
        delete this.host.files[fileName];
    }
    getDiagnostics(fileName: string) {
        var errs = this.service.getSyntacticDiagnostics(fileName);
        // getSemanticDiagnostics sometimes throws an exception on files with syntax errors
        if (! errs.length) {
            errs = errs.concat(this.service.getSemanticDiagnostics(fileName));
        }
        var lineStarts = this.host.getScriptSnapshot(fileName).getLineStartPositions();
        return errs.map(diag => ({
            line: ts.getLineAndCharacterOfPosition(lineStarts, diag.start).line,
            start: diag.start,
            length: diag.length,
            messageText: diag.messageText,
            category: diag.category,
            code: diag.code
        }));
    }
    getAllDiagnostics() {
        var errs: any = {};
        for (var fileName in this.host.files) {
            errs[fileName] = this.getDiagnostics(fileName);
        }
        return errs;
    }
    getCompletions(fileName: string, position: number, isMemberCompletion: boolean, prefix: string) {
        var service = this.service;
        var info = service.getCompletionsAtPosition(fileName, position);
        prefix = prefix.toLowerCase(); // NetBeans completion is case insensitive
        if (info) {
            return {
                isMemberCompletion: info.isMemberCompletion,
                entries: info.entries.filter(function(e) {
                    return e.name.substr(0, prefix.length).toLowerCase() === prefix;
                })
            };
        }
        return info;
    }
    getCompletionEntryDetails(fileName: string, position: number, entryName: string) {
        return this.service.getCompletionEntryDetails(fileName, position, entryName);
    }
    getQuickInfoAtPosition(fileName: string, position: number) {
        var quickInfo = this.service.getQuickInfoAtPosition(fileName, position);
        return quickInfo && {
            name: this.host.getScriptSnapshot(fileName).getText(quickInfo.textSpan.start(), quickInfo.textSpan.end()),
            kind: quickInfo.kind,
            kindModifiers: quickInfo.kindModifiers,
            start: quickInfo.textSpan.start(),
            end: quickInfo.textSpan.end(),
            displayParts: quickInfo.displayParts,
            documentation: quickInfo.documentation
        };
    }
    getDefsAtPosition(fileName: string, position: number) {
        var defs = this.service.getDefinitionAtPosition(fileName, position);
        return defs && defs.map(di => {
            var lineStarts = this.host.getScriptSnapshot(di.fileName).getLineStartPositions();
            return {
                fileName: di.fileName,
                start: di.textSpan.start(),
                line: ts.getLineAndCharacterOfPosition(lineStarts, di.textSpan.start()).line,
                kind: di.kind,
                name: di.name,
                containerKind: di.containerKind,
                containerName: di.containerName
            };
        });
    }
    getOccurrencesAtPosition(fileName: string, position: number) {
        var occurrences = this.service.getOccurrencesAtPosition(fileName, position);
        return occurrences && occurrences.map(occ => ({
            start: occ.textSpan.start(),
            end: occ.textSpan.end()
        }));
    }
    getNetbeansSemanticHighlights(fileName: string) {
        var sourceFile = this.service.getRealSourceFile(fileName);
        var typeInfoResolver = this.service.getTypeInfoResolver();

        var results: any[] = [];
        var resultByPos: {[pos: number]: any} = {};
        function highlight(start: number, end: number, attr: string) {
            var res = resultByPos[start];
            if (! res) {
                res = {s: start, l: end - start, a: []};
                results.push(res);
                resultByPos[start] = res;
            }
            res.a.push(attr);
        }
        function highlightIdent(node: ts.Identifier, attr: string) {
            // node.pos is too early (includes leading trivia)
            node.text && highlight(node.end - node.text.length, node.end, attr);
        }

        var localDecls: ts.Declaration[] = [];
        var usedSymbols = new Set<ts.Symbol>();

        function isGlobal(decl: ts.Node) {
            do {
                decl = decl.parent;
            } while (! decl.locals);
            return decl.kind === SK.SourceFile && ! ts.isExternalModule(<ts.SourceFile>decl);
        }

        function walk(node: any) {
            if (node.symbol && node.name && node.name.text) {
                var isLocal: boolean;
                if (node.kind === SK.Parameter && ! node.parent.body) {
                    // don't complain about unused parameters in functions with no implementation body
                    isLocal = false;
                } else if (node.symbol.flags & 0x1A00C) {
                    // property, enum member, method, get/set - public by default
                    // is only local if "private" modifier is present
                    isLocal = !! (node.flags & ts.NodeFlags.Private);
                } else {
                    // other symbols are local unless in global scope or exported
                    isLocal = ! (isGlobal(node) || node.localSymbol);
                }
                isLocal && localDecls.push(node);
            }
            if (node.kind === SK.Identifier && node.text) {
                var symbol: ts.Symbol;
                if (node.parent.symbol && node.parent.name === node) {
                    // declaration
                    symbol = node.parent.symbol;
                    if (node.parent.kind === SK.ShorthandPropertyAssignment) {
                        // this isn't just a declaration, but also a usage - of a different symbol
                        usedSymbols.add(typeInfoResolver.getShorthandAssignmentValueSymbol(node.parent));
                    }
                } else {
                    // usage
                    symbol = typeInfoResolver.getSymbolAtLocation(node);
                    if (symbol) {
                        // if this is a generic instantiation, find the original symbol
                        symbol = (<ts.TransientSymbol>symbol).target || symbol;
                        usedSymbols.add(symbol);
                    }
                }
                if (symbol) {
                    var decls = symbol.declarations;
                    if (symbol.flags & ts.SymbolFlags.Deprecated) {
                        highlightIdent(node, 'DEPRECATED');
                    }

                    if (symbol.flags & 0x1800C) {
                        // Property, EnumMember, GetAccessor, SetAccessor
                        highlightIdent(node, 'FIELD');
                    } else if (symbol.flags & 0x7FB) {
                        // var, function, class, interface, enum, module
                        if (isGlobal(decls[0])) {
                            highlightIdent(node, 'GLOBAL');
                        }
                    }
                } else {
                    highlightIdent(node, 'UNDEFINED');
                }
                return;
            }
            switch (node.kind) {
                case SK.Method:
                case SK.FunctionDeclaration:
                case SK.ClassDeclaration:
                case SK.InterfaceDeclaration:
                case SK.TypeAliasDeclaration:
                case SK.EnumDeclaration:
                case SK.ModuleDeclaration:
                    // name.kind could be string (external module decl); don't highlight that
                    if (node.name.kind === SK.Identifier) {
                        highlightIdent(node.name, 'METHOD');
                    }
                    break;
                case SK.Constructor:
                    node.getChildren().forEach(function(n: ts.Node) {
                        if (n.kind === SK.ConstructorKeyword) {
                            highlight(n.end - 11, n.end, 'METHOD');
                        }
                    });
                    break;
                case SK.GetAccessor:
                case SK.SetAccessor:
                    highlight(node.name.pos - 3, node.name.pos, 'METHOD');
                    break;
                case SK.ObjectLiteralExpression:
                    var objType = typeInfoResolver.getContextualType(node);
                    if (objType && (objType.flags & ts.TypeFlags.ObjectType) &&
                            ! typeInfoResolver.getIndexTypeOfType(objType, ts.IndexKind.String)) {
                        (<ts.ObjectLiteralExpression>node).properties.forEach(function(prop) {
                            var name = <any>prop.name;
                            if (! typeInfoResolver.getPropertyOfType(objType, name.text) &&
                                (isNaN(name.text) ||
                                    ! typeInfoResolver.getIndexTypeOfType(objType, ts.IndexKind.Number))) {
                                highlight(ts.skipTrivia(sourceFile.text, prop.name.pos), prop.name.end, 'UNUSED');
                            }
                        });
                    }
                    break;
            }
            ts.forEachChild(node, walk);
        }
        walk(sourceFile);

        localDecls.forEach(function(decl) {
            usedSymbols.has(decl.symbol) || highlightIdent(<any>decl.name, 'UNUSED');
        });
        return results;
    }
    getStructureItems(fileName: string) {
        var sourceFile = this.service.getRealSourceFile(fileName);
        var typeInfoResolver = this.service.getTypeInfoResolver();

        function buildResults(topNode: ts.Node, inFunction: boolean) {
            var results: any[] = [];
            function add(node: ts.Declaration, kind: string, symbol?: ts.Symbol) {
                var name = node.kind === SK.Constructor ? "constructor" : (<any>node.name).text;
                if (! name) { // anonymous function
                    return;
                }
                var res: any = {
                    name: name,
                    kind: kind,
                    kindModifiers: ts.getNodeModifiers(node),
                    start: ts.skipTrivia(sourceFile.text, node.pos),
                    end: node.end
                };
                if (symbol) {
                    var type = typeInfoResolver.getTypeOfSymbolAtLocation(symbol, node);
                    res.type = typeInfoResolver.typeToString(type);
                }
                results.push(res);
                return res;
            }
            function addFunc(node: ts.FunctionLikeDeclaration, kind: string, symbol?: ts.Symbol) {
                if (node.body) {
                    var res = add(node, kind, symbol);
                    res.children = buildResults(node.body, true);
                }
            }
            function addWithHeritage(node: ts.ClassDeclaration | ts.InterfaceDeclaration, kind: string) {
                var res = add(node, kind);
                node.heritageClauses && node.heritageClauses.forEach(hc => {
                    var types = hc.types.map(type => type.getFullText()).join(', ');
                    if (hc.token === SK.ExtendsKeyword) {
                        res.extends = types;
                    } else {
                        res.type = types;
                    }
                });
                return res;
            }
            function visit(node: ts.Node) {
                switch (node.kind) {
                    case SK.Property:
                        add(<ts.PropertyDeclaration>node, SEK.memberVariableElement, node.symbol);
                        break;
                    case SK.Method:
                        addFunc(<ts.MethodDeclaration>node, SEK.memberFunctionElement, node.symbol);
                        break;
                    case SK.Constructor:
                        addFunc(<ts.ConstructorDeclaration>node, SEK.constructorImplementationElement);
                        break;
                    case SK.GetAccessor:
                        addFunc(<ts.AccessorDeclaration>node, SEK.memberGetAccessorElement, node.symbol);
                        break;
                    case SK.SetAccessor:
                        addFunc(<ts.AccessorDeclaration>node, SEK.memberSetAccessorElement, node.symbol);
                        break;
                    case SK.VariableStatement:
                        if (! inFunction) {
                            (<ts.VariableStatement>node).declarations.forEach(function(v) {
                                add(v, SEK.variableElement, v.symbol);
                            });
                        }
                        break;
                    case SK.FunctionDeclaration:
                        addFunc(<ts.FunctionDeclaration>node, SEK.functionElement, node.symbol);
                        break;
                    case SK.ClassDeclaration:
                        var res = addWithHeritage(<ts.ClassDeclaration>node, SEK.classElement);
                        res.children = buildResults(node, false);
                        break;
                    case SK.InterfaceDeclaration:
                        addWithHeritage(<ts.InterfaceDeclaration>node, SEK.interfaceElement);
                        break;
                    case SK.EnumDeclaration:
                        add(<ts.EnumDeclaration>node, SEK.enumElement);
                        break;
                    case SK.ModuleDeclaration:
                        var res = add(<ts.ModuleDeclaration>node, SEK.moduleElement);
                        res.children = buildResults(node, false);
                        break;
                    case SK.ModuleBlock:
                        (<ts.ModuleBlock>node).statements.forEach(visit);
                        break;
                }
            }
            ts.forEachChild(topNode, visit);
            return results;
        }
        return buildResults(sourceFile, false);
    }
    getFolds(fileName: string) {
        return this.service.getOutliningSpans(fileName).map(os => ({
            start: os.textSpan.start(),
            end: os.textSpan.end()
        }));
    }
    getReferencesAtPosition(fileName: string, position: number) {
        var refs = this.service.getReferencesAtPosition(fileName, position);
        return refs && refs.map(ref => {
            var snapshot = this.host.getScriptSnapshot(ref.fileName);
            var lineStarts = snapshot.getLineStartPositions();
            var line = ts.getLineAndCharacterOfPosition(lineStarts, ref.textSpan.start()).line;
            return {
                fileName: ref.fileName,
                isWriteAccess: ref.isWriteAccess,
                start: ref.textSpan.start(),
                end: ref.textSpan.end(),
                lineStart: lineStarts[line - 1],
                lineText: snapshot.getText(lineStarts[line - 1], lineStarts[line])
            };
        });
    }
}

require('readline').createInterface(process.stdin, process.stdout).on('line', (l: string) => {
    try {
        var r = JSON.stringify(eval(l));
    } catch (error) {
        r = 'X' + JSON.stringify(error.stack);
    }
    process.stdout.write(r + '\n');
});
