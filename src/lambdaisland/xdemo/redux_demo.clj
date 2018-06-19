(ns lambdaisland.xdemo.redux-demo
  (:require [redux.core :as redux]
            [net.cgrand.xforms :as x]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/post-complete

(transduce identity
           (redux/post-complete
            + (fn [x] {:result x}))
           [1 2 3])
;;=> {:result 6}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/pre-step
;;
;; Kind of like comp, in that it lets you "prefix" a function with some extra
;; initial step, e.g. to get to the right key in a seq of maps.

(transduce identity
           (redux/pre-step + :width)
           [{:width 10} {:width 20}])
;;=> 30

;; Or do some pre-processing

(transduce identity
           (redux/pre-step + inc)
           [1 1 1])
;;=> 6

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/fuse
;;
;; Reducing-function version of xforms/multiplex or xforms/transjuct

(transduce identity
           (redux/fuse {:sum + :product *})
           [1 2 3 4])
;;=> {:sum 10, :product 24}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/facet
;;
;; Apply a reducing function for a number of separate key functions, slicing
;; across your data in multiple ways.

(transduce identity
           (redux/facet + [:height :width])
           [{:height 10, :width 20}
            {:height 7, :width 17}])
;;=> [17 37]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/with-xform
;;
;; Reduce while also applying a transducer before each step. Kind of like
;; pre-step but takes a transducer instead of a function.

(transduce identity
           (redux/with-xform + (map #(java.lang.Integer/parseInt %)))
           ["123" "456"])
;;=> 579

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; redux/juxt

(transduce identity (redux/juxt + *) [1 2 3 4])
;;=> [10 24]

(redux/fuse-matrix)
