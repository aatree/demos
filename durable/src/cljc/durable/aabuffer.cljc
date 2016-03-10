(ns durable.aabuffer
  (:require [octet.core :as buf]
            [octet.spec :as spec])
  (:refer-clojure :exclude [-reset!])
  #?(:clj (:import (java.nio CharBuffer ByteBuffer))))

(defn data-size [spec data]
  (if (satisfies? spec/ISpecDynamicSize spec)
    (spec/size* spec data)
    (spec/size spec)))

(defprotocol aa-buffer
  (-array [this])
  (-array-offset [this])
  (-capacity [this])
  (-clear! [this])
  (-duplicate [this])
  (-flip! [this])
  (-limit [this])
  (-limit! [this nl])
  (-mark! [this])
  (-position [this])
  (-position! [this np])
  (-read! [this spec])
  (-read-at [this spec offset])
  (-remaining [this])
  (-remaining? [this])
  (-reset! [this])
  (-rewind! [this])
  (-write!
    [this data spec])
  (-write-at!
    [this data spec offset]))

(declare ->aabuf)

#?(:clj (extend-type ByteBuffer aa-buffer
          (-array [this] (.array this))
          (-array-offset [this] (.arrayOffset this))
          (-capacity [this] (.capacity this))
          (-duplicate [this] (.duplicate this))
          (-position [this] (.position this))
          (-position! [this np] (.position this np))
          (-limit [this] (.limit this))
          (-limit! [this nl] (.limit this nl))
          (-clear! [this] (.clear this))
          (-flip! [this] (.flip this))
          (-rewind! [this] (.rewind this))
          (-remaining [this] (.remaining this))
          (-remaining? [this] (.hasRemaining this))
          (-mark! [this] (.mark this))
          (-reset! [this] (.reset this))
          (-write!
            [this data spec]
            (let [p (.position this)
                  dl (spec/write spec this p data)]
              (.position this (+ p dl))
              dl))
          (-write-at!
            [this data spec offset] (spec/write spec this offset data))
          (-read!
            [this spec]
            (let [p (.position this)
                  [dl data] (spec/read spec this p)]
              (.position this (+ p dl))
              data))
          (-read-at
            [this spec offset] (spec/read spec this offset)))
   :cljs (deftype aabuf  [^{:volatile-mutable true} m
                          ^{:volatile-mutable true} p
                          ^{:volatile-mutable true} l
                          o c ro b] aa-buffer
           (-array [this] b)
           (-array-offset [this] o)
           (-capacity [this] (aget b "byteLength"))
           (-position [this] p)
           (-position! [this np]
             (if (or (< np 0) (> np l))
               (throw "invalid position")
               (do
                 (set! p np)
                 (if (< p m)
                   (set! m -1))))
             b)
           (-limit [this] l)
           (-limit! [this nl]
             (if (> nl c)
               (throw "invalid limit")
               (do
                 (set! l nl)
                 (if (< l p)
                   (-position! this l))))
             b)
           (-clear! [this] (set! m -1) (set! p 0) (set! l c) b)
           (-duplicate [this] (->aabuf m p l o c ro b))
           (-flip! [this] (set! l p) (set! p 0) b)
           (-rewind! [this] (set! p 0) b)
           (-remaining [this] (- l p))
           (-remaining? [this] (< p l))
           (-mark! [this] (set! m p))
           (-reset! [this]
             (if (= m -1)
               (throw "invalid mark")
               (set! p m)))
           (-write!
             [this data spec]
             (if ro (throw "read only"))
             (let [dl (data-size spec data)
                   np (+ dl p)]
               (if (> np l)
                 (throw "remaining space is too small"))
               (spec/write spec b (+ p o) data)
               (set! p np)
               dl))
           (-write-at!
             [this data spec offset]
             (if ro (throw "read only"))
             (let [dl (data-size spec data)
                   np (+ offset dl)]
               (if (> np l)
                 (throw "possible buffer corruption by writing past limit"))
               (spec/write spec b (+ offset o) data)
               dl))
           (-read!
             [this spec]
             (let [[dl data] (spec/read spec b (+ p o))]
               (set! p (+ p dl))
               (when (> p l)
                 (set! p (- p dl))
                 (throw "remaining data is too small"))
               data))
           (-read-at
             [this spec offset]
             (let [[dl data] (spec/read spec b (+ offset o))
                   np (+ offset dl)]
               (if (> np l)
                 (throw "read past limit"))
               data))))

(defn newBuffer [size]
#?(:clj (buf/allocate size)
   :cljs (->aabuf -1 0 size 0 size false (buf/allocate size))))
