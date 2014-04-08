(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop timeout alt! put!)]))

(defn make-player
  [action]
  (let [ch (chan)]
    (go-loop []
      (let [msg (<! ch)]
        (action msg)))
    ch))

;player one loop
(def me (make-player (fn [msg] (println msg))))

;player two loop
(def you (make-player #(put! me %)))

(>!! you "test")

