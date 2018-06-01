/*
 * Copyright (C) 2015 Everlaw
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace ts {
    export interface Symbol {
        nbtsDeprecated?: boolean;
    }

    var { bindSourceFile, getNodeModifiers } = ts;
    ts.bindSourceFile = function(file, options) {
        bindSourceFile(file, options);
        var next = -1;
        // Find all declarations with a preceding @deprecated comment and mark their symbols as such
        forEachChild(file, function visit(node: Node) {
            if (next >= node.end) return;
            if (next < node.pos) {
                next = file.text.indexOf("@deprecated", node.pos) >>> 0;
            }
            if (next < skipTrivia(file.text, node.pos)) {
                if (node.kind === SyntaxKind.ModuleDeclaration) {
                    while ((<ModuleDeclaration>node).body &&
                        (<ModuleDeclaration>node).body.kind === SyntaxKind.ModuleDeclaration) {
                         node = (<ModuleDeclaration>node).body;
                    }
                } else if (node.kind === SyntaxKind.VariableStatement) {
                    forEach((<VariableStatement>node).declarationList.declarations, decl => {
                        decl.symbol.nbtsDeprecated = true;
                    });
                }
                if (node.symbol) node.symbol.nbtsDeprecated = true;
                next = file.text.indexOf("@deprecated", next + 11) >>> 0;
                if (next >= node.end) return;
            }
            forEachChild(node, visit);
        });
    };
    ts.getNodeModifiers = function(node) {
        var result = getNodeModifiers(node);
        if (node.symbol && node.symbol.nbtsDeprecated) {
            result += (result && ",") + "deprecated";
        }
        return result;
    };
}

import SK = ts.SyntaxKind;
import SEK = ts.ScriptElementKind;

// Node.js stuff
declare var require: any;
declare module process { var stdin: any, stdout: any; }
declare class Set<T> { add(t: T): void; has(t: T): boolean; }

var version = 0;
var files: {[name: string]: {version: string; snapshot: SnapshotImpl}} = {};
var builtinLibs: {[name: string]: string} = {};
var docRegistry = ts.createDocumentRegistry(ts.sys.useCaseSensitiveFileNames);

var implicitAnyErrors: {[code: number]: boolean} = {};
[[2602, 2602], [7000, 7026], [7031, 7034]].forEach(([lo, hi]) => {
    for (var code = lo; code <= hi; code++) {
        implicitAnyErrors[code] = true;
    }
});

class HostImpl implements ts.LanguageServiceHost {
    cachedConfig: {
        parseError: ts.Diagnostic;
        pcl: ts.ParsedCommandLine;
    } = null;
    constructor(public path: string, public isConfig: boolean) {}
    log(s: string) {
        process.stdout.write('L' + JSON.stringify(s) + '\n');
    }
    getCompilationSettings() {
        var options = this.configUpToDate().pcl.options;
        var settings: ts.CompilerOptions = Object.create(options);
        if (options.noImplicitAny == null) {
            // report implicit-any errors anyway, but only as warnings (see getDiagnostics)
            settings.noImplicitAny = true;
        }
        return settings;
    }
    getNewLine() {
        return ts.getNewLineCharacter(this.configUpToDate().pcl.options);
    }
    getProjectVersion() {
        return String(version);
    }
    getScriptFileNames() {
        return this.configUpToDate().pcl.fileNames;
    }
    getScriptVersion(fileName: string) {
        if (fileName in builtinLibs) {
            return "0";
        } else if (files[fileName]) {
            return files[fileName].version;
        }
        return this.getProjectVersion();
    }
    getScriptSnapshot(fileName: string): ts.IScriptSnapshot {
        if (fileName in builtinLibs) {
            return new SnapshotImpl(builtinLibs[fileName]);
        } else if (files[fileName]) {
            return files[fileName].snapshot;
        }
        var text = ts.sys.readFile(fileName);
        return typeof text === 'string' ? new SnapshotImpl(text) : undefined;
    }
    getCurrentDirectory() {
        return "";
    }
    getDefaultLibFileName(options: ts.CompilerOptions): string {
        return "(builtin)/" + ts.getDefaultLibFileName(options);
    }
    useCaseSensitiveFileNames() {
        return ts.sys.useCaseSensitiveFileNames;
    }
    readDirectory(path: string, extensions?: string[], exclude?: string[], include?: string[]) {
        return ts.sys.readDirectory(path, extensions, exclude, include);
    }
    readFile(path: string, encoding?: string) {
        return ts.sys.readFile(path, encoding);
    }
    fileExists(path: string) {
        return ts.sys.fileExists(path);
    }
    directoryExists(directoryName: string) {
        return ts.sys.directoryExists(directoryName);
    }
    getDirectories(directoryName: string) {
        return ts.sys.getDirectories(directoryName);
    }
    configUpToDate() {
        if (! this.cachedConfig) {
            var parsed: { config?: any; error?: ts.Diagnostic } = {};
            if (this.isConfig) {
                parsed = ts.readConfigFile(this.path, this.readFile);
            } else {
                parsed = { config: { files: [this.path] } }
            }
            var dir = ts.getDirectoryPath(this.path);
            this.cachedConfig = {
                parseError: parsed.error,
                pcl: ts.parseJsonConfigFileContent(parsed.config || {}, ts.sys, dir)
            }
        }
        return this.cachedConfig;
    }
}

class SnapshotImpl implements ts.IScriptSnapshot {
    constructor(public text: string) {}
    getText(start: number, end: number) {
        return this.text.substring(start, end);
    }
    getLength() {
        return this.text.length;
    }
    getChangeRange(oldSnapshot: SnapshotImpl): ts.TextChangeRange {
        var newText = this.text, oldText = oldSnapshot.text;
        var newEnd = newText.length, oldEnd = oldText.length;
        while (newEnd > 0 && oldEnd > 0 && newText.charCodeAt(newEnd - 1) === oldText.charCodeAt(oldEnd - 1)) {
            newEnd--;
            oldEnd--;
        }
        var start = 0, start = 0;
        while (start < oldEnd && start < newEnd && newText.charCodeAt(start) === oldText.charCodeAt(start)) {
            start++;
        }
        return { span: { start: start, length: oldEnd - start }, newLength: newEnd - start };
    }
}

class Program {
    service = ts.createLanguageService(this.host, docRegistry);
    constructor(public host: HostImpl) {}
    fileInProject(fileName: string) {
        return !!this.service.getProgram().getSourceFile(ts.normalizeSlashes(fileName));
    }
    getDiagnostics(fileName: string) {
        var config = this.host.configUpToDate();
        if (! this.fileInProject(fileName)) {
            return {
                errs: [],
                metaErrors: ["File " + fileName + " is not in project defined by " + this.host.path]
            };
        }
        function errText(diag: ts.Diagnostic): string {
            return ts.flattenDiagnosticMessageText(diag.messageText, "\n");
        }
        var metaErrors = config.parseError
                ? [errText(config.parseError)]
                : config.pcl.errors.map(diag => this.host.path + ": " + errText(diag));
        this.service.getCompilerOptionsDiagnostics().forEach(diag => {
            metaErrors.push("Project error: " + errText(diag));
        });
        var mapDiag = (diag: ts.Diagnostic) => ({
            line: ts.getLineAndCharacterOfPosition(diag.file, diag.start).line + 1,
            start: diag.start,
            length: diag.length,
            messageText: errText(diag),
            category: diag.code in implicitAnyErrors && ! config.pcl.options.noImplicitAny
                ? ts.DiagnosticCategory.Warning
                : diag.category,
            code: diag.code
        });
        var errs = this.service.getSyntacticDiagnostics(fileName).map(mapDiag);
        try {
            // In case there are bugs in the type checker, make sure we can handle it throwing an
            // exception and still show the syntactic errors.
            errs = errs.concat(this.service.getSemanticDiagnostics(fileName).map(mapDiag));
        } catch (e) {
            metaErrors.push("Error in getSemanticDiagnostics\n\n" + e.stack);
        }
        return { errs, metaErrors };
    }
    getCompletions(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getCompletionsAtPosition(fileName, position, void 0);
    }
    getCompletionEntryDetails(fileName: string, position: number, entryName: string) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getCompletionEntryDetails(fileName, position, entryName, void 0, void 0, void 0);
    }
    getCompletionEntryLocation(fileName: string, position: number, entryName: string) {
        if (! this.fileInProject(fileName)) return null;
        var sym = this.service.getCompletionEntrySymbol(fileName, position, entryName, void 0);
        if (sym) {
            var decl = sym.declarations[0];
            if (decl) {
                var sourceFile = decl.getSourceFile();
                return {
                    fileName: sourceFile.fileName,
                    start: ts.skipTrivia(sourceFile.text, decl.pos),
                    end: decl.end
                };
            }
        }
        return null;
    }
    getQuickInfoAtPosition(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        var quickInfo = this.service.getQuickInfoAtPosition(fileName, position);
        return quickInfo && {
            name: this.host.getScriptSnapshot(fileName).getText(quickInfo.textSpan.start, quickInfo.textSpan.start + quickInfo.textSpan.length),
            kind: quickInfo.kind,
            kindModifiers: quickInfo.kindModifiers,
            start: quickInfo.textSpan.start,
            end: quickInfo.textSpan.start + quickInfo.textSpan.length,
            displayParts: quickInfo.displayParts,
            documentation: quickInfo.documentation
        };
    }
    getDefsAtPosition(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        var defs = this.service.getDefinitionAtPosition(fileName, position);
        var program = this.service.getProgram();
        return defs && defs.map(di => {
            var sourceFile = program.getSourceFile(di.fileName);
            return {
                fileName: di.fileName,
                start: di.textSpan.start,
                line: sourceFile.getLineAndCharacterOfPosition(di.textSpan.start).line + 1,
                kind: di.kind,
                name: di.name,
                containerKind: di.containerKind,
                containerName: di.containerName
            };
        });
    }
    getOccurrencesAtPosition(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        var occurrences = this.service.getOccurrencesAtPosition(fileName, position);
        return occurrences && occurrences.map(occ => ({
            start: occ.textSpan.start,
            end: occ.textSpan.start + occ.textSpan.length
        }));
    }
    getSemanticHighlights(fileName: string) {
        var program = this.service.getProgram();
        var sourceFile = program.getSourceFile(ts.normalizeSlashes(fileName));
        if (! sourceFile) return null;
        var typeInfoResolver = program.getTypeChecker();

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

        var localDecls: ts.NamedDeclaration[] = [];
        var usedSymbols = new Set<ts.Symbol>();

        function isGlobal(decl: ts.Node) {
            switch (decl.kind) {
                case SK.FunctionExpression:
                case SK.ClassExpression:
                case SK.SourceFile:
                    return false;
            }
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
                } else if (node.kind === SK.ExportSpecifier) {
                    usedSymbols.add(typeInfoResolver.getAliasedSymbol(node.symbol));
                    isLocal = false;
                } else if (node.symbol.flags & 0x1A00C) {
                    // property, enum member, method, get/set - public by default
                    // is only local if "private" modifier is present
                    isLocal = ts.hasModifier(node, ts.ModifierFlags.Private);
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
                } else {
                    // usage
                    // TODO: In code like "import A = X; import B = A.foo;" this does not do quite
                    // what we want. For the A in A.foo, it returns the aliased symbol X rather than
                    // the alias A, so we fail to recognize that the alias A is used.
                    symbol = typeInfoResolver.getSymbolAtLocation(node);
                    if (symbol) {
                        // if this is a generic instantiation, find the original symbol
                        symbol = (<ts.TransientSymbol>symbol).target || symbol;
                        usedSymbols.add(symbol);
                    }
                }
                if (symbol) {
                    if (symbol.nbtsDeprecated) {
                        highlightIdent(node, 'DEPRECATED');
                    }

                    if (symbol.flags & 0x1800C) {
                        // Property, EnumMember, GetAccessor, SetAccessor
                        highlightIdent(node, 'FIELD');
                    } else if (symbol.flags & ts.SymbolFlags.ModuleMember) {
                        // var, function, class, interface, enum, module, type alias, alias
                        if (isGlobal(symbol.declarations[0])) {
                            highlightIdent(node, 'GLOBAL');
                        }
                    }
                } else {
                    highlightIdent(node, 'UNDEFINED');
                }
                return;
            }
            switch (node.kind) {
                case SK.MethodDeclaration:
                case SK.FunctionExpression:
                case SK.FunctionDeclaration:
                    // For MethodDeclaration, name.kind could be string literal
                    if (node.name && node.name.kind === SK.Identifier) {
                        highlightIdent(node.name, 'METHOD');
                    }
                    break;
                case SK.ClassExpression:
                case SK.ClassDeclaration:
                case SK.InterfaceDeclaration:
                case SK.TypeAliasDeclaration:
                case SK.EnumDeclaration:
                case SK.ModuleDeclaration:
                    // name.kind could be string (external module decl); don't highlight that
                    if (node.name && node.name.kind === SK.Identifier) {
                        highlightIdent(node.name, 'CLASS');
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
                case SK.ShorthandPropertyAssignment:
                    // this isn't just a declaration, but also a usage - of a different symbol
                    usedSymbols.add(typeInfoResolver.getShorthandAssignmentValueSymbol(node));
                    break;
                case SK.ImportSpecifier:
                case SK.ExportSpecifier:
                    if (typeInfoResolver.getAliasedSymbol(node.symbol).nbtsDeprecated) {
                        highlightIdent(node.propertyName || node.name, 'DEPRECATED');
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
        var program = this.service.getProgram();
        var sourceFile = program.getSourceFile(ts.normalizeSlashes(fileName));
        if (! sourceFile) return null;
        var typeInfoResolver = program.getTypeChecker();

        function buildResults(topNode: ts.Node, inFunction: boolean, baseTypes?: [ts.Type, boolean][]) {
            var results: any[] = [];
            function add(node: ts.NamedDeclaration, kind: string, symbol?: ts.Symbol) {
                var res: any = {
                    name: node.name && (<any>node.name).text || "<unnamed>",
                    kind: kind,
                    kindModifiers: ts.getNodeModifiers(node),
                    start: ts.skipTrivia(sourceFile.text, node.pos),
                    end: node.end
                };
                if (symbol) {
                    var type = typeInfoResolver.getTypeOfSymbolAtLocation(symbol, node);
                    res.type = typeInfoResolver.typeToString(type);
                    if (baseTypes && symbol.name && ! ts.hasModifier(node, ts.ModifierFlags.Static)) {
                        var overrides: any[] = [];
                        baseTypes.forEach(([baseType, isImplements]) => {
                            var baseSym = typeInfoResolver.getPropertyOfType(baseType, symbol.name);
                            if (! baseSym) return;
                            var baseDecl = baseSym.valueDeclaration;
                            var baseSource = baseDecl.getSourceFile();
                            overrides.push({
                                fileName: baseSource.fileName,
                                start: ts.skipTrivia(baseSource.text, baseDecl.pos),
                                name: typeInfoResolver.symbolToString(baseSym.parent),
                                wasAbstract: isImplements || ts.hasModifier(baseDecl, ts.ModifierFlags.Abstract)
                            });
                        });
                        if (overrides.length) {
                            res.overrides = overrides;
                        }
                    }
                }
                results.push(res);
                return res;
            }
            function addFunc(node: ts.FunctionLikeDeclaration, kind: string, symbol?: ts.Symbol) {
                var res = add(node, kind, symbol);
                if (node.body) {
                    res.children = buildResults(node.body, true);
                }
                return res;
            }
            function addClass(node: ts.ClassDeclaration | ts.InterfaceDeclaration, kind: string) {
                var res = add(node, kind);
                var baseTypes: [ts.Type, boolean][] = [];
                node.heritageClauses && node.heritageClauses.forEach(hc => {
                    var isExtends = hc.token === SK.ExtendsKeyword;
                    var typeNames = hc.types.map(typeNode => {
                        const t = typeInfoResolver.getTypeAtLocation(typeNode);
                        t && baseTypes.push([t, ! isExtends]);
                        return typeNode.getFullText();
                    }).join(", ");
                    res[isExtends ? "extends" : "type"] = typeNames;
                });
                res.children = buildResults(node, false, baseTypes);
            }
            function visit(node: ts.Node) {
                switch (node.kind) {
                    case SK.PropertyDeclaration:
                    case SK.PropertySignature:
                        add(<ts.PropertyDeclaration | ts.PropertySignature>node, SEK.memberVariableElement, node.symbol);
                        break;
                    case SK.MethodDeclaration:
                        addFunc(<ts.MethodDeclaration>node, SEK.memberFunctionElement, node.symbol);
                        break;
                    case SK.MethodSignature:
                        add(<ts.MethodSignature>node, SEK.memberFunctionElement, node.symbol);
                        break;
                    case SK.Constructor:
                        var res = addFunc(<ts.ConstructorDeclaration>node, SEK.constructorImplementationElement);
                        res.name = "constructor";
                        (<ts.ConstructorDeclaration>node).parameters.forEach(function(p) {
                            if (ts.hasModifier(p, ts.ModifierFlags.ParameterPropertyModifier))
                                add(p, SEK.memberVariableElement, p.symbol);
                        });
                        break;
                    case SK.GetAccessor:
                        addFunc(<ts.AccessorDeclaration>node, SEK.memberGetAccessorElement, node.symbol);
                        break;
                    case SK.SetAccessor:
                        addFunc(<ts.AccessorDeclaration>node, SEK.memberSetAccessorElement, node.symbol);
                        break;
                    case SK.VariableStatement:
                        if (! inFunction) {
                            (<ts.VariableStatement>node).declarationList.declarations.forEach(function(v) {
                                add(v, SEK.variableElement, v.symbol);
                            });
                        }
                        break;
                    case SK.FunctionDeclaration:
                        addFunc(<ts.FunctionDeclaration>node, SEK.functionElement, node.symbol);
                        break;
                    case SK.ClassDeclaration:
                        addClass(<ts.ClassDeclaration>node, SEK.classElement);
                        break;
                    case SK.InterfaceDeclaration:
                        addClass(<ts.InterfaceDeclaration>node, SEK.interfaceElement);
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
        // ok if file not in project
        return this.service.getOutliningSpans(fileName).map(os => ({
            start: os.textSpan.start,
            end: os.textSpan.start + os.textSpan.length
        }));
    }
    getReferencesAtPosition(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        var refs = this.service.getReferencesAtPosition(fileName, position);
        var program = this.service.getProgram();
        return refs && refs.map(ref => {
            var file = program.getSourceFile(ref.fileName);
            var lineStarts = file.getLineStarts();
            var line = ts.computeLineAndCharacterOfPosition(lineStarts, ref.textSpan.start).line;
            return {
                fileName: ref.fileName,
                isWriteAccess: ref.isWriteAccess,
                start: ref.textSpan.start,
                end: ref.textSpan.start + ref.textSpan.length,
                lineStart: lineStarts[line],
                lineText: file.text.substring(lineStarts[line], lineStarts[line + 1])
            };
        });
    }
    getFormattingEdits(fileName: string, start: number, end: number, settings: ts.FormatCodeSettings) {
        // ok if file not in project
        return this.service.getFormattingEditsForRange(fileName, start, end, settings);
    }
    getRenameInfo(fileName: string, position: number) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getRenameInfo(fileName, position);
    }
    findRenameLocations(fileName: string, position: number, findInStrings: boolean, findInComments: boolean) {
        if (! this.fileInProject(fileName)) return null;
        var locs = this.service.findRenameLocations(fileName, position, findInStrings, findInComments);
        return locs && locs.map(loc => {
            return {
                fileName: loc.fileName,
                start: loc.textSpan.start,
                end: loc.textSpan.start + loc.textSpan.length,
            };
        });
    }
    getCompileOnSaveEmitOutput(fileName: string, guiSetting: boolean) {
        var { compileOnSave = guiSetting } = this.host.configUpToDate().pcl.raw;
        if (! compileOnSave || ! this.fileInProject(fileName)) return null;
        return this.service.getEmitOutput(fileName);
    }
    getEmitOutput(fileName: string) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getEmitOutput(fileName);
    }
    getCompilerOptions() {
        return ts.optionDeclarations.map(function optToJson(opt) {
            var res = <any>ts.clone(opt);
            if (typeof opt.type === 'object') {
                res.type = ts.arrayFrom(opt.type.keys());
            } else if (opt.type === 'list') {
                res.element = optToJson((<ts.CommandLineOptionOfListType> opt).element);
            }
            return res;
        });
    }
    getCodeFixesAtPosition(fileName: string, start: number, end: number, errorCodes: number[],
            formatOptions: ts.FormatCodeSettings) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getCodeFixesAtPosition(fileName, start, end, errorCodes, formatOptions, void 0);
    }
    getApplicableRefactors(fileName: string, pos: number, end: number) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getApplicableRefactors(fileName, pos === end ? pos : { pos, end }, void 0);
    }
    getEditsForRefactor(fileName: string, formatOptions: ts.FormatCodeSettings, pos: number, end: number,
            refactorName: string, actionName: string) {
        if (! this.fileInProject(fileName)) return null;
        return this.service.getEditsForRefactor(fileName, formatOptions, pos === end ? pos : { pos, end },
                refactorName, actionName, void 0);
    }
}

var programCache: {[path: string]: Program} = {};

function clearProgramCache() {
    for (var path in programCache) {
        programCache[path] && programCache[path].service.dispose();
    }
    programCache = {};
}

function updateFile(fileName: string, newText: string) {
    version++;
    if (! (fileName in files) || /\.json$/.test(fileName)) {
        clearProgramCache();
    }
    files[fileName] = {
        version: String(version),
        snapshot: new SnapshotImpl(newText)
    };
}

function deleteFile(fileName: string) {
    version++;
    clearProgramCache();
    delete files[fileName];
}

function fileCall(method: keyof Program, fileName: string/*, ...*/) {
    var p = programCache[fileName];
    if (! p) {
        // Walk up the directory tree looking for tsconfig.json
        p = (function getConfiguredProgram(dir: string) {
            if (! (dir in programCache)) {
                var config = ts.combinePaths(dir, "tsconfig.json");
                if (ts.sys.fileExists(config)) {
                    programCache[dir] = new Program(new HostImpl(config, true));
                } else {
                    var parentDir = ts.getDirectoryPath(dir);
                    programCache[dir] = parentDir === dir ? null : getConfiguredProgram(parentDir);
                }
            }
            return programCache[dir];
        })(ts.getDirectoryPath(fileName));
        // If no tsconfig.json found, create a program with only this file
        programCache[fileName] = p || (p = new Program(new HostImpl(fileName, false)));
    }
    return (<Function>p[method]).apply(p, [].slice.call(arguments, 1));
}

require('readline').createInterface(process.stdin, process.stdout).on('line', (l: string) => {
    try {
        var r = JSON.stringify(eval(l));
    } catch (error) {
        r = 'X' + JSON.stringify(error.stack);
    }
    process.stdout.write(r + '\n');
});
