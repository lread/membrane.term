(ns com.phronemophobic.membrane.term.main
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [com.phronemophobic.membrane.term :as term]
            [docopt.core :as docopt]))

(def docopt-usage
  "membrane.term

Usage:
  membrane.term run-term [--width=<cols>] [--height=<rows>] \\
   [--color-scheme=<path>] [--font-family=<font>] [--font-size=<points>]
  membrane.term screenshot --play=<path> [--width=<cols>] [--height=<rows>] \\
   [--color-scheme=<path>] [--font-family=<font>] [--font-size=<points>]\\
   [--out=<file>] [--line-delay=<ms>] [--final-delay=<ms>]
  membrane.term --help

Common Options:
  -w, --width=<cols>         Width in characters [default: 90]
  -h, --height=<rows>        Height in characters [default: 30]
      --color-scheme=<path>  Local path or url to iTerm .itermcolors scheme file, uses internal scheme by default.
      --font-family=<font>   Choose an OS installed font [default: monospace]
      --font-size=<points>   Specify the font point size [default: 12]

Screenshot Options:
  -p, --play=<path>          Path to script to play in terminal
  -o, --out=<file>           Filename of the image to generate [default: terminal.png]
      --line-delay=<ms>      Delay in ms to wait after each line is sent to the terminal [default: 1000]
      --final-delay=<ms>     Delay in ms to wait before writing the view of the terminal [default: 10000]

Replace membrane.term with your appropriate Clojure tools CLI launch sequence. For example:
|
| clojure -M:membrane.term run-term -w 133 -h 60
|")

(defn- parse-string [v]
  (str v))

(defn- int-parser-validator [min]
  (fn [v]
    (let [num (if (integer? v)
                v
                (when (and (string? v) (re-matches #"\d+" v))
                  (Integer/parseInt v)))]
      (if (and num (>= num min))
        num
        {:error (format "expected positive integer >= %d" min)}))))

(defn- parse-existing-path [v]
  (when v
    (let [p (str v)]
      (try
        (with-open [_rdr (io/reader p)])
        p
        (catch Throwable e
          {:error (format "unable to open path, %s" (ex-message e))})))))

(defn- parse-image-out [v]
  (when v
    (let [p (str v)]
      (if (re-find #"(?i).+\.(png|jpeg|jpg|webp)$" p)
        p
        {:error "supported image formats are png, webp and jpeg (aka jpg)."}))))

(defn- validate-font [args]
  (let [font-family (get args "--font-family")
        font-size (get args "--font-size")]
    (when-not (term/font-valid? font-family font-size)
      {:error (format "font family %s, size %d not found" font-family font-size)})))

(defn- validate-args [args args-def post-validations]
  (let [parsed-args (reduce-kv (fn [m k v]
                                 (let [od (get args-def k)
                                       v ((or od identity) v)]
                                   (if (:error v)
                                     (reduced {:error (format "%s: %s" k (:error v))})
                                     (assoc m k v))))
                               {}
                               args)]
    (if (:error parsed-args)
      parsed-args
      (if-let [validation-error (first (keep #(% parsed-args) post-validations))]
        validation-error
        parsed-args))))

(defn- keywordize [arg-map]
  (into {}
        (map (fn [[k v]]
               [(keyword (string/replace-first k #"^--" "")) v])
             arg-map)))

(defn- undo-line-continuations
  "Docopt does not seem to support line continuations, but I feel they make the usage help readable."
  [usage]
  (string/replace usage #"\\\R" ""))

(defn -main [& args]
  (docopt/docopt (undo-line-continuations docopt-usage) args
                 (fn result-fn [arg-map]
                   (let [arg-map (validate-args arg-map {"--width" (int-parser-validator 1)
                                                         "--height" (int-parser-validator 1)
                                                         "--play" parse-existing-path
                                                         "--color-scheme" parse-existing-path
                                                         "--out" parse-image-out
                                                         "--line-delay" (int-parser-validator 0)
                                                         "--final-delay" (int-parser-validator 0)
                                                         "--font-family" parse-string
                                                         "--font-size" (int-parser-validator 1)}
                                                [validate-font])]
                     (if-let [error (:error arg-map)]
                       (do
                         (println (format "*\n* Error: %s\n*\n" error))
                         (println docopt-usage)
                         (System/exit 1))
                       (let [opts (keywordize arg-map)]
                         (cond
                           (:help opts) (println docopt-usage)
                           (:run-term opts) (term/run-term opts)
                           :else (term/screenshot opts))))))
                 (fn usage-fn [_]
                   (println "*\n* Usage error\n*\n")
                   (println docopt-usage)
                   (System/exit 1)))
  (shutdown-agents))
