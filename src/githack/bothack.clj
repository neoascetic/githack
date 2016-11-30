(ns githack.bothack
  (:require [bothack.jta :refer :all]
            [bothack.util :refer :all]
            [bothack.bothack :refer :all]
            [bothack.handlers :refer :all]
            [bothack.delegator :refer :all]))

(def ^:private cfg {:bot "bothack.bots.mainbot" :menubot "bothack.bots.dgl-menu"
                    :interface :telnet :host "localhost" :port 23
                    :dgl-pass "pass" :dgl-game "p"})

(defn- turns-passed [game]
  (- (:turn game) (:initial-turn game (:turn game))))

(defn- write! [bh ch]
  (raw-write (:jta bh) (str esc esc esc esc ch))
  bh)

(defn- save [bh] (write! bh "Syq"))
(defn- quit [bh] (write! bh "#quit\nyq"))

(defn- end-session [bh how]
  (-> bh pause how stop)
  (deliver
    (get-in bh [:config :promise])
    (turns-passed @(:game bh)))
  nil)

(defn- quit-when-looping [bh]
  (let [actions-this-turn (atom 0)]
    (register-handler bh (dec priority-top)
      (reify ActionHandler
        (choose-action [_ game]
          (if (= (:turn game) (:turn (:last-state game)))
            (swap! actions-this-turn inc)
            (reset! actions-this-turn 0))
          (if (< 1000 @actions-this-turn)
            (end-session bh quit)))))))

(defn- quit-when-stuck [bh]
  (register-handler bh (inc priority-bottom)
    (reify ActionHandler
      (choose-action [_ _]
        (end-session bh quit)))))

(defn- save-after [bh n]
  (register-handler bh priority-top
    (reify AboutToChooseActionHandler
      (about-to-choose [_ game]
        (if (>= (turns-passed game) n)
          (end-session bh save))))))

(defn- game-stopped [bh]
  (register-handler bh
    (reify GameStateHandler
      (ended [_] (end-session bh identity))
      (started [_]))))

(defn- initial-turn-marker [bh]
  (register-handler bh priority-top
    (reify AboutToChooseActionHandler
      (about-to-choose [this game]
        (swap! (:game bh) assoc :initial-turn (:turn game))
        (deregister-handler bh this)))))

(defn do-turns!
  "Attempts to make `n` turns in the game for player `name`.
  Returns the number of turns passed, which may be greater than `n`
  (because of sleeps, autotravel, etc)."
  [name n]
  (with-redefs [bothack.bothack/load-config identity]
    (let [turns-num (promise)]
      (-> (new-bh (assoc cfg :dgl-login name :promise turns-num))
          (save-after n)
          game-stopped
          quit-when-stuck
          quit-when-looping
          initial-turn-marker
          start)
      (deref turns-num (+ 10000 (* 5000 n)) 0))))
