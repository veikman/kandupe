(ns kandupe.core
  (:require [clojure.string :as string]
            [clojure.java.io :refer [input-stream]]
            [clojure.xml :as xml]
            [hiccup.core :refer [html]]))

(def inputfile "resources/kanjidic2.xml")
(def csv-separator "\t")  ; The tab character is used by default in Anki.

(def kanjidic-jouyou-grades #{1 2 3 4 5 6 8})

(def whole-meaning-blacklist
  #{"(used only in compounds)"
    "(kokuji)"
    "{kokuji}"  ; Variant of "(kokuji)".
    "?"})

(def word-blacklist
  "Words considered meaningless for finding similarities."
  #{""
    "a"
    "-"  ; eg. "Ipomoea aquatica - used as a vegetable".
    "&"
    "an"
    "as"
    "at"
    "be"
    "by"
    "for"
    "in"
    "is"
    "it"
    "go"
    "of"
    "on"
    "or"
    "to"
    "up"
    "and"
    "the"  ; e.g. “to be the first”.
    "put"
    "etc."
    "e.g."
    "off"
    "out"  ; e.g. “throw out”, “bail out”.
    "from"
    "with"
    "one's"
    "counter"  ; Counting suffixes.
    "radical"})  ; Kanji used as radicals; these share no meaning.

(def word-values
  "Value multipliers for non-blacklisted words appearing in phrases."
  {"our" 0.2
   "one" 0.5  ; Oneself or e.g. “one foot two inches”
   "not" 0.1
   "two" 0.1  ; e.g. “two pieces of jade”.
   "away" 0.4  ; e.g. “explain away”.
   "have" 0.2
   "into" 0.1
   "kind" 0.2  ; “kind of”.
   "name" 0.3  ; Used in lots of proper nouns (mountains, species etc.).
   "over" 0.1
   "small" 0.5
   "tree" 0.4  ; 74 different varieties.
   "type" 0.2
   "used" 0.1
   "very" 0.3
   "found" 0.7
   "large" 0.6})

(def whole-meaning-corrections
  ; Quotation marks break CSV:
  {"2nd character of the \"branches\"" ["2nd character of the “branches”"]
   "(transliteration of Sanskrit \"kh\")" ["transliteration of Sanskrit “kh”"]
   "honorific for \"you\"" ["second-person honorific"]
   "var of \"to be\"" ["to be"]
   "\"summer\"" ["summer"]
   "used in \"meiboku sendaihagi\"" []
   "transliteration of Sanskrit \"u\"" ["transliteration of Sanskrit “u”"]
   "transliteration of Sanskrit \"kSo\"" ["transliteration of Sanskrit “kSo”"]
   "\"male\" principle" ["masculine principle"]
   ; Mojibake:
   "grease_ lard_ etc." ["grease" "lard"]
   "to quell (uprising_ rebellion_ etc.) to punish (another nation_ etc.) by force of arms" ["quell" "punish by force of arms"]
   "plan or methods_ etc." ["plan" "method"]
   "in ancient times_ article for preparing the body for the coffin (something slipped on the hand of the dead" ["something slipped on the hand of the dead for burial"]
   "to temper iron or steel for making swords_ etc. (also used figuratively)" ["temper iron or steel"]
   "arena for drill_ etc." ["arena"]
   ; Other problems, mostly case/spelling/punctuation:
   "river in northern china", ["river in northern China"]
   "insects which eat into clot" ["insects which eat into cloth"]
   "vomitting" ["vomiting"]
   "uninterruptedto tie together" ["uninterrupted" "to tie together"]
   "rever" ["revere"]
   "very-usually of objectionable things" ["very (usually of objectionable things)"]
   "brown eared bulbul" ["brown-eared bulbul"]
   "%" ["percent"]})

(def word-substitutions
  {"3rd" "third"
   "7th" "seventh"
   "Dr." "doctor"
   "3" "three"
   "II" "two"})

(defn substitute-word [word] (get word-substitutions word word))

(defn sememize-word
  "Trim a word for use in word-by-word comparisons.
  This will reduce e.g. “suddenly” to “sudden” for a direct match.
  This is brutal enough that it will sometimes make the word unreadable,
  but should not make it ambiguous in meaning."
  [word]
  (-> word
      substitute-word
      (string/replace #"[,?]$" "")
      (string/replace #"(ly|s|ed)$" "")))

(defn sememize-words [words] (map sememize-word words))

(defn order-key
  "In tabular outputs, order key by rising grade, defaulting to a value higher
  than any grade in KANJI, and then by O’Neill index, and finally in character
  (Java string comparison) order."
  [[kanji {:keys [grade oneill]}]]
  [(or grade 100) (or oneill 10000) kanji])

(defn html-row
  [[character {:keys [oneill grade meanings relations]}]]
  (let [{:keys [jouyou other]} relations
        overlap (fn [coll]
                  (interpose ", " (for [k coll] [:a {:href (str "#" k)} k])))]
    [:tr
     [:td oneill]
     [:td grade]
     [:td {:id character} character]
     [:td (string/join "; " (sort (keys meanings)))]
     [:td (overlap jouyou)]
     [:td (overlap other)]]))

(defn html-table
  [data]
  (html
    [:table {:class "table"}
     [:thead
      [:tr
       [:th "O’Neill"]
       [:th "Grade"]
       [:th "Character"]
       [:th "KANJIDIC meanings"]
       [:th "<i>Jōyō</i> overlap"]
       [:th "Other overlap"]]]
     [:tbody
      (for [datum (sort-by order-key data)]
        (html-row datum))]]))

(defn csv-row
  "A row of CSV for spaced-repetition applications.
  The number of stated matches is limited here, for ease of reading."
  [[character {:keys [oneill meanings relations]}]]
  (string/join csv-separator
    [character
     oneill
     (string/join "; " (sort (keys meanings)))
     (string/join " " (take 6 (:jouyou relations)))
     (string/join " " (take 6 (:other relations)))]))

(defn tag-is [target] (fn [{:keys [tag]}] (= tag target)))

(defn filter-tag [tag coll] (filter (tag-is tag) coll))

(defn grab-content [tag coll]
  (let [elements (filter-tag tag coll)]
    (if (= (count elements) 1)
      (:content (first elements))
      (map :content elements))))

(defn content-in
  [coll tags]
  (reduce (fn [coll tag] (grab-content tag coll)) coll tags))

(defn drop-blacklisted-meanings
  [meanings]
  (apply disj meanings whole-meaning-blacklist))

(defn drop-blacklisted-words
  [words]
  (apply disj words word-blacklist))

(defn correct-whole-meaning
  [meaning]
  (get whole-meaning-corrections meaning [meaning]))

(defn massage-meanings
  "Do various processing of KANJIDIC’s meanings.
  Use only English, for simplicity."
  [raw]
  (->> raw
       (remove #(some? (:attrs %)))    ; Select English.
       (map #(get-in % [:content 0]))  ; Select readings (strings).
       set  ; Resolve map.
       drop-blacklisted-meanings
       (map correct-whole-meaning)
       (apply concat)  ; Resolve one-to-many nature of corrections.
       set))

(defn add-extra-words
  "Add any extra word tags based on a source meaning."
  [raw meaning]
  (if (string/includes? meaning "10**") (conj raw "10**") raw))

(defn find-words
  "Get words from a meaning."
  [meaning]
  (-> meaning
      (string/replace #"( )?(\([^\)]*\))( )?" "")  ; Elide parentheticals.
      (string/split #" ")  ; Split on space.
      set
      drop-blacklisted-words
      sememize-words
      (add-extra-words meaning)))

(defn rate-words
  "Rate the importance of each word within a meaning of a kanji."
  [meaning]
  (let [rate (fn [word] (/ (* (get word-values word 1) (count word))
                           (count meaning)))]
    (reduce
      (fn [coll word] (assoc coll word (rate word)))
      {}
      (find-words meaning))))

(defn evaluate-by-meaning
  "Compare the meanings of a kanji to a map of all meanings.
  Using frequencies, rate similarity as 1 per shared meaning."
  [global local]
  (->> local
       (mapcat (partial get global))
       frequencies))

(defn evaluate-by-word
  "Compare the words in the meanings of a kanji to a map of all words.
  Sum up (without overwriting) the ratings of each match into a map of kanji
  to ratings."
  [global local]
  (->> local
       (mapcat (partial get global))
       (map (partial apply hash-map))
       (apply (partial merge-with +))))

(defn sort-relations
  "Sort kanji similar to a subject kanji into two buckets and with an internal
  order based on the degree of similarity."
  [dictionary others]
  (let [relations (keys others)
        jouyou? (fn [kanji] (get-in dictionary [kanji :jouyou]))
        sorted (fn [filter-fn]
                 (->> (filter filter-fn relations)
                      (sort-by (partial get others))
                      (reverse)))]
    {:ratings others
     :jouyou (sorted jouyou?)
     :other (sorted (complement jouyou?))}))

(defn match-kanji
  "Compare one kanji to all others. Add a map of similar kanji to a rating
  of that similarity."
  [all-meanings all-words coll kanji {:keys [meanings]}]
  (let [totals (merge-with +
                 (evaluate-by-meaning all-meanings (keys meanings))
                 (evaluate-by-word all-words (mapcat keys (vals meanings))))]
    (assoc-in coll [kanji :relations]
      (sort-relations coll (dissoc totals kanji)))))

(defn meaning-kanji-pairs
  [base]
  (apply concat
    (reduce-kv
      (fn [coll kanji {:keys [meanings]}]
        (conj coll (into [] (for [m (keys meanings)] [m kanji]))))
      []
      base)))

(defn map-meanings-to-kanji
  [base]
  (let [append (fn [old new] (conj (or old []) new))]
    (reduce
      (fn [coll [meaning kanji]] (update coll meaning append kanji))
      {}
      (meaning-kanji-pairs base))))

(defn to-highest-rated-word
  "A transducer for taking a base map of kanji to various properties,
  outputting key-value pairs of kanji and maps of words to the highest value
  of each word for each kanji."
  [reducer]
  (completing
    (fn [result [kanji {:keys [meanings]}]]
      (reducer result
        [kanji (apply (partial merge-with max) (vals meanings))]))))

(defn to-indexable-order
  "A transducer for rearranging a map of kanji to word to rating into a
  map of word to kanji to rating."
  [reducer]
  (completing
    (fn [result [kanji word-to-rating]]
      (reducer result
        (mapcat (fn [[word rating]] [word kanji rating]) word-to-rating)))))

(defn to-metamap
  [reducer]
  (completing
    (fn [result [word kanji rating]]
      (reducer result
        (when word  ; Drop nil.
          [word (merge (get result word {}) {kanji rating})])))))

(def by-word
  "A composite transducer."
  (comp
    to-highest-rated-word
    to-indexable-order
    to-metamap))

(defn meaningless? [[_ {:keys [meanings]}]] (empty? meanings))

(defn select-kanji
  "Fetch and preprocess data on a single kanji out of KANJIDIC."
  [{:keys [content]}]
  (let [grade-str (first (content-in content [:misc :grade]))
        grade-int (when-not (empty? grade-str) (Integer/parseInt grade-str))
        rmgroup (content-in content [:reading_meaning :rmgroup])
        meanings (into {} (map (fn [m] [m (rate-words m)])
                               (massage-meanings (filter-tag :meaning rmgroup))))
        oneill-kk (filter #(= (get-in % [:attrs :dr_type]) "oneill_kk")
                          (grab-content :dic_number content))
        oneill-str (first (:content (first oneill-kk)))]
    [(first (grab-content :literal content))
     {:grade grade-int
      :jouyou (contains? kanjidic-jouyou-grades grade-int)
      :oneill (when-not (empty? oneill-str) (Integer/parseInt oneill-str))
      :meanings meanings}]))

(defn write [filename content] (spit (str "output/" filename) content))

(defn frequency-csv
  "Write a CSV as a byproduct."
  [filename content]
  (write filename (string/join "\n" (map #(string/join csv-separator %)
                                         (frequencies content)))))

(defn work
  [kdic]
  (let [kseq (filter-tag :character (:content kdic))
        selected (map select-kanji kseq)
        meaningless (set (map first (filter meaningless? selected)))
        base (into {} (remove meaningless? selected))
        all-meanings (->> base vals (mapcat :meanings) (into {}))
        meanings (map-meanings-to-kanji base)
        words (into {} by-word base)
        rich (reduce-kv (partial match-kanji meanings words) base base)]
    (frequency-csv "meaning_frequencies.csv" (keys all-meanings))
    (frequency-csv "word_frequencies.csv" (mapcat keys (vals all-meanings)))
    (write "spaced_repetition.csv" (string/join "\n" (map csv-row rich)))
    (write "meaningless.txt" (string/join " " (sort meaningless)))
    (write "table.htm" (html-table rich))))

(defn read-file [] (-> inputfile slurp .getBytes input-stream xml/parse))

(defn -main
  "Find duplicate proposed meanings in KANJIDIC."
  [& args]
  (work (read-file)))
