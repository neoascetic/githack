(ns githack.github
  (:require [clojure.set :as s :refer [rename-keys]]
            [tentacles.events :as events :refer [performed-events]]))

(defn- res->meta [res rmeta]
  (let [fst (first res)
        id (:id fst)]
    (merge rmeta (meta fst) (when id {:id id}))))

(defn events-seq [name opts]
  (let [exec-request
    (fn exec-request [page]
      (let [res (events/performed-events name (assoc opts :page page))]
        (cond
          (-> res meta :links :next) (lazy-cat res (exec-request (inc page)))
          (= ::tentacles.core/not-modified res) nil
          (seq? res) res
          :else
          (throw (ex-info "Something wrong with the API" {:response res})))))]
      (exec-request 1)))

(defn events-count
  "Takes username and previous request meta information.
  Returns number of events occured and request meta information as a vector."
  [name meta]
  (try
     (let [opts (s/rename-keys meta {:last-modified :if-modified-since})
           events (events-seq name opts)]
      [(count (take-while #(not= (:id meta) (% :id)) events))
       (res->meta events meta)])
  (catch Exception e
    [0 meta])))
