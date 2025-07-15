(ns debux.core-stubs)

;;; config APIs
(defn set-debug-mode! [_val])
(defn set-source-info-mode! [_val])
(defn set-print-length! [_num])
(defn set-line-bullet! [_bulet])

(defmacro set-ns-blacklist! [_blacklist])
(defmacro set-ns-whitelist! [_whitelist])
(defmacro set-tap-output! [_bool])
(defmacro set-date-time-fn! [_date-time-fn])


;;; debugging APIs
(defmacro dbg [form & _opts] form)
(defmacro dbgn [form & _opts] form)
(defmacro dbgt [form & _opts] form)
(defn dbg-prn [& _args])
(defmacro dbg-last [& args] (last args))

(defmacro with-level [_debug-level & args]
  `(do ~@args))

;;; turn-off versions
(defmacro dbg_ [form & _opts] form)
(defmacro dbgn_ [form & _opts] form)
(defmacro dbgt_ [form & _opts] form)

(defn dbg-prn_ [& args])
(defmacro dbg-last_ [& args] (last args))


;;; tag literals
(defn dbg-tag [form] form)
(defn dbgn-tag [form] form)
(defn dbgt-tag [form] form)


;;; macro registering APIs
(defmacro register-macros! [_macro-type _symbols])

(defmacro show-macros
  ([])
  ([_macro-type]))
