(ns layout)


(defn page
  [{:html/keys [head body]}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
    [:title "Sprecha"]
    [:link {:rel "icon" :href "favicon.ico"}]
    ;; Preventing FOUT here (https://web.dev/articles/preload-optional-fonts)
    [:link {:rel "preload" :href "/fonts/Nunito/nunito-v26-cyrillic_latin-500.woff2" :as "font" :type "font/woff2" :crossorigin true}]
    [:link {:rel "preload" :href "/fonts/Nunito/nunito-v26-cyrillic_latin-700.woff2" :as "font" :type "font/woff2" :crossorigin true}]
    [:link {:rel "preload" :href "/fonts/Nunito/nunito-v26-cyrillic_latin-800.woff2" :as "font" :type "font/woff2" :crossorigin true}]
    [:link {:rel "stylesheet" :href "/css/styles.css"}]
    [:link {:rel "manifest" :href "/manifest.json"}]
    [:script {:src "/js/htmx/htmx.min.js"}]
    [:script {:src "/js/htmx/idiomorph-ext.min.js"}]
    [:script {:src "/js/app/shared.js" :defer true}]
    [:script {:src "/js/app/sw-loader.js" :defer true}]
    head]
   [:body
    {:hx-ext "morph"}
    body]])
