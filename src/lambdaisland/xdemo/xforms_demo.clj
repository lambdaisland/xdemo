(ns lambdaisland.xdemo.xforms-demo
  (:require [net.cgrand.xforms :as x]
            [clojure.string :as str]))

;; *****************************************************************************
;; Single result transducers
;; *****************************************************************************

;; These transducers return a collection containing a single element, they are
;; most useful when combined with other transducers to form the final result.


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/into
;;
;; This function has all the behavior of clojure.core/into, but adds an extra
;; 1-arity version which returns a transducer.

(sequence (x/into #{}) [1 2 3])
;;=> (#{1 3 2})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/reduce
;;
;; Transducer version of clojure.core/reduce.

(sequence (x/reduce +) [1 2 3])
;;=> (6)

;; Note that there's also x/reductions, similar to how clojure.core/reduce has a
;; reductions counterpart.

(sequence (x/reductions +) [1 2 3])
;;=> (0 1 3 6)


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


;; *****************************************************************************
;; New transducing contexts
;; *****************************************************************************

;; xforms adds several new transducing contexts, so besides transduce, sequence,
;; into, eduction, and core.async/channel, you can now also use these.


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/some
;;
;; Similar to clojure.core/some, but instead of taking a regular function, it
;; takes a transducer.

(x/some x/vals (array-map :foo nil, :bar 123, :baz 456))
;;=> 123


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/str
;;
;; Concatenates the result into a string. Can also be used directly as
;; single-result transducer.

(x/str (comp (map str/upper-case)
             (interpose "-"))
       ["foo" "bar" "baz"])
;;=> "FOO-BAR-BAZ"


(x/into [] x/str ["foo" "bar" "baz"])
;;=> ["foobarbaz"]


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/transjuxt
;;
;; Like transduce, but applies several transducers in a single pass, returning a
;; vector or map of results.

(x/transjuxt [x/min x/max (x/reduce +)] [1 10 100])
;;=> [1 100 111]

(x/transjuxt {:min x/min
              :max x/max
              :sum (x/reduce +)}
             [1 10 100])
;;=> {:min 1, :max 100, :sum 111}

;; Can also be used as a single-result transducer

(sequence (x/transjuxt [(x/reduce +) (x/reduce *)]) [1 4 9])
;;=> ([14 36])

;; multiplex is somewhat similar, but only servers as a transducer, and returns
;; not a single result, but transduces to a collection of results. We'll look at
;; multiplex in the next section.

(sequence (x/multiplex [(x/reduce +) (x/reduce *)]) [1 4 9])
;;=> (14 36)
;;notice: a single seq, not a seq containing a vector


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/without
;;
;; Opposite of x/into, remove items from a set/vector/map

;; Can be used on its own as a multi-dissoc
(x/without #{:a :b :c} [:b :c])
;;=> #{:a}

;; or as a transducing context
(x/without #{:a :b :c} (map (comp keyword str))  [\b \c])
;;=> #{:a}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/iterator
;;
;; A new transducing context that applies a transducer to a java.util.Iterator.
;; Not something you'll use often in pure Clojure code, but when you're dealing
;; with Java libraries and want to insert a transducer in a process that uses
;; Java iterators, then this can be very handy indeed.

(let [src-it (.iterator (eduction (range 4)))
      it (x/iterator (map inc) src-it)]
  [(.next it) (.next it) (.next it) (.next it)])
;;=> [1 2 3 4]



;; *****************************************************************************
;; Transducers that form their own transducing context
;; *****************************************************************************

;; xforms introduces several transducers that are their own transducing context,
;; this allows you to declaratively specify nested operations.


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


;; *****************************************************************************
;; Other transducers
;; *****************************************************************************

;; Most of these are transducer versions of functions from clojure.core, they
;; should make it a lot easier to translate non-transducer code to transducers.


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
;; x/keys
;;
;; Iterate over the keys of a map, similar to (map key)

(into #{} x/keys {:foo 1 :bar 2})
;;=> #{:bar :foo}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/vals
;;
;; Iterate over the values of a map, similar to (map val)

(into #{} x/vals {:foo 1 :bar 2})
;;=> #{1 2}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/partition

;; ------------------------------------------------------------
;; The missing zero arity version of clojure.core/partition

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/sort
;;
;; Return a sorted collection. Note that this relies on the zero-arity
;; completion step being called, which is not guaranteed in all transducing
;; contexts. e.g. you can't use this on a core.async channel, since the
;; transducer will simply buffer the input indefinitely without ever outputting
;; a result.

(into [] (x/sort) [3 4 2 1])

;;=> [1 2 3 4]

;; can optionally take a comparator

(into []
      (x/sort (comparator #(< (count %1) (count %2))))
      ["hello" "xx" "baz" "babelonians"])
;;=> ["xx" "baz" "hello" "babelonians"]


;; a sort-by version takes a key function

(into []
      (x/sort-by count)
      ["hello" "xx" "baz" "babelonians"])
;;=> ["xx" "baz" "hello" "babelonians"]


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/wrap
;;
;; Insert an extra element at the front, at the back, and optionally in between
;; items.

(x/str (x/wrap "<div>" "</div>" "<br>") ["hello" "world"])


;; *****************************************************************************
;; Other helper functions
;; *****************************************************************************


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/unreduced->
;;
;; Threads as long as the result is unreduced. May return a
;; clojure.lang.Reduced, so you'll want to call unreduced on the result.

;; regular operations, result is produced as with ->
(x/unreduced-> 10
               (+ 5)
               (* 20))
;;=> 300

;; after the second step the result is marked as "reduced", the last step is
;; skipped, the result is still wrapped in a Reduced.
(x/unreduced-> 10
               (+ 5)
               reduced
               (* 20))
;;=> #reduced[{:status :ready, :val 15} 0x23f80280]


;; Unwrap the result. Use unreduced rather than deref, this way it works both on
;; plain values and values wrapped in a Reduced.
(unreduced
 (x/unreduced-> 10
                (+ 5)
                reduced
                (* 20)))
;;=> 15


;; ******************************************************************************
;; Alpha stuff
;; ******************************************************************************

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/window
;;
;; Compute values based on a sliding window of fixed size over the input, takes
;; a window size n, a function to apply f, and an inverse function invf.
;;
;; This transducer still processes one element at a time, you give it two
;; functions, which both take 2 arguments. One "adds" a new value to the current
;; window, and "takes away" an old value from the window.
;;
;; So assuming we use + for f, and - for invf, then each step is computed as
;; follows:
;;
;; (+ (- old-result value-exiting-window) value-entering-window)

(sequence (x/window 3 + -) (range 10))
;;=> (0 1 3 6 9 12 15 18 21 24)
;;= [(+ 0)
;;   (+ 0 1)
;;   (+ 0 1 2)
;;   (+ (- 3   0)  3)
;;   (+ (- 6   1)  4)
;;   (+ (- 9   2)  5)
;;   (+ (- 12  3)  6) ,,,]
;;         ^   ^   ^
;;        acc out  in

;; For certain statistics this approach will work well, if instead you want access
;; to the complete window in each step, then you can use
;; (x/partition n (x/reduce f)) instead, obviating the need for an inverse function
;; that can "take a step back".

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; x/window-by-time
;;
;; Similar to x/window, this produces a stream of values, each computed based on
;; a sliding window over the input values. The difference is that instead of
;; having a fixed window size (say, 4), the window is based on a time interval,
;; so you could have a window size of 1 second, or 1 hour, or 15 minutes.
;;
;; To make this work you provide an extra timef, a function that returns for a
;; given input value the time in a chosen unit, as a double. The "n" parameter
;; in this case refurs to the amount of windows per time unit, so if your timef
;; returns seconds, and n == 4, then you get 4 windows per second.
;;
;; f and invf are as per x/window


(defn inst->min [i]
  (/ (double (.getTime i)) 3600))

(def values
  [{:ts #inst "2018-06-18T11:14:57.613-00:00" :val 644}
   {:ts #inst "2018-06-18T11:14:55.727-00:00" :val 269}
   {:ts #inst "2018-06-18T11:14:53.699-00:00" :val 877}
   {:ts #inst "2018-06-18T11:14:51.949-00:00" :val 57}
   {:ts #inst "2018-06-18T11:14:50.170-00:00" :val 929}
   {:ts #inst "2018-06-18T11:14:48.426-00:00" :val 254}
   {:ts #inst "2018-06-18T11:14:46.700-00:00" :val 337}
   {:ts #inst "2018-06-18T11:14:44.844-00:00" :val 288}
   {:ts #inst "2018-06-18T11:14:43.105-00:00" :val 616}
   {:ts #inst "2018-06-18T11:14:41.284-00:00" :val 986}
   {:ts #inst "2018-06-18T11:14:39.423-00:00" :val 597}
   {:ts #inst "2018-06-18T11:14:36.490-00:00" :val 267}])

;; couldn't quite get this example to work:

;; (defn avg
;;   ([] [0 0])
;;   ([[_ acc]] acc)
;;   ([[i acc] x]
;;    (prn [i acc x])
;;    [(inc i)
;;                 (/ (+ (* acc i) (:val x)) (inc i))]))

;; (defn avg-invf [[i acc] x]
;;   (prn [:- i acc x])
;;   [(dec i)
;;    (/ (- (* acc i) (:val x)) (inc i))])

;; (into []
;;  (x/window-by-time (comp inst->min :ts) 1 avg avg-invf)
;;  values)

;; -----------------------------------------------------------------------------

;; It is interesting to note that window-by-time does not require an invf, if
;; none is given then it will use an internal queue to hold on to the current
;; window, and apply a (reduce f) on each.

;; (into []
;;       (x/window-by-time (comp inst->min :ts) 1 (fn
;;                                                  ([] 0)
;;                                                  ([acc] acc)
;;                                                  ([acc x] (+ acc (:val x)))))
;;       values)

;; This unfortunately still spins indefinitely
