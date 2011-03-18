(ns artifact.game
  "Implements the logic of the game"
  (:use artifact.triplestore))

(defn- players
  "Given a store, returns a sequence of players in that store."
  [store]
  (or (get-triple-value store "game" "players") []))

(defn- next-player-id
  "Return the next available player id in the specified store."
  [store]
  (let [player-ids (set (get-triple-value store "game" "players"))]
    (first
     (filter 
      #(not (player-ids %))
      (map #(str "player:" %) (iterate inc 1))))))

(defn lookup-token
  "Given a player id and a store, return the player's token."
  [store id]
  (get-triple-value store id "token"))

(defn lookup-player-name
  "Given a player id and a store, return the player's name"
  [store id]
  (get-triple-value store id "name"))

(defn lookup-player
  "Given a token and a store, return the corresponding player id from
  that store."
  [store token]
  (->> (query store ["*" "token" token])
       (first)
       (entity)))

(defn add-player
  "Update the store to include a new player. Return the identity of
  the new player."
  [store name]
  ;; TODO: Barf if we're not in the setup phase
  (let [token (str (rand-int 1000000000))
	id (next-player-id store)]
    (add-triples store
     [id "self" true]
     [id "name" name]
     [id "token" token]
     [id "ready" false]
     [id "available-actions" [id "ready" true]]
     ["game" "players" (conj (players store) id)])
    id))

;;; Visibility

(def ^{:private true :doc "Defines the visibility rules for the game"}
  acl-rules
  [["*"        "*"     :public]
   ["player:*" "*"     :private]
   ["player:*" "ready" :public]
   ["player:*" "name"  :public]])

(defn initialize-game
  "Sets up a game with the data it needs in order to bootstrap."
  [store]
  (add-triples store ["game" "phase" :setup]))

(defn- rule-visibility
  "Given a triple and a rule, return the visibility if the rule
  matches, and nil otherwise."
  [triple rule]
  (let [[espec aspec vis] rule
	pred (build-spec-filter [espec aspec "*"])]
    (when (pred triple) vis)))

(defn- is-public?
  "Returns true if the given triple is public."
  [triple]
  (->> acl-rules
       (map #(rule-visibility triple %))
       (filter identity)		; Remove nils
       (last)
       (= :public)))

(defn get-visible-triples
  "Returns all the triples up to the current moment that are visible.
  Triples are visible either if they are public or if they are visible
  in the session identitifed by token."
  [store token]
  (let [player-id (lookup-player store token)
	owned-entities #{player-id}]
    (->> store
	 (get-all-triples)
	 (filter #(or (owned-entities (entity %))
		      (is-public? %))))))

(defn update-game
  "Updates the state of the game given a set of triples being asserted
  by a given player."
  [store & triples]
  ;; TODO: This results in two moments, because of the two calls to
  ;; add-triples. Not sure that's what we want. Could change it so
  ;; that we have a propose-add-triples that returns what the game
  ;; state would be if the specified triples were added.
  (apply add-triples store triples)
  (if (and (= :setup (value (first (query store ["game" "phase" "*"]))))
	   (every? identity (map value (query store ["player:*" "ready" "*"]))))
    (add-triples store ["game" "phase" :playing])))