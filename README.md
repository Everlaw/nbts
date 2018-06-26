**IMPORTANT: On NetBeans 9, use only version 2.9.1.1 or above.** Using any previous version of this plugin on NetBeans 9 will cause severe IDE-wide indexing slowdown.

## NetBeans TypeScript Editor

This plugin allows editing of TypeScript code within NetBeans. Many IDE features are supported:

* Code completion
* Error checking
* Find usages
* Go to Declaration
* Syntax highlighting

<img src="screenshot.png">

### Installation

For this plugin to work, you will need:
* NetBeans 8.0.2 or later
* Node.js 0.8.0 or later
* TypeScript 1.5.3 or later

Download the latest netbeanstypescript.nbm file from the [Releases](https://github.com/Everlaw/nbts/releases) page. Then, in NetBeans, go to Tools > Plugins, and select the "Downloaded" tab. Click "Add Plugins..." and locate the netbeanstypescript.nbm file. The TypeScript Editor should appear in the list. Select it and click "Install".

Open a .ts file, right-click on its source code window and select "TypeScript Setup...". Locate the "lib" directory from your TypeScript installation.

### Notes

* All .ts/.tsx files under a directory containing a tsconfig.json file are assumed to be part of that TypeScript project.
* By default, "implicit any" errors are enabled, but are shown as warnings rather than errors. You may explicitly specify `"noImplicitAny": false` in a TypeScript project's tsconfig.json to disable "implicit any" errors altogether.

### Contributing

We are happy to receive Pull Requests. If you are planning a big change, it's probably best to discuss it as an [Issue](https://github.com/Everlaw/nbts/issues) first.

### Building

To build the plugin yourself, you may need to make some small edits to `build.xml`. See the comments in that file for details.
