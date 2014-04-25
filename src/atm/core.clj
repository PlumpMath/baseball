(ns atm.core
  (:require [clojure.core.async :refer (chan >!! <!! >! <! close! go go-loop)]))

(def host (chan))
(def from-host (chan))
(def atm (chan))

(defn run
  []
  (go-loop
    [data {}]
    (let [new-txn-data
          (cond 
            (:error data) (do
                            (println "Got an error. No monies for you.")
                            {})
            (:dispense data) (do
                               (println "You've withdrawn: " (:amount data))
                               {})
            (:host-response data) (let [resp (<! from-host)
                                        data (dissoc data :host-response)]
                                    (case resp
                                      :success (assoc data :dispense true)
                                      (assoc data :error true)))
            (:pin data) (let [amount (<! atm)
                              txn (assoc data :amount amount)]
                          (>! host txn)
                          (assoc txn :host-response true))
            (:card data) (let [pin (<! atm)]
                           (assoc data :pin pin))
            (empty? data) (let [card-info (<! atm)]
                            (assoc data :card card-info))
            )]
      (recur new-txn-data)))

  (go-loop
    []
    (let [txn (<! host)]
      (if (= 1111 (:pin txn))
        (>! from-host :success)
        (>! from-host :error))
      (recur)))

  (>!! atm 411111111111)
  (>!! atm 1111)
  (>!! atm 200)

  (>!! atm 411111111111)
  (>!! atm 112)
  (>!! atm 200)
  )
