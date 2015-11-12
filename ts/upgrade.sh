#!/bin/sh

if [ $# != 2 ]
then
	echo Upgrade to a new version of TypeScript and apply local changes.
	echo Usage: $0 old_version new_version
	exit
fi
OLDSRC=~/TypeScript-$1/src
NEWSRC=~/TypeScript-$2/src
for dir in "$OLDSRC" "$NEWSRC"
do
	if [ ! -d "$dir" ]
	then
		echo "$dir" is not a directory
		exit
	fi
done

diff -ru "$OLDSRC" . > diffs.tmp
find compiler services -type f -exec cp "$NEWSRC/{}" {} \;
patch --no-backup-if-mismatch -p1 -i diffs.tmp
rm diffs.tmp
