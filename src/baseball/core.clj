(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop timeout alt! put!)]))

(def me-ch (chan))
(def you-ch (chan))

;player one loop
(def me (go-loop
          []
          (let [msg (<! me-ch)]
            (println msg))))

;player two loop
(def you (go-loop
          []
          (let [msg (<! you-ch)]
            (put! me-ch msg))))

(>!! you-ch "test")

