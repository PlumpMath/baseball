(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop timeout alt! put!)]))

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
(def me (make-player 10000
                     #(do
                        (println (:ball %))
                        (put! (:channel %) (:ball %)))))

;player two loop
(def player1 (make-player 10 #(put! me {:channel player1, :ball %})))
(def la-lob (make-player 10 #(put! me {:channel la-lob, :ball %})))
(def grounder (make-player 10 #(put! me {:channel grounder, :ball %})))

(>!! player1 "ball1")
(>!! la-lob "ball2")
(>!! grounder "ball3")

