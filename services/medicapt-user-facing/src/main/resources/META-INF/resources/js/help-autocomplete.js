(() => {
    const form = document.querySelector('form[action="/help"]');
    const messageEl = document.getElementById('message');
    const inlineEl = document.getElementById('autocomplete-inline');
    if (!form || !messageEl || !inlineEl) return;

    const demoSuggestions = [
        {
            trigger: 'i tried p',
            text: 'I tried paying 5 times with different cards, all failed!'
        },
        {
            trigger: 'when cli',
            text: 'When clicking Confirm Appointment when creating an appointment, I get a crazy 404 error page'
        }
    ];
    const MIN_AUTOCOMPLETE_CHARS = 5;

    let currentSuggestion = null;

    function applySuggestion(text) {
        messageEl.value = text;
        hideInlineSuggestion();
        messageEl.focus();
        messageEl.setSelectionRange(text.length, text.length);
    }

    function hideInlineSuggestion() {
        currentSuggestion = null;
        inlineEl.value = '';
        inlineEl.style.display = 'none';
    }

    function showInlineSuggestion(typed, suggestionText) {
        if (!typed || !suggestionText || suggestionText.length <= typed.length) {
            hideInlineSuggestion();
            return;
        }
        inlineEl.value = suggestionText;
        inlineEl.style.display = 'block';
    }

    function syncInlineScroll() {
        inlineEl.scrollTop = messageEl.scrollTop;
        inlineEl.scrollLeft = messageEl.scrollLeft;
    }

    function updateInlineSuggestion() {
        const typed = messageEl.value || '';
        const typedLower = typed.toLowerCase();
        if (!typedLower || typedLower.length < MIN_AUTOCOMPLETE_CHARS || typed.includes('\n')) {
            hideInlineSuggestion();
            return;
        }

        const match = demoSuggestions.find((item) => item.trigger.startsWith(typedLower));
        if (!match) {
            hideInlineSuggestion();
            return;
        }

        currentSuggestion = match.text;
        showInlineSuggestion(typed, currentSuggestion);
        syncInlineScroll();
    }

    messageEl.addEventListener('input', updateInlineSuggestion);
    messageEl.addEventListener('scroll', syncInlineScroll);
    messageEl.addEventListener('keydown', (e) => {
        if (e.key === 'Tab' && currentSuggestion) {
            e.preventDefault();
            applySuggestion(currentSuggestion);
            return;
        }
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            if (currentSuggestion) {
                applySuggestion(currentSuggestion);
            }
            form.requestSubmit();
        }
    });
})();
