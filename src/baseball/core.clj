(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop timeout alt! put!)]))
(declare you)

(defn make-player
  [max-cnt action]
  (let [ch (chan)]
    (go-loop
      [cnt max-cnt]
      (when (pos? cnt)
        (let [msg (<! ch)]
          (action msg)
          (recur (dec cnt)))))
    ch))

;player one loop
(def me (make-player 10
                     #(do
                        (println %)
                        (put! you %))))

;player two loop
(def you (make-player 10 #(put! me %)))

(>!! you "test")

