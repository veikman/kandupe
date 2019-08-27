# `aiok-dupe`

A script to show the overlapping meanings of kanji, for educational purposes.

## Installation

Clone this repository with Git. There’s no real need to compile the code if
you also get [Leiningen](https://leiningen.org/).

## Usage

$ lein run

## Raw data

The input data under `resources` is excerpted from the uncredited
“All in One Kanji Deck” [on AnkiWeb](https://ankiweb.net/shared/info/798002504).

Here is how the excerpt was taken:

* Download the deck.
* Rename the file with a .zip extension.
* Decompress it.
* Dump the `flds` field from the deck’s SQLite database and cut out the
  particular sub-fields of interest.

The last step can be done in a shell pipeline on GNU+Linux:

$ sqlite3 collection.anki2 'SELECT flds FROM notes;' | cut -f 1,5 -d
$(echo -e '\x1f') --output-delimiter '|' > aiok.txt

## License

Source code copyright © 2019 Viktor Eikman.

This program is made available under the terms of the Eclipse Public License
2.0 which is available at http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published
by the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

The “All in One Kanji Deck”, distinct from the source code and unaffiliated
with this software project, is licensed as per
[AnkiWeb terms and conditions](https://ankiweb.net/account/terms).
