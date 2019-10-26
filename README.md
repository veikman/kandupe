# `kandupe`

A script to show the overlapping meanings of kanji, for educational purposes.

## Installation

Clone this repository with Git. There’s no real need to compile the code if
you also get [Leiningen](https://leiningen.org/).

## Usage

Download `kanjidic2` from
[EDRDG](http://www.edrdg.org/wiki/index.php/KANJIDIC_Project). Unzip the XML
file as `kanjidic2.xml` in the `resources` folder of this project.

Then, with $ representing your terminal prompt:

$ lein run

## Outputs

The following files end up in the `output` folder.

* `meaning_frequencies.csv`: Tab-separated table of processed meanings and
  the number of times they appear in the input.
* `word_frequencies.csv`: Tab-separated table of pseudo-sememe words and the
  number of times they were extracted from processed meanings.
* `spaced_repetition.csv`: Tab-separated table of the 6 strongest matches in
  each category, and some other data suitable for use in a spaced-repetition
  learning application such as Anki.
* `meaningless.txt`: A space-separated list of all the kanji in the input that
  are not associated with any meanings. More specifically, with no meanings
  not tagged with a language, i.e. no English-language meanings.
* `table.htm`: A relatively complex HTML table with complete sets of matches.

To merge CSV from this application into an Anki deck, you might try applying
`awk` or a spreadsheet `LOOKUP` function to an exported deck, then re-import
it.

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
