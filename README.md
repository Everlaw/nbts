## NetBeans TypeScript Editor

This plugin allows editing of TypeScript code within NetBeans. Many IDE features are supported:

* Code completion
* Error checking
* Find usages
* Go to Declaration
* Syntax highlighting

### Installation

For this plugin to work, you will need to have Node.js version 0.8.0 or later installed.

In NetBeans, go to Tools > Plugins, and select the "Downloaded" tab. Click "Add Plugins..." and locate the netbeanstypescript.nbm file. The TypeScript Editor should appear in the list. Select it and click "Install".

### Notes

* All .ts/.tsx files under one source root are currently assumed to be part of one TypeScript project.
* By default, "implicit any" errors are enabled, but are shown as warnings rather than errors. You may explicitly specify `"noImplicitAny": false` in a TypeScript project's tsconfig.json to disable "implicit any" errors altogether.
* Compile-on-save, automatic reformatting, and multi-file rename are not implemented yet.
