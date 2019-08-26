(ns aiok-dupe.core
  (:require [clojure.string :as string]))

(def ortho-sep #"\|")
(def meaning-sep #",")

(defn cluster
  [kanji coll meaning]
  (update coll (string/trim meaning) #(conj (or % []) kanji)))

(defn organize
  [coll line]
  (let [[k mm] (string/split line ortho-sep)]
    (if (nil? mm)
      coll
      (reduce (partial cluster k) coll (string/split mm meaning-sep)))))

(defn by-count
  [coll item]
  (update coll (count (second item)) #(conj (or % {}) item)))

(defn -main
  "Find duplicate proposed meanings in a kanji deck."
  [& args]
  (let [lines (-> "resources/aiok.txt" slurp string/split-lines)
        by-word (dissoc (reduce organize {} lines) "(kokuji)")
        dupes-only (dissoc (reduce by-count {} by-word) 1)
        sorted (reverse (sort dupes-only))]
    (println (string/join "\n" sorted))))
