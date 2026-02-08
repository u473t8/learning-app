"use strict";

class WordAutocomplete extends HTMLElement {
  constructor() {
    super();
    this._boundOnRootKeydown = this._onRootKeydown.bind(this);
    this._boundOnRootClick = this._onRootClick.bind(this);
    this._boundOnRootInput = this._onRootInput.bind(this);
    this._boundOnSuggestionsUpdated = this._onSuggestionsUpdated.bind(this);

    this._connected = false;
    this._basePlaceholder = "";
    this._placeholderRef = null;

    this._observer = null;
    this._observedList = null;
  }

  connectedCallback() {
    if (this._connected) return;
    this._connected = true;

    if (!this._refreshRefs()) {
      this._connected = false;
      return;
    }

    this.addEventListener('keydown', this._boundOnRootKeydown);
    this.addEventListener('click', this._boundOnRootClick);
    this.addEventListener('input', this._boundOnRootInput);
    this.addEventListener('autocomplete:suggestions-updated', this._boundOnSuggestionsUpdated);

    this._boundOnFormReset = this.reset.bind(this);
    this._form = this.closest('form');
    if (this._form) this._form.addEventListener('reset', this._boundOnFormReset);

    this._ensureObserver();
    this._syncTranslationPlaceholder();
  }

  disconnectedCallback() {
    if (!this._connected) return;
    this._connected = false;

    this.removeEventListener('keydown', this._boundOnRootKeydown);
    this.removeEventListener('click', this._boundOnRootClick);
    this.removeEventListener('input', this._boundOnRootInput);
    this.removeEventListener('autocomplete:suggestions-updated', this._boundOnSuggestionsUpdated);

    if (this._form) {
      this._form.removeEventListener('reset', this._boundOnFormReset);
      this._form = null;
    }

    if (this._observer) {
      this._observer.disconnect();
      this._observer = null;
    }
    this._observedList = null;
  }

  _getItems() {
    if (!this.suggestionsList) return [];
    return this.suggestionsList.querySelectorAll('[data-ac-role="item"]');
  }

  _getTopSuggestion(activeOrExactOnly) {
    if (!this.suggestionsList) return null;
    var active = this.suggestionsList.querySelector('[data-active]');
    var exact = this.suggestionsList.querySelector('[data-exact]');
    if (activeOrExactOnly) return active || exact;
    return active || exact || this._getItems()[0] || null;
  }

  _refreshRefs() {
    this.wordInput = this.querySelector('[data-ac-role="word"]');
    this.translationInput = this.querySelector('[data-ac-role="translation"]');
    this.suggestionsList = this.querySelector('[data-ac-role="list"]');

    if (this.translationInput !== this._placeholderRef) {
      this._placeholderRef = this.translationInput;
      this._basePlaceholder = this.translationInput ? (this.translationInput.getAttribute('placeholder') || '') : '';
    }

    return !!(this.wordInput && this.suggestionsList);
  }

  _ensureObserver() {
    if (!this.suggestionsList) return;

    if (this._observer && this._observedList === this.suggestionsList) {
      return;
    }

    if (this._observer) {
      this._observer.disconnect();
    }

    var self = this;
    this._observer = new MutationObserver(function() {
      self._syncTranslationPlaceholder();
    });
    this._observer.observe(this.suggestionsList, { childList: true });
    this._observedList = this.suggestionsList;
  }

  _restorePlaceholder() {
    if (!this.translationInput) return;
    this.translationInput.placeholder = this._basePlaceholder;
  }

  _syncTranslationPlaceholder() {
    this._refreshRefs();
    this._ensureObserver();
    if (!this.translationInput) return;

    if (this.translationInput.value) {
      this._restorePlaceholder();
      return;
    }

    var item = this._getTopSuggestion(false);
    var hint = item && item.dataset ? (item.dataset.translation || '') : '';
    this.translationInput.placeholder = hint || this._basePlaceholder;
  }

  _clearSuggestions() {
    this._refreshRefs();
    this._ensureObserver();
    if (!this.suggestionsList) return;
    this.suggestionsList.innerHTML = '';
    this._syncTranslationPlaceholder();
  }

  _isWordInput(element) {
    if (!element || !element.getAttribute) return false;
    return element.getAttribute('data-ac-role') === 'word' && element.closest('word-autocomplete') === this;
  }

  _isTranslationInput(element) {
    if (!element || !element.getAttribute) return false;
    return element.getAttribute('data-ac-role') === 'translation' && element.closest('word-autocomplete') === this;
  }

  _onRootInput(event) {
    if (!this._refreshRefs()) return;
    this._ensureObserver();
    if (this._isTranslationInput(event.target)) {
      this._syncTranslationPlaceholder();
    }
  }

  _onRootKeydown(event) {
    if (!this._refreshRefs() || !this._isWordInput(event.target)) return;

    var list = this.suggestionsList;
    var items = this._getItems();
    var hasItems = items.length > 0;
    var key = event.key;

    // Arrow navigation
    if (hasItems && (key === 'ArrowDown' || key === 'ArrowUp')) {
      var active = list.querySelector('[data-active]');
      var idx = active ? Array.prototype.indexOf.call(items, active) : -1;
      idx = key === 'ArrowDown'
        ? Math.min(items.length - 1, idx + 1)
        : Math.max(0, idx - 1);
      if (active) delete active.dataset.active;
      var next = items[idx];
      if (next) {
        next.dataset.active = '';
        next.scrollIntoView({ block: 'nearest' });
      }
      this._syncTranslationPlaceholder();
      event.preventDefault();
      return;
    }

    // Tab selects top suggestion and then moves focus to translation
    if (key === 'Tab' && !event.shiftKey) {
      if (hasItems) {
        var selected = list.querySelector('[data-active]')
          || list.querySelector('[data-exact]')
          || items[0];
        if (selected) {
          this._selectItem(selected);
          event.preventDefault();
        }
      }
      return;
    }

    // Escape clears
    if (hasItems && key === 'Escape') {
      this._clearSuggestions();
      event.preventDefault();
      return;
    }
  }

  _onRootClick(event) {
    if (!this._refreshRefs()) return;
    this._ensureObserver();
    var item = event.target.closest('[data-ac-role="item"]');
    if (!item || item.closest('word-autocomplete') !== this) return;
    if (item) this._selectItem(item);
  }

  _onSuggestionsUpdated() {
    if (!this._refreshRefs()) return;
    this._syncTranslationPlaceholder();
  }

  _selectItem(item) {
    this._refreshRefs();

    var lemma = (item.dataset && item.dataset.lemma) || item.textContent.trim();
    var translation = item.dataset.translation;

    this.wordInput.value = lemma;
    this._clearSuggestions();

    if (this.translationInput && translation) {
      this.translationInput.value = translation;
      this._restorePlaceholder();
    }
    if (this.translationInput) {
      this.translationInput.focus();
    }
  }

  /**
   * Public method to reset the autocomplete state.
   * Clears suggestions list and placeholder hint state.
   */
  reset() {
    this._refreshRefs();
    this._ensureObserver();
    this._clearSuggestions();
    this._syncTranslationPlaceholder();
  }
}

customElements.define('word-autocomplete', WordAutocomplete);
