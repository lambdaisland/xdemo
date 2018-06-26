(ns lambdaisland.into-transducers
  (:require [clojure.string :as str]))

reduce

(reduce + [1 2 3])
;; => 6

(reduce + 100 [1 2 3])
;; => 106

(reductions (fn [acc x]
              (+ acc x))
            0
            [1 2 3])
;; => (0 1 3 6)

(reduce (fn [acc x]
          (conj acc (* x x)))
        []
        [1 2 3])
;; => [1 4 9]


(defn my-into [target src]
  (reduce conj target src))

(my-into #{} [1 2 3])
;; => #{1 3 2}

(reduce (fn [acc x]
          (if (> acc 5)
            (reduced acc)
            (+ acc x)))
        [1 2 3 4 5])
;; => 6

(reduced 42)
;; => #<Reduced@1d158391: 42>

(type (reduced 42))
;; => clojure.lang.Reduced

@(reduced 42)
;; => 42

(type (reduced 42))
;; => clojure.lang.Reduced

(unreduced (reduced 42))
;; => 42

(unreduced (reduced 42))
;; => 42

["wulong" "red" "black" "green"]
;;=> ["1. wulong" "2. red" "3. black" "4. green"]

(second
 (reduce (fn [[idx res] s]
           [(inc idx) (conj res (str idx ". " s))])
         [1 []]
         ["wulong" "red" "black" "green"]))
;; => ["1. wulong" "2. red" "3. black" "4. green"]


(defn add-index-prefix
  ([] [1 []])
  ([acc] (second acc))
  ([[idx res] x]
   [(inc idx) (conj res (str idx ". " x))]))

(transduce identity add-index-prefix ["wulong" "red" "black" "green"])
;; => ["1. wulong" "2. red" "3. black" "4. green"]

(transduce identity + [1 2 3 4])
;; => 10

(-)
(transduce identity - [1 2 3 4])

(transduce identity - 0 [1 2 3 4])
;; => 10

(transduce identity (completing - #(str "Result: " %)) 0 [1 2 3 4])
;; => Result: -10

(transduce identity (completing -) 0 [1 2 3 4])
;; => -10


(transduce identity
           (completing
            (fn [[idx res] s]
              [(inc idx) (conj res (str idx ". " s))])
            second)
           [1 []]
           ["wulong" "red" "black" "green"])
;; => ["1. wulong" "2. red" "3. black" "4. green"]


;; Transducer :: rf -> rf
(fn [rf]
  (fn
    ([] (rf))
    ([acc] (rf acc))
    ([acc x] (rf acc x))))

;; A :: rf -> rf
;; B :: rf -> rf
;;=>
;; (comp A B) :: rf -> rf





(require '[net.cgrand.xforms :as x])

(x/count (filter #(= 0 (mod % 3))) (range 1 20))
;; => 6

(x/some (filter #(= 0 (mod % 3))) (range 1 20))
;; => 3

(x/str (comp (map str/upper-case)
             (interpose "-"))
       ["foo" "bar" "baz"])
;; => FOO-BAR-BAZ

(into [] x/str ["foo" "bar" "baz"])
;; => ["foobarbaz"]

(into [] (x/reduce +) [3 5 7])
;; => [15]

(into [] x/count [1 2 3])
;; => [3]

(into [] (x/into #{}) [1 2 3])
;; => [#{1 3 2}]

(into {}
      (x/by-key :color :size (x/reduce +))
      [{:color :blue, :size 1}
       {:color :red, :size 2}
       {:color :blue, :size 3}])
;; => {:blue 4, :red 2}

(require '[redux.core :as redux])


(transduce identity
           (redux/post-complete
            + (fn [x] {:result x}))
           [1 2 3])
;; => {:result 6}

(transduce identity
           (redux/pre-step + :width)
           [{:width 10} {:width 20}])
;; => 30

(transduce identity
           (redux/fuse {:sum + :product *})
           [1 2 3 4])
;; => {:sum 10, :product 24}


(x/transjuxt [x/min x/max (x/reduce +)] [1 10 100])
;; => [1 100 111]














;; Summary

;; step-function (use with clojure.core/reduce)
(fn [acc x]
  ,,,)

;; Reducing function (use with transduce, xforms/reduce)
(fn
  ([] init)
  ([acc] completion)
  ([acc x]
   ...step...))

;; Reducer
(fn [rf]
  (fn
    ([] (rf))
    ([acc] (rf acc))
    ([acc x] (rf acc x) ...)))



(defn button [xs]
  `[:div ~@xs])
