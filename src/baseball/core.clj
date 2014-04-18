(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop)]
            [clojure.core.match :refer (match)]))

(defprotocol Player
  (player-name [p])
  (walk [p] [p paces])
  (throw-to [p ball])
  (play [p])
  (done [p]))

(deftype LazyLobber [ch nm max-cnt partner]
  Player
  (player-name [p] nm)
  (walk [p] (walk p 20))
  (walk [p paces]
    (future
      (println "Walking to position")
      (Thread/sleep (* 100 paces))
      (println "Ready to play")
      (>!! partner [:ready p])))
  (throw-to [p ball] (>!! ch ball))
  (done [p]
    (println (str nm " is done playing."))
    (close! ch))
  (play [p]
    (go-loop
      [cnt max-cnt]
      (let [msg (<! ch)]

       (if (pos? cnt)
         (do
           (println (str nm " got the ball (" cnt "). Throwing back."))
           (>! partner [:ball p "Lazy Lob"])
           (recur (dec cnt)))
         (>! partner [:stop p]))))
    p))

(def players (atom []))
(defn start-playing [p]
  (swap! players conj p)
  (throw-to p "Ball"))

(defn stop-playing [p me]
  (swap! players #(remove (fn [p1] (= (player-name p) (player-name p1))) %))
  (done p))

(defn make-me
  []
  (let [me-ch (chan)]
    (go-loop
      []
      (let [ball (<! me-ch)]
        ;; (println ball)
        (match [ball]
               [[:ready p]] (start-playing p)
               [[:stop p]] (stop-playing p me-ch)
               [[:ball p msg]] (throw-to p msg)
               :else (println "Got some message I don't know how to handle"))

        (if (empty? @players)
          (println "Game over, time to go home.")
          (recur))))
    me-ch))

;player two loop
(defn run []
  (let [me (make-me)
        friend1 (LazyLobber. (chan) "Friend1" 30 me)
        friend2 (LazyLobber. (chan) "Friend2" 15 me)
        friend3 (LazyLobber. (chan) "Friend3" 5 me)]

    (doseq [f [friend1 friend2 friend3]]
      (walk (play f)))))

