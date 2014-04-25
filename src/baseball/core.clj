(ns baseball.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop)]
            [clojure.core.match :refer (match)]))


;; http://www.variousandsundry.com/cs/blog/2014/02/20/baseball-processes/
;; It’s like playing catch in the backyard. You don’t want to play by yourself. So you tell a friend
;; to stand about 20 paces away from you. Once they’re there, they wave their arms to be sure you can
;; see them. Having seen them, you throw them the ball. They throw it back as a solid line drive 
;; right at your mitt. End of process.
;;
;; Unless, of course, your friend is recursive. And wants another ball…
;;
;; Maybe you want to practice catching pop flies, though. Tell another friend to stand over there
;; somewhere. Call him “La Lob”. Have him let you know where he is. Throw him the ball. He’ll throw it
;; back in a graceful arc. And, assuming you made him to be recursive, he’ll keep standing there,
;; waiting for more, ready to send any balls back from whence they came in a graceful arc.

(defprotocol Player
  (player-name [p])
  (play [p]))

(defprotocol Friend
  (walk [p] [p paces])
  (throw-to [p ball]))

(deftype LazyLobber [ch nm max-cnt partner]
  Player
  (player-name [p] nm)
  (play [p]
    (go-loop
      [cnt max-cnt state :walking]
      (let [[new-cnt new-state]
            (case state
              :walking (let [paces (<! ch)]
                         (println "Walking to position")
                         (Thread/sleep (* 100 paces))
                         (println "Ready to play")
                         (>! partner [:ready p])
                         [cnt :playing])
              :playing (let [msg (<! ch)]
                         (println (str nm " got the ball (" cnt "). Throwing back."))
                         (if-not (pos? (dec cnt))
                           [(dec cnt) :done]
                           (do
                             (>! partner [:ball p "Lazy Lob"])
                             [(dec cnt) state])))
              :done (do
                      (println (str nm " is done playing."))
                      (>! partner [:stop p])
                      (close! ch)
                      [-1 :done]))]
        (when (>= new-cnt 0)
          (recur new-cnt new-state))))
    p)
  Friend
  (walk [p] (walk p 20))
  (walk [p paces] (>!! ch paces))
  (throw-to [p ball] (>!! ch ball)))

(def players (atom []))
(defn start-playing [p]
  (swap! players conj p)
  (throw-to p "Ball"))

(defn stop-playing [p]
  (swap! players #(remove (fn [p1] (= (player-name p) (player-name p1))) %)))

(defn play-with [me &friends]
  (play me)
  (doseq [f &friends]
    (walk (play f))))

(deftype Me [me-ch]
  Player
  (player-name [me] "Geoff")
  (play [me]
    (go-loop
      []
      (let [ball (<! me-ch)]
        ;; (println ball)
        (match [ball]
               [[:ready p]] (start-playing p)
               [[:stop p]] (stop-playing p)
               [[:ball p msg]] (throw-to p msg)
               :else (println "Got some message I don't know how to handle"))

        (if (empty? @players)
          (println "Game over, time to go home.")
          (recur))))))

;player two loop
(defn run []
  (let [me-chan (chan)
        me (Me. me-chan)
        friend1 (LazyLobber. (chan) "Friend1" 30 me-chan)
        friend2 (LazyLobber. (chan) "Friend2" 15 me-chan)
        friend3 (LazyLobber. (chan) "Friend3" 5 me-chan)]

    (play-with me [friend1 friend2 friend3])))

