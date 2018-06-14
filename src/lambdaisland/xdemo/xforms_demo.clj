(ns lambdaisland.xdemo.xforms-demo
  (:require [net.cgrand.xforms :as x]))

(defn xidentity [rf]
  (fn
    ([] rf)
    ([acc] (rf acc))
    ([acc x] (rf acc x))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/by-key
;;
;; Aggregates values based on a key function.
;;
;; Interestingly by-key has no opinion on what that "aggregate" of values looks
;; like, instead it's just a logical stream of values that are handed over to
;; another transducer.
;;
;; For this transducers that produce a single value, like x/into or x/reduce are
;; useful.

(sequence (x/into []) [1 2 3 4])        ; => ([1 2 3 4])
(sequence (x/reduce +) [1 2 3 4])       ; => (10)

;; Docstring:
;;
;; Returns a transducer which partitions items according to kfn.
;; It applies the transform specified by xform to each partition.
;; Partitions contain the "value part" (as returned by vfn) of each item.
;; The resulting transformed items are wrapped back into a "pair" using the pair function.
;; Default values for kfn, vfn and pair are first, second (or identity if kfn is specified) and vector.

;; ------------------------------------------------------------
;; 1-arity: (x/by-key xform)
;; Uses first and second as key and value function

(into {}
      (x/by-key (x/into []))
      [[:xxx 123]
       [:xxx 345]
       [:yyy 678]])
;;=> {:xxx [123 345], :yyy [678]}

;; This first/second default means that by-key can conveniently transform maps.

(defn map-vals [f m]
  (into {} (x/by-key (map f)) m))

(map-vals inc {:a 1 :b 2})
;;=> {:a 2, :b 3}

;; ------------------------------------------------------------
;; 2-arity: (x/by-key kfn xform)
;; Use the given key function, in this case identity is used as value function

(into {}
      (x/by-key :color (x/into []))
      [{:color :blue, :size 1}, {:color :red, :size 2}, {:color :blue, :size 3}])
;;=> {:blue [{:color :blue, :size 1} {:color :blue, :size 3}],
;;     :red [{:color :red, :size 2}]}

;; ------------------------------------------------------------
;; 3-arity: (x/by-key kfn vfn xform)

(into {}
      (x/by-key :color :size (x/into []))
      [{:color :blue, :size 1}, {:color :red, :size 2}, {:color :blue, :size 3}])
;;=> {:blue [1 3], :red [2]}

;; ------------------------------------------------------------
;; 4-arity: (x/by-key kfn vfn pair xform)
(into []
      (x/by-key :color :size #(array-map :color %1 :total-size %2) (x/reduce +))
      [{:color :blue, :size 1}, {:color :red, :size 2}, {:color :blue, :size 3}])
;;=> [{:color :blue, :total-size 4} {:color :red, :total-size 2}]

;; some more examples of by-key

(defn my-group-by [kfn coll]
  (into {} (x/by-key kfn (x/into [])) coll))

(defn sum-by [kfn coll]
  (into {} (x/by-key kfn (x/reduce +)) coll))

(defn my-frequencies [coll]
  (into {} (x/by-key identity x/count) coll))

;; It's worth noting that by-key is much more a general rollup/aggregate
;; function than its name suggests

(def sales [{:sku "ABC", :qty 3, :item-price 7.00}
            {:sku "XXX", :qty 2, :item-price 12.50}
            {:sku "ABC", :qty 9, :item-price 3.99}])

(into {} (x/by-key :sku #(* (:qty %) (:item-price %)) (x/reduce +)) sales)
;;=> {"ABC" 56.91, "XXX" 25.0}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/into-by-key
;;
;; Shorthand for (comp (x/by-key ,,,) (x/into ,,,))

(sequence (x/into-by-key {} #(mod % 10) (x/into #{})) (range 0 75 3))
;;=> ({0 #{0 60 30},
;;      7 #{27 57},
;;      1 #{21 51},
;;      4 #{24 54},
;;      6 #{36 6 66},
;;      3 #{33 3 63},
;;      2 #{72 12 42},
;;      9 #{69 39 9},
;;      5 #{15 45},
;;      8 #{48 18}})

(sequence (x/into-by-key #{}
                         :color              ; keyfn
                         :size               ; vfn
                         (fn [a b] [a :> b]) ; pair
                         (x/into []))        ; xform
          [{:color :blue, :size 1},
           {:color :blue, :size 2},
           {:color :red, :size 3}])
;;=> (#{[:red :> [3]] [:blue :> [1 2]]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/count

;; Docstring:
;; Count the number of items. Either used directly as a transducer or invoked with two args
;; as a transducing context.

;; ------------------------------------------------------------
;; x/count used as transducer directly

(into [] x/count [1 2 3])
;;=> [3]

(into {}
      (x/by-key :color x/count)
      [{:color :x} {:color :x} {:color :y}])
;;=> {:x 2, :y 1}

;; ------------------------------------------------------------
;; as transducing context : (x/count xform coll)

(x/count (take-while #(< % 100)) (range))
;;=> 100


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/drop-last

(into [] (x/drop-last) [1 2 3])
;;=> [1 2]

(into [] (x/drop-last 2) [1 2 3])
;;=> [1]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/take-last

;; in contrast to drop-last, take-last only has a one-arity version

(into [] (x/take-last 2) [1 2 3])
;;=> [2 3]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/for
;;
;; Similar to clojure.core/for, but when you pass % or _ as the first binding
;; form, then the result is a transducer.

(into []
      (x/for [i %]
        (* i i))
      (range 5))
;;=> [0 1 4 9 16]

;; same thing
(into []
      (x/for [i _]
        (* i i))
      (range 5))
;;=> [0 1 4 9 16]

;; Used standalone the result is an "eduction", a kind of lazy evaluation of the
;; transformation that can be used a java iterable, or a clojure reducable, or
;; as a regular seq.
;;
;; So while the result looks like a sequence, you can use the result as an
;; iterable or in a call to reduce, and avoid creating the intermediate
;; sequence.
(x/for [i (range 10)]
  (inc i))
;;=> (1 2 3 4 5 6 7 8 9 10)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/into
;;
;; This function has all the behavior of clojure.core/into, but adds an extra
;; 1-arity version which returns a transducer.

(sequence (x/into #{}) [1 2 3])
;;=> (#{1 3 2})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/iterator
;;
;; A new transducing context that applies a transducer to a java.util.Iterator

(let [src-it (.iterator (eduction (range 4)))
      it (x/iterator (map inc) src-it)]
  [(.next it) (.next it) (.next it) (.next it)])
;;=> [1 2 3 4]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/keys
;;
;; Iterate over the keys of a map

(into #{} x/keys {:foo 1 :bar 2})
;;=> #{:bar :foo}

(transduce)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/maximum
;; x/mimimum

;; Not much to be said. Note that it takes a Java Comparator, not a predicate,
;; but clojure.core/comparator can solve that for you. You can optionally
;; provide a min-value/max-value, if omitted an empty collection will reduce to
;; nil.
;;
;; There's also min/max which are transducers themselves (like cat, you don't
;; call them, just use them.)

(into [] (x/maximum (comparator <)) (range 20))        ;=> [19]
(into [] (x/maximum (comparator >)) (range 20))        ;=> [0]
(into [] (x/maximum (comparator >)) [])                ;=> [nil]
(into [] (x/maximum (comparator <) Long/MAX_VALUE) []) ; [9223372036854775807]

(into [] x/min [1 2 3 4 5])   ; [5]
(into [] x/max [1 2 3 4 5])   ; [5]

;; Since these are all transducers that returns a single value, they are useful with x/by-key.

(into {} (x/by-key :sku :size x/max) [{:sku "ABC" :size 3} {:sku "ABC" :size 7} {:sku "DEF" :size 2}])
;;=> {"ABC" 7, "DEF" 2}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/multiplex

;; Perform multiple transductions in a single pass. Either specify a vector or a
;; map of xforms to be applied.

(sequence (x/multiplex [x/max x/min (x/reduce +) x/count]) [1 6 3 9 2 5 10])
;;=> (1 10 36 7)

(into {} (x/multiplex {:max x/max
                       :min x/min
                       :sum (x/reduce +)
                       :cnt x/count}) [1 6 3 9 2 5 10])
;;=> {:max 10, :min 1, :sum 36, :cnt 7}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/partition

;; ------------------------------------------------------------
;; The missing zero arity versio from clojure.core/partition

(into [] (x/partition 2) (range 10))
;;=> [[0 1] [2 3] [4 5] [6 7] [8 9]]

;; ------------------------------------------------------------
;; The two arity version has two faces: it can mean you specify a step size, or
;; an xform

;; "sliding window"
(into [] (x/partition 3 1) (range 10))
;;=> [[0 1 2] [1 2 3] [2 3 4] [3 4 5] [4 5 6] [5 6 7] [6 7 8] [7 8 9]]

;; transducer over each partition
(into [] (x/partition 3 (x/reduce +)) (range 10))
;;=> [3 12 21]

;; ------------------------------------------------------------
;; The 3-arity version again can do two things, either you pass it s seq, which
;; is used to "pad" the final partition.

(into [] (x/partition 3 1 (repeat nil)) (range 10))
;;=> [[0 1 2] [1 2 3] [2 3 4] [3 4 5] [4 5 6] [5 6 7] [6 7 8] [7 8 9] [8 9 nil]]

;; Or you pass it an xform
(into []
      (x/partition 2 1 (x/reduce +))
      (range 10))
;;=> [1 3 5 7 9 11 13 15 17]

;; ------------------------------------------------------------
;; Finally the full monty: partition-size, step, padding seq, xform

(into [] (x/partition 4 1 (repeat '_) (x/into #{})) (range 10))
;;=> [#{0 1 3 2} #{1 4 3 2} #{4 3 2 5} #{4 6 3 5} #{7 4 6 5} #{7 6 5 8} #{7 6 9 8} #{7 _ 9 8}]


;; x/reduce
;; x/reductions
;; x/some
;; x/sort
;; x/sort-by
;; x/str
;; x/transjuxt
;; x/unreduced->
;; x/vals
;; x/window
;; x/window-by-time
;; x/without
;; x/wrap
