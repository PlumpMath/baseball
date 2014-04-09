(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop timeout alt! put!)]))

(defn make-player
  [max-cnt action]
  (let [ch (chan)]
    (go-loop
      [cnt max-cnt]
      (when (pos? cnt)
        (let [msg (<! ch)]
          (action ch msg)
          (recur (dec cnt)))))
    ch))

;player one loop
(def me (make-player 10000
                     #(do
                        (println (:ball %2))
                        (put! (:channel %2) (:ball %2)))))

;player two loop
(doseq [x (range 10)]
  (let [ch (make-player 10 #(put! me {:channel %1, :ball %2}))]
    (>!! ch (str "ball" x))))

