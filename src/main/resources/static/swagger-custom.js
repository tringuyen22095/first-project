(function () {
    const HEADER_NAME = 'X-Correlation-Id';
    const STORAGE_KEY = 'swagger-corr-id';

    /* ── Patch window.fetch to inject corrId on every Swagger "Execute" ── */
    const originalFetch = window.fetch;
    window.fetch = function (url, options) {
        options = options || {};
        const corrId = getCorrId();
        if (corrId) {
            options.headers = options.headers || {};
            if (options.headers instanceof Headers) {
                if (!options.headers.has(HEADER_NAME)) {
                    options.headers.set(HEADER_NAME, corrId);
                }
            } else {
                options.headers[HEADER_NAME] = options.headers[HEADER_NAME] || corrId;
            }
        }
        return originalFetch.call(this, url, options);
    };

    function getCorrId() {
        const input = document.getElementById('corr-id-input');
        return input ? input.value.trim() : (localStorage.getItem(STORAGE_KEY) || '');
    }

    /* ── Inject sticky bar after Swagger UI topbar renders ── */
    function injectBar() {
        if (document.getElementById('corr-id-bar')) return;

        const topbar = document.querySelector('.topbar');
        if (!topbar) {
            setTimeout(injectBar, 200);
            return;
        }

        const saved = localStorage.getItem(STORAGE_KEY) || '';

        const bar = document.createElement('div');
        bar.id = 'corr-id-bar';
        bar.style.cssText = [
            'position: sticky',
            'top: 0',
            'z-index: 9999',
            'background: #2d2d2d',
            'padding: 8px 20px',
            'display: flex',
            'align-items: center',
            'gap: 10px',
            'box-shadow: 0 2px 6px rgba(0,0,0,.4)',
            'font-family: sans-serif',
        ].join(';');

        bar.innerHTML = `
            <span style="color:#a8d08d;font-size:13px;font-weight:600;white-space:nowrap;">
                X-Correlation-Id
            </span>
            <input
                id="corr-id-input"
                type="text"
                placeholder="leave blank to auto-generate"
                value="${saved}"
                style="flex:1;max-width:420px;padding:5px 10px;border-radius:4px;
                       border:1px solid #555;background:#1b1b1b;color:#e0e0e0;
                       font-size:13px;outline:none;"
            />
            <button id="corr-id-apply"
                style="padding:5px 14px;border-radius:4px;border:none;
                       background:#89bf04;color:#fff;font-size:13px;
                       cursor:pointer;font-weight:600;">
                Apply
            </button>
            <span id="corr-id-status" style="color:#a8d08d;font-size:12px;"></span>
        `;

        topbar.insertAdjacentElement('afterend', bar);

        document.getElementById('corr-id-apply').addEventListener('click', function () {
            const value = document.getElementById('corr-id-input').value.trim();
            if (value) {
                localStorage.setItem(STORAGE_KEY, value);
            } else {
                localStorage.removeItem(STORAGE_KEY);
            }
            const status = document.getElementById('corr-id-status');
            status.textContent = value ? '✓ Applied' : '✓ Cleared (will auto-generate)';
            setTimeout(() => { status.textContent = ''; }, 2500);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', injectBar);
    } else {
        injectBar();
    }
})();
