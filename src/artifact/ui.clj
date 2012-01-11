(ns artifact.ui
  "Contains the HTML-y bits of the game."
  (:use [clojure.data.json :only (json-str)]
        [ring.util.response :only (response)]
        compojure.core
        artifact.tuplestore
        artifact.game
        artifact.state
        artifact.error
        artifact.logging)
  (:require [net.cgrand.enlive-html :as html])
  (:refer-clojure :exclude [time]))

;;; Helpers

(defn- request-dump [req]
  [:div {:class "request-dump"}
   [:h4 "Diagnostic info (request dump)"]
   [:p "Request Parameters :"]
   [:table
    [:tr [:th "Name"] [:th "Value"]]
    (map (fn [[k v]] [:tr [:td (str k)] [:td (str v)]]) (:params req))]
   [:p "Raw request map: " (str (:params req))]])

(defn- state-url
  "Retrieve the URL that the client can use to get the state of the
game."
  ([token] (str "/api?token=" token)))

;;; Snippets

(html/defsnippet flash (html/html-resource "html/snippets.html") [:p.flash]
  [message]
  (html/content message))

;;; Endpoints

;; TODO: Deal with the token
(defn game-page [token]
  (apply str (html/emit* (html/html-resource "html/professor-board.html"))))

(comment
  (defn game-page [token]
    (dosync
     (let [player-id (lookup-player @*game* token)
           player-name (lookup-player-name @*game* player-id)]
       (response
        (to-html-str
         [:doctype! "html"]
         [:html
          [:head
           [:title "Artifact (Pre-Alpha)"]
           [:link {:rel "stylesheet" :type "text/css" :href "/styles/game.css"}]
           [:link {:rel "stylesheet" :type "text/css" :href "/styles/sunny/jquery-ui-1.8.13.custom.css"}]

           ;; JQuery and related plugins
           ;; Empty string in script tag is to get the closing tag to
           ;; show up, since the validator complains otherwise.
           [:script {:src "/script/jquery.min-1.5.1.js"} ""]
           [:script {:src "/script/jquery.timers-1.2.js"} ""]
           [:script {:src "/script/jquery-ui-1.8.13.custom.min.js"} ""]

           [:script
            ;; URL for retrieving game state
            [:raw! (str "var gameStateUrl='" (state-url token) "';")]]
           [:script {:src "/script/game.js"} ""]]
          [:body
           [:div {:id "setup-ui"}
            [:button {:id "start-game"
                      :onclick "javascript:startGame()"
                      :disabled "disabled"}
             "Start game!"]
            [:table {:id "joined-players"}
             [:tr [:th "Player"] [:th "State"]] ""]]
           [:div {:id "playing-ui"}
            [:div {:id "playing-tabs"}
             [:ul
              [:li [:a {:href "#ma-board"} "Major Action Board"]]
              [:li [:a {:href "#academy-board"} "Academy Board"]]]
             [:div {:id "ma-board"} ""]
             [:div {:id "academy-board"} ""]]]
           [:textarea {:id "gameState" :readonly "readonly" :rows 20}
            "diagnostic information is displayed here"]]]))))))

(defn index [& messages]
  (let [h1 (html/html-resource "html/index.html")
        h2 (html/at h1 [:div#messages] (fn [_] (mapcat flash messages)))]
    (apply str (html/emit* h2))))

(def ^{:private true} error-messages
  {:artifact.game/cannot-add-more-players "The game is already full."})

(defn join-page [player-name]
  (app-try
   (dosync
    (alter *game* update-game nil [nil "game" "new-player" player-name])
    (let [token (last (query-values @*game* [:any #"player:.*" "token" :any]))]
      {:status 303
       :headers {"Location" (str "/game/" token)}}))
   (app-catch e
              (debug "Error when" player-name "tried to join game:" e)
              (index (get error-messages e "Unrecognized error")))))


