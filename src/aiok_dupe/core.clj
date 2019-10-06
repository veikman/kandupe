(ns aiok-dupe.core
  (:require [clojure.string :as string]
            [clojure.set :refer [intersection]]
            [clojure.java.io :refer [input-stream]]
            [clojure.xml :as xml]
            [hiccup.core :refer [html]]))

(def inputfile "resources/kanjidic2.xml")
(def kanjidic-jouyou-grades #{1 2 3 4 5 6 8})

(defn order-key
  [[_ {:keys [grade oneill]}]]
  [(or grade 100) (or oneill 10000)])

(defn html-row
  [[character {:keys [oneill grade meanings relations]}]]
  (let [{:keys [jouyou other]} relations
        overlap (fn [coll]
                  (interpose ", " (for [k coll] [:a {:href (str "#" k)} k])))]
    [:tr
     [:td oneill]
     [:td grade]
     [:td {:id character} character]
     [:td (string/join ", " (sort meanings))]
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
  [[character {:keys [oneill relations]}]]
  (string/join ","
    [character
     oneill
     (string/join " " (:jouyou relations))
     (string/join " " (:other relations))]))

(defn tag-is [target] (fn [{:keys [tag]}] (= tag target)))

(defn filter-tag [tag coll] (filter (tag-is tag) coll))

(defn grab-content [tag coll]
  (let [elements (filter-tag tag coll)]
    (if (= (count elements ) 1)
      (:content (first elements))
      (map :content elements))))

(defn content-in
  [coll tags]
  (reduce
    (fn [coll tag] (grab-content tag coll))
    coll
    tags))

(defn select-kanji
  [{:keys [content]}]
  (let [grade-str (first (content-in content [:misc :grade]))
        grade-int (when-not (empty? grade-str) (Integer/parseInt grade-str))
        rmgroup (content-in content [:reading_meaning :rmgroup])
        meanings (filter-tag :meaning rmgroup)
        english (remove #(some? (:attrs %)) meanings)
        readings (set (map #(get-in % [:content 0]) english))
        dic_number (grab-content :dic_number content)
        oneill_kk (filter #(= (get-in % [:attrs :dr_type]) "oneill_kk")
                          dic_number)
        oneill-str (first (:content (first oneill_kk)))
        oneill-int (when-not (empty? oneill-str) (Integer/parseInt oneill-str))]
    [(first (grab-content :literal content))
     {:grade grade-int
      :jouyou (contains? kanjidic-jouyou-grades grade-int)
      :oneill oneill-int
      :meanings (disj readings "(kokuji)")}]))

(defn rate-overlap
  [a b]
  (let [i (intersection a b)
        [na nb ni] (map count [a b i])
        n (max na nb)]
    (try
      (/ ni n)
      (catch ArithmeticException _ 0))))

(defn compare-to-all
  [{:keys [meanings]} other]
  (reduce
    (fn [coll other-kanji]
      (let [other-meanings (:meanings (second other-kanji))
            rate (rate-overlap meanings other-meanings)]
        (if (zero? rate)
          coll
          (assoc coll (key other-kanji) rate))))
    {}
    other))

(defn enrich-kanji
  [coll key]
  (assoc-in coll [key :similarity-rates]
                 (compare-to-all (get coll key) (dissoc coll key))))

(defn sort-relation
  [{:keys [similarity-rates]} dictionary]
  (let [relations (keys similarity-rates)
        jouyou? (fn [kanji] (get-in dictionary [kanji :jouyou]))
        sorted (fn [filter-fn]
                 (->> (filter filter-fn relations)
                      (sort-by (partial get similarity-rates))
                      (reverse)))]
    {:jouyou (sorted jouyou?)
     :other (sorted (complement jouyou?))}))

(defn sort-relations
  [coll key]
  (assoc-in coll [key :relations]
                 (sort-relation (get coll key) (dissoc coll key))))

(defn work
  [kdic]
  (let [kseq (take 10 (filter-tag :character (:content kdic)))
        base (into {} (map select-kanji kseq))
        kanji (keys base)
        rich (reduce enrich-kanji base kanji)
        richer (reduce sort-relations rich kanji)]
    ; (spit "output.htm" (html-table richer))
    (spit "output.csv" (string/join "\n" (map csv-row richer)))))

(defn -main
  "Find duplicate proposed meanings in KANJIDIC."
  [& args]
  (let [kdic (-> inputfile slurp .getBytes input-stream xml/parse)]
    (work kdic)))
