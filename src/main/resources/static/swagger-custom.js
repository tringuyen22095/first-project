(function () {
    const HEADER_NAME = 'X-Correlation-Id';
    const STORAGE_KEY = 'swagger-corr-id';

    /* ── Patch window.fetch to inject corrId on every "Execute" call ── */
    const originalFetch = window.fetch;
    window.fetch = function (url, options) {
        options = options || {};
        const corrId = (document.getElementById('corr-id-input') || {}).value || localStorage.getItem(STORAGE_KEY) || '';
        if (corrId) {
            options.headers = options.headers || {};
            if (options.headers instanceof Headers) {
                if (!options.headers.has(HEADER_NAME)) options.headers.set(HEADER_NAME, corrId);
            } else {
                options.headers[HEADER_NAME] = options.headers[HEADER_NAME] || corrId;
            }
        }
        return originalFetch.call(this, url, options);
    };

    /* ── Build the sticky bar ── */
    function injectBar() {
        if (document.getElementById('corr-id-bar')) return;

        const topbar = document.querySelector('.topbar');
        if (!topbar) return;

        const defaultId = localStorage.getItem(STORAGE_KEY) || crypto.randomUUID();
        localStorage.setItem(STORAGE_KEY, defaultId);

        const bar = document.createElement('div');
        bar.id = 'corr-id-bar';
        bar.style.cssText =
            'position:sticky;top:0;z-index:9999;background:#1b1b1b;' +
            'padding:8px 20px;display:flex;align-items:center;gap:10px;' +
            'box-shadow:0 2px 6px rgba(0,0,0,.5);font-family:sans-serif;';

        bar.innerHTML =
            '<label style="color:#89bf04;font-size:13px;font-weight:700;white-space:nowrap;" for="corr-id-input">' +
                'X-Correlation-Id' +
            '</label>' +
            '<input id="corr-id-input" type="text"' +
                ' placeholder="leave blank to auto-generate on server"' +
                ' value="' + defaultId + '"' +
                ' style="flex:1;max-width:480px;padding:5px 10px;border-radius:4px;' +
                        'border:1px solid #444;background:#2d2d2d;color:#e0e0e0;' +
                        'font-size:13px;outline:none;" />' +
            '<button id="corr-id-save"' +
                ' style="padding:5px 14px;border-radius:4px;border:none;' +
                        'background:#89bf04;color:#fff;font-size:13px;cursor:pointer;font-weight:700;">' +
                'Save' +
            '</button>' +
            '<button id="corr-id-reset"' +
                ' style="padding:5px 14px;border-radius:4px;border:1px solid #555;' +
                        'background:transparent;color:#aaa;font-size:13px;cursor:pointer;">' +
                'New UUID' +
            '</button>' +
            '<span id="corr-id-status" style="color:#89bf04;font-size:12px;min-width:80px;"></span>';

        topbar.insertAdjacentElement('afterend', bar);

        document.getElementById('corr-id-save').addEventListener('click', function () {
            const val = document.getElementById('corr-id-input').value.trim();
            if (val) localStorage.setItem(STORAGE_KEY, val);
            else localStorage.removeItem(STORAGE_KEY);
            flash(val ? '✓ Saved' : '✓ Cleared');
        });

        document.getElementById('corr-id-reset').addEventListener('click', function () {
            const newId = crypto.randomUUID();
            document.getElementById('corr-id-input').value = newId;
            localStorage.setItem(STORAGE_KEY, newId);
            flash('✓ New UUID');
        });

        function flash(msg) {
            const s = document.getElementById('corr-id-status');
            s.textContent = msg;
            setTimeout(function () { s.textContent = ''; }, 2000);
        }
    }

    /* ── Watch for Swagger UI to finish rendering ── */
    const observer = new MutationObserver(function () {
        if (document.querySelector('.topbar')) {
            injectBar();
            if (document.getElementById('corr-id-bar')) observer.disconnect();
        }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });

    /* ── Also try immediately in case the page is already rendered ── */
    injectBar();
})();

