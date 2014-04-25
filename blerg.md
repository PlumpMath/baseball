# Using A Core.Async Routine As A State Machine

Clojure has a library called Core.Async that it allows you to model your system as a set of
independent processes that pass messages to each other through channels. Each process is responsible
for running itself, managing its own state, etc. But how do you manage state in a language where
everything is immutable? Well there are many ways to do that using Atoms, Refs, and Agents. But
those constructs are mostly about sharing mutable state between processes, and in many cases end up
being more complex than you would need to implement state machine that entirely encapsulated its own
state transitions. Fortunately there's another way.

The go block is the basis of each of the independent processes. These blocks define a unit of
asyncrhonous work. The go block is generally implemented in a loop/recur fashion where a message is
received from a channel, processed, and then the recur is called to loop back and wait for another
message to be received. This looping gives the go block an opportunity to transform itself into a
new state after each message is processed. 

## A Simple Exmple

A simple state machine might only have a couple of states and transform from one to the next.
![Simple State Machine](simple.png)

Below you can see a go block that implements those three states: starting, playing, and done. It
does so by starting in the ":starting" state and then transforming to ":playing" state when it
recurs in the loop. The ":playing" state is run over and over until "some-condition" is met that
makes it go to ":done".


    (go-loop
       [state :starting]
       (let [[new-state]
            (case state
                  :starting (let [init-value (<! ch)]
                             (some-work init-value)
                             :playing)
                  :playing (let [round (<! ch)]
                             (if some-condition
                               :done
                               (do
                                 (some-other-work round)
                                 state)))
                  :done (do
                          (maybe-do-some-cleanup)
                          (close! ch)
                          :done))]
           (if-not (= :done new-state)
              (recur new-state))))


## Using Data to Determine State Transitions

A slightly more complex state machine might have multiple states to step through to process a series
of messages. Think of an ATM as an example (a really simple ATM that is).
![ATM State Machine](atm.png)

The ATM has multiple user interactions and must communicate with other services to actually
authorize the transaction. When the ATM is done with a given user, it goes back into a waiting state
until someone swipes their card again.

Instead of passing an explicit state through the loop, our implementation passes data around. This
accumlator pattern allows for the state machine to collect data at each step and then to pass that
data on to later states. The data itself is used to determine what state the state machine is in. If
there's card information, but then the process expects a pin, if there's a pin it expects an amount,
etc. Once it's accumulated the data it can process the transaction. It then transitions back to the
waiting state by setting the transaction data back to empty so that the next iteration puts it back
in the waiting state.

    (def host (chan))
    (def from-host (chan))
    (def atm (chan))
    
    (defn run
      []

      ;; The State machine for the ATM
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
    
      ;; Simulator for the Host that the ATM talks to
      (go-loop
        []
        (let [txn (<! host)]
          (if (= 1111 (:pin txn))
            (>! from-host :success)
            (>! from-host :error))
          (recur)))
    
      ;; Simulated hardware events from the ATM machine
      (>!! atm 411111111111)
      (>!! atm 1111)
      (>!! atm 200)
    
      (>!! atm 411111111111)
      (>!! atm 112)
      (>!! atm 200)
    )

Each of those states is a fairly simple process, but you can easily layer on more rules to implement
more complex state transitions.

