(function () {
    if (window.MTMSFlash) return;

    var doc = document;
    var storageKey = 'mtms.flash.config.v1';
    var rootId = 'mtms-flash-root';
    var initialId = 'mtms-flash-initial';
    var confirmModalId = 'mtmsConfirmModal';

    var defaultConfig = {
        position: 'top-right',
        maxVisible: 3,
        newestOnTop: true,
        maxQueue: 50,
        defaultTimeoutMs: 4000,
        dedupeWindowMs: 2000,
        persistConfig: true,
        types: {
            success: { timeoutMs: 3200, icon: 'bi-check-circle-fill', title: 'Success' },
            error: { timeoutMs: 7000, icon: 'bi-exclamation-triangle-fill', title: 'Error' },
            warning: { timeoutMs: 5200, icon: 'bi-exclamation-diamond-fill', title: 'Warning' },
            info: { timeoutMs: 4200, icon: 'bi-info-circle-fill', title: 'Info' }
        }
    };

    function clampInt(v, min, max, fallback) {
        var n = parseInt(v, 10);
        if (Number.isNaN(n)) return fallback;
        if (n < min) return min;
        if (n > max) return max;
        return n;
    }

    function normalizeType(type) {
        var t = (type || '').toString().trim().toLowerCase();
        if (t === 'danger') t = 'error';
        if (t === 'warn') t = 'warning';
        if (t === 'information') t = 'info';
        if (!defaultConfig.types[t]) return 'info';
        return t;
    }

    function normalizePosition(pos) {
        var p = (pos || '').toString().trim().toLowerCase();
        p = p.replace('_', '-');
        var allowed = {
            'top-right': 1,
            'top-left': 1,
            'bottom-right': 1,
            'bottom-left': 1,
            'top-center': 1,
            'bottom-center': 1,
            'center': 1
        };
        if (allowed[p]) return p;
        return defaultConfig.position;
    }

    function safeJsonParse(s) {
        try {
            return JSON.parse(s);
        } catch (e) {
            return null;
        }
    }

    function shallowMerge(a, b) {
        var out = {};
        Object.keys(a || {}).forEach(function (k) { out[k] = a[k]; });
        Object.keys(b || {}).forEach(function (k) { out[k] = b[k]; });
        return out;
    }

    var state = {
        seq: 1,
        config: shallowMerge(defaultConfig, {}),
        buckets: new Map(),
        recent: new Map(),
        fetchInstalled: false,
        confirmInstalled: false,
        booted: false
    };

    function loadStoredConfig() {
        if (!defaultConfig.persistConfig) return null;
        try {
            var raw = localStorage.getItem(storageKey);
            if (!raw) return null;
            return safeJsonParse(raw);
        } catch (e) {
            return null;
        }
    }

    function storeConfig(cfg) {
        if (!cfg.persistConfig) return;
        try {
            localStorage.setItem(storageKey, JSON.stringify({
                position: cfg.position,
                maxVisible: cfg.maxVisible,
                newestOnTop: cfg.newestOnTop,
                maxQueue: cfg.maxQueue,
                defaultTimeoutMs: cfg.defaultTimeoutMs
            }));
        } catch (e) {
        }
    }

    function ensureRoot() {
        var root = doc.getElementById(rootId);
        if (!root) {
            root = doc.createElement('div');
            root.id = rootId;
            doc.body.appendChild(root);
        }
        if (!root.classList.contains('mtms-flash-root')) root.classList.add('mtms-flash-root');
        return root;
    }

    function ensureStack(position) {
        var root = ensureRoot();
        var pos = normalizePosition(position);
        var existing = root.querySelector('.mtms-flash-stack[data-position="' + pos + '"]');
        if (existing) return existing;
        var el = doc.createElement('div');
        el.className = 'mtms-flash-stack mtms-flash-pos-' + pos;
        el.setAttribute('data-position', pos);
        root.appendChild(el);
        return el;
    }

    function getBucket(position) {
        var pos = normalizePosition(position);
        var bucket = state.buckets.get(pos);
        if (bucket) return bucket;
        bucket = {
            position: pos,
            stackEl: null,
            visible: [],
            queue: []
        };
        state.buckets.set(pos, bucket);
        return bucket;
    }

    function iconClassFor(type) {
        var t = normalizeType(type);
        var meta = state.config.types[t] || defaultConfig.types.info;
        return meta.icon || defaultConfig.types.info.icon;
    }

    function titleFor(type) {
        var t = normalizeType(type);
        var meta = state.config.types[t] || defaultConfig.types.info;
        return meta.title || defaultConfig.types.info.title;
    }

    function buildToastEl(msg) {
        var type = normalizeType(msg.type);

        var el = doc.createElement('div');
        el.className = 'mtms-flash mtms-flash--' + type;
        el.setAttribute('tabindex', '0');
        el.setAttribute('data-flash-id', String(msg.id));
        el.setAttribute('data-flash-pos', msg.position);
        el.setAttribute('aria-atomic', 'true');
        el.setAttribute('role', (type === 'error' || type === 'warning') ? 'alert' : 'status');

        var icon = doc.createElement('div');
        icon.className = 'mtms-flash__icon';
        var iconI = doc.createElement('i');
        iconI.className = 'bi ' + iconClassFor(type);
        icon.appendChild(iconI);

        var body = doc.createElement('div');
        body.className = 'mtms-flash__body';

        var title = doc.createElement('div');
        title.className = 'mtms-flash__title';
        title.textContent = msg.title || titleFor(type);

        var message = doc.createElement('div');
        message.className = 'mtms-flash__message';
        message.textContent = msg.message || '';

        body.appendChild(title);
        body.appendChild(message);

        var actions = doc.createElement('div');
        actions.className = 'mtms-flash__actions';
        var close = doc.createElement('button');
        close.type = 'button';
        close.className = 'mtms-flash__close';
        close.setAttribute('aria-label', 'Dismiss notification');
        var closeI = doc.createElement('i');
        closeI.className = 'bi bi-x-lg';
        close.appendChild(closeI);
        actions.appendChild(close);

        el.appendChild(icon);
        el.appendChild(body);
        el.appendChild(actions);

        close.addEventListener('click', function () {
            dismiss(msg.position, msg.id, 'manual');
        });

        el.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                e.preventDefault();
                dismiss(msg.position, msg.id, 'escape');
            }
        });

        return el;
    }

    function applyInAnimation(el) {
        requestAnimationFrame(function () {
            el.classList.add('mtms-flash--in');
        });
    }

    function applyOutAnimation(el, done) {
        var finished = false;
        var finish = function () {
            if (finished) return;
            finished = true;
            el.removeEventListener('transitionend', onEnd);
            done();
        };
        var onEnd = function (e) {
            if (e && e.target !== el) return;
            finish();
        };
        el.addEventListener('transitionend', onEnd);
        el.classList.remove('mtms-flash--in');
        el.classList.add('mtms-flash--out');
        setTimeout(finish, 340);
    }

    function nowMs() {
        return Date.now ? Date.now() : new Date().getTime();
    }

    function shouldDedupe(key) {
        if (!key) return false;
        var w = clampInt(state.config.dedupeWindowMs, 0, 60000, defaultConfig.dedupeWindowMs);
        var t = nowMs();
        var last = state.recent.get(key);
        state.recent.set(key, t);
        if (last && (t - last) <= w) return true;
        if (state.recent.size > 200) {
            var cutoff = t - w * 2;
            state.recent.forEach(function (v, k) {
                if (v < cutoff) state.recent.delete(k);
            });
        }
        return false;
    }

    function computeTimeout(type, overrideMs) {
        if (overrideMs === 0) return 0;
        if (typeof overrideMs === 'number' && overrideMs > 0) return overrideMs;
        var t = normalizeType(type);
        var per = state.config.types[t] && typeof state.config.types[t].timeoutMs === 'number'
            ? state.config.types[t].timeoutMs
            : null;
        if (typeof per === 'number' && per >= 0) return per;
        return clampInt(state.config.defaultTimeoutMs, 0, 600000, defaultConfig.defaultTimeoutMs);
    }

    function ensureBucketDom(bucket) {
        if (!bucket.stackEl) bucket.stackEl = ensureStack(bucket.position);
        return bucket.stackEl;
    }

    function enforceMaxVisible(bucket) {
        var max = clampInt(state.config.maxVisible, 1, 8, defaultConfig.maxVisible);
        while (bucket.visible.length > max) {
            var victim = bucket.visible[bucket.visible.length - 1];
            dismiss(bucket.position, victim.id, 'overflow');
        }
    }

    function showMessage(bucket, msg) {
        ensureBucketDom(bucket);
        var el = buildToastEl(msg);
        msg.el = el;

        if (state.config.newestOnTop) {
            bucket.stackEl.insertBefore(el, bucket.stackEl.firstChild);
            bucket.visible.unshift(msg);
        } else {
            bucket.stackEl.appendChild(el);
            bucket.visible.push(msg);
        }

        applyInAnimation(el);
        enforceMaxVisible(bucket);

        var timeout = computeTimeout(msg.type, msg.timeoutMs);
        if (timeout > 0) {
            msg.timer = setTimeout(function () {
                dismiss(bucket.position, msg.id, 'timeout');
            }, timeout);
        }
    }

    function drainQueue(bucket) {
        var max = clampInt(state.config.maxVisible, 1, 8, defaultConfig.maxVisible);
        while (bucket.queue.length > 0 && bucket.visible.length < max) {
            var next = state.config.newestOnTop ? bucket.queue.pop() : bucket.queue.shift();
            if (!next) break;
            showMessage(bucket, next);
        }
    }

    function dismiss(position, id, reason) {
        var pos = normalizePosition(position);
        var bucket = getBucket(pos);
        var idx = -1;
        for (var i = 0; i < bucket.visible.length; i++) {
            if (bucket.visible[i].id === id) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        var msg = bucket.visible[idx];
        bucket.visible.splice(idx, 1);
        if (msg.timer) clearTimeout(msg.timer);
        if (msg.el) {
            var el = msg.el;
            applyOutAnimation(el, function () {
                if (el && el.parentNode) el.parentNode.removeChild(el);
                drainQueue(bucket);
                window.dispatchEvent(new CustomEvent('mtms:flashDismiss', { detail: { id: id, position: pos, reason: reason || null } }));
            });
        } else {
            drainQueue(bucket);
        }
        return true;
    }

    function enqueue(bucket, msg) {
        var maxQueue = clampInt(state.config.maxQueue, 0, 500, defaultConfig.maxQueue);
        if (maxQueue === 0) return;
        if (bucket.queue.length >= maxQueue) {
            if (state.config.newestOnTop) bucket.queue.shift();
            else bucket.queue.pop();
        }
        bucket.queue.push(msg);
    }

    function push(input) {
        var msgObj = input || {};
        var type = normalizeType(msgObj.type);
        var position = normalizePosition(msgObj.position || state.config.position);
        var message = (msgObj.message || msgObj.text || '').toString();
        var title = (msgObj.title || '').toString();
        var dedupeKey = (msgObj.dedupeKey || '').toString();

        if (!message) return null;
        if (dedupeKey && shouldDedupe(dedupeKey)) return null;

        var msg = {
            id: state.seq++,
            type: type,
            title: title,
            message: message,
            timeoutMs: (typeof msgObj.timeoutMs === 'number') ? msgObj.timeoutMs : null,
            position: position,
            createdAt: nowMs(),
            el: null,
            timer: null
        };

        var bucket = getBucket(position);
        ensureBucketDom(bucket);

        var max = clampInt(state.config.maxVisible, 1, 8, defaultConfig.maxVisible);
        if (bucket.visible.length < max) showMessage(bucket, msg);
        else enqueue(bucket, msg);

        window.dispatchEvent(new CustomEvent('mtms:flashPush', { detail: { id: msg.id, type: msg.type, position: msg.position } }));
        return msg.id;
    }

    function configure(opts) {
        var o = opts || {};
        if (typeof o.position === 'string') state.config.position = normalizePosition(o.position);
        if (typeof o.maxVisible !== 'undefined') state.config.maxVisible = clampInt(o.maxVisible, 1, 8, state.config.maxVisible);
        if (typeof o.newestOnTop === 'boolean') state.config.newestOnTop = o.newestOnTop;
        if (typeof o.maxQueue !== 'undefined') state.config.maxQueue = clampInt(o.maxQueue, 0, 500, state.config.maxQueue);
        if (typeof o.defaultTimeoutMs !== 'undefined') state.config.defaultTimeoutMs = clampInt(o.defaultTimeoutMs, 0, 600000, state.config.defaultTimeoutMs);
        if (typeof o.dedupeWindowMs !== 'undefined') state.config.dedupeWindowMs = clampInt(o.dedupeWindowMs, 0, 60000, state.config.dedupeWindowMs);
        if (typeof o.persistConfig === 'boolean') state.config.persistConfig = o.persistConfig;

        if (o.types && typeof o.types === 'object') {
            var nextTypes = shallowMerge(state.config.types, {});
            Object.keys(o.types).forEach(function (k) {
                var t = normalizeType(k);
                nextTypes[t] = shallowMerge(nextTypes[t] || {}, o.types[k] || {});
            });
            state.config.types = nextTypes;
        }

        state.buckets.forEach(function (bucket) {
            if (bucket.stackEl) {
                bucket.stackEl.className = 'mtms-flash-stack mtms-flash-pos-' + bucket.position;
            }
            enforceMaxVisible(bucket);
            drainQueue(bucket);
        });

        storeConfig(state.config);
        return shallowMerge(state.config, {});
    }

    function getConfig() {
        return shallowMerge(state.config, {});
    }

    function parseErrorMessage(body, res) {
        if (!body) return null;
        if (typeof body === 'string') return body;
        if (typeof body === 'object') {
            if (typeof body.error === 'string' && body.error) return body.error;
            if (typeof body.message === 'string' && body.message) return body.message;
            if (body.errors) {
                if (Array.isArray(body.errors)) {
                    var s = body.errors.filter(Boolean).slice(0, 3).join(' ');
                    if (s) return s;
                }
                if (typeof body.errors === 'object') {
                    var parts = [];
                    Object.keys(body.errors).slice(0, 3).forEach(function (k) {
                        var v = body.errors[k];
                        if (typeof v === 'string' && v) parts.push(v);
                        else if (Array.isArray(v) && v.length) parts.push(v[0]);
                    });
                    if (parts.length) return parts.join(' ');
                }
            }
        }
        if (res && res.status) return 'Request failed (HTTP ' + res.status + ').';
        return null;
    }

    function installFetchInterceptor() {
        if (state.fetchInstalled) return;
        if (typeof window.fetch !== 'function') return;
        var nativeFetch = window.fetch.bind(window);
        state.fetchInstalled = true;

        window.fetch = function (input, init) {
            var cfg = init && init.mtms ? init.mtms : null;
            var silent = cfg && cfg.silent === true;
            var dedupeKey = cfg && typeof cfg.dedupeKey === 'string' ? cfg.dedupeKey : null;

            return nativeFetch(input, init).then(function (res) {
                if (silent) return res;
                if (!res || res.ok) return res;
                var url = '';
                try {
                    url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                } catch (e) {
                    url = '';
                }
                return res.clone().json().catch(function () { return null; }).then(function (body) {
                    var msg = parseErrorMessage(body, res);
                    if (!msg) msg = (res.status === 401) ? 'Please login to continue.' : 'Request failed (HTTP ' + res.status + ').';
                    var key = dedupeKey || ('http:' + res.status + ':' + url + ':' + msg);
                    push({ type: (res.status >= 500) ? 'error' : 'warning', message: msg, dedupeKey: key });
                    return res;
                });
            }).catch(function (err) {
                if (!silent) {
                    var msg = (err && err.message) ? err.message : 'Network error. Please try again.';
                    var key = dedupeKey || ('net:' + msg);
                    push({ type: 'error', message: msg, dedupeKey: key });
                }
                throw err;
            });
        };
    }

    function getConfirmModal() {
        var el = doc.getElementById(confirmModalId);
        if (!el) return null;
        if (typeof bootstrap === 'undefined' || !bootstrap.Modal) return null;
        return { el: el, modal: bootstrap.Modal.getOrCreateInstance(el) };
    }

    function confirm(opts) {
        var o = opts || {};
        var title = (o.title || 'Confirm').toString();
        var message = (o.message || '').toString();
        var confirmText = (o.confirmText || 'Confirm').toString();
        var cancelText = (o.cancelText || 'Cancel').toString();
        var danger = o.danger === true;

        if (!message) return Promise.resolve(false);
        var bundle = getConfirmModal();
        if (!bundle) return Promise.resolve(window.confirm(message));

        var el = bundle.el;
        var modal = bundle.modal;

        var titleEl = el.querySelector('[data-role="title"]');
        var bodyEl = el.querySelector('[data-role="message"]');
        var confirmBtn = el.querySelector('[data-role="confirm"]');
        var cancelBtn = el.querySelector('[data-role="cancel"]');

        if (titleEl) titleEl.textContent = title;
        if (bodyEl) bodyEl.textContent = message;
        if (confirmBtn) confirmBtn.textContent = confirmText;
        if (cancelBtn) cancelBtn.textContent = cancelText;

        if (confirmBtn) {
            confirmBtn.classList.remove('btn-primary', 'btn-danger');
            confirmBtn.classList.add(danger ? 'btn-danger' : 'btn-primary');
        }

        return new Promise(function (resolve) {
            var lastActive = doc.activeElement;
            var resolved = false;

            var cleanup = function (value) {
                if (resolved) return;
                resolved = true;
                el.removeEventListener('hidden.bs.modal', onHidden);
                if (confirmBtn) confirmBtn.removeEventListener('click', onConfirm);
                if (cancelBtn) cancelBtn.removeEventListener('click', onCancel);
                el.removeEventListener('keydown', onKeydown);
                if (lastActive && lastActive.focus) lastActive.focus();
                resolve(value);
            };

            var onConfirm = function () {
                modal.hide();
                cleanup(true);
            };
            var onCancel = function () {
                modal.hide();
                cleanup(false);
            };
            var onHidden = function () {
                cleanup(false);
            };
            var onKeydown = function (e) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    modal.hide();
                    cleanup(false);
                }
            };

            if (confirmBtn) confirmBtn.addEventListener('click', onConfirm);
            if (cancelBtn) cancelBtn.addEventListener('click', onCancel);
            el.addEventListener('hidden.bs.modal', onHidden, { once: true });
            el.addEventListener('keydown', onKeydown);

            modal.show();
            setTimeout(function () {
                if (confirmBtn && confirmBtn.focus) confirmBtn.focus();
            }, 60);
        });
    }

    function installConfirmDelegation() {
        if (state.confirmInstalled) return;
        state.confirmInstalled = true;

        doc.addEventListener('click', function (e) {
            var el = e.target && e.target.closest ? e.target.closest('[data-confirm]') : null;
            if (!el) return;
            if (el.getAttribute('data-confirmed') === 'true') return;
            var message = el.getAttribute('data-confirm') || '';
            if (!message) return;
            var title = el.getAttribute('data-confirm-title') || 'Confirm';
            var confirmText = el.getAttribute('data-confirm-ok') || 'Confirm';
            var cancelText = el.getAttribute('data-confirm-cancel') || 'Cancel';
            var danger = (el.getAttribute('data-confirm-danger') || '').toLowerCase() === 'true';

            e.preventDefault();
            e.stopPropagation();

            confirm({ title: title, message: message, confirmText: confirmText, cancelText: cancelText, danger: danger }).then(function (ok) {
                if (!ok) return;
                el.setAttribute('data-confirmed', 'true');
                if (el.tagName === 'A' && el.href) {
                    window.location.href = el.href;
                    return;
                }
                if (el.tagName === 'BUTTON') {
                    var form = el.form || (el.closest ? el.closest('form') : null);
                    if (form) form.requestSubmit ? form.requestSubmit(el) : form.submit();
                }
            });
        }, true);

        doc.addEventListener('submit', function (e) {
            var form = e.target;
            if (!form || form.getAttribute('data-confirmed') === 'true') return;
            var message = form.getAttribute('data-confirm') || '';
            if (!message) return;
            e.preventDefault();
            e.stopPropagation();

            var title = form.getAttribute('data-confirm-title') || 'Confirm';
            var confirmText = form.getAttribute('data-confirm-ok') || 'Confirm';
            var cancelText = form.getAttribute('data-confirm-cancel') || 'Cancel';
            var danger = (form.getAttribute('data-confirm-danger') || '').toLowerCase() === 'true';

            confirm({ title: title, message: message, confirmText: confirmText, cancelText: cancelText, danger: danger }).then(function (ok) {
                if (!ok) return;
                form.setAttribute('data-confirmed', 'true');
                form.submit();
            });
        }, true);
    }

    installFetchInterceptor();
    installConfirmDelegation();

    function initFromDom() {
        var root = ensureRoot();
        var stored = loadStoredConfig();
        if (stored && typeof stored === 'object') configure(stored);

        var rootPos = root.getAttribute('data-default-position');
        var rootMaxVisible = root.getAttribute('data-max-visible');
        var rootTimeout = root.getAttribute('data-default-timeout-ms');
        var rootNewestOnTop = root.getAttribute('data-newest-on-top');
        var rootMaxQueue = root.getAttribute('data-max-queue');

        var patch = {};
        if (rootPos) patch.position = rootPos;
        if (rootMaxVisible) patch.maxVisible = rootMaxVisible;
        if (rootTimeout) patch.defaultTimeoutMs = rootTimeout;
        if (rootNewestOnTop) patch.newestOnTop = (rootNewestOnTop.toLowerCase() === 'true');
        if (rootMaxQueue) patch.maxQueue = rootMaxQueue;
        if (Object.keys(patch).length) configure(patch);

        var initEl = doc.getElementById(initialId);
        if (!initEl) return;

        var success = initEl.getAttribute('data-success');
        var error = initEl.getAttribute('data-error');
        var warning = initEl.getAttribute('data-warning');
        var info = initEl.getAttribute('data-info');
        var pwSuccess = initEl.getAttribute('data-pw-success');
        var pwError = initEl.getAttribute('data-pw-error');

        if (success) push({ type: 'success', message: success, dedupeKey: 'server:success:' + success });
        if (info) push({ type: 'info', message: info, dedupeKey: 'server:info:' + info });
        if (warning) push({ type: 'warning', message: warning, dedupeKey: 'server:warning:' + warning });
        if (error) push({ type: 'error', message: error, dedupeKey: 'server:error:' + error });
        if (pwSuccess) push({ type: 'success', title: 'Password', message: pwSuccess, dedupeKey: 'server:pwSuccess:' + pwSuccess });
        if (pwError) push({ type: 'error', title: 'Password', message: pwError, dedupeKey: 'server:pwError:' + pwError });
    }

    function installGlobalErrorHandlers() {
        window.addEventListener('unhandledrejection', function (e) {
            var msg = e && e.reason && e.reason.message ? e.reason.message : null;
            if (!msg) msg = 'Unexpected error. Please try again.';
            push({ type: 'error', message: msg, dedupeKey: 'unhandled:' + msg });
        });
        window.addEventListener('error', function (e) {
            if (!e) return;
            var msg = e.message || null;
            if (!msg) return;
            push({ type: 'error', message: msg, dedupeKey: 'error:' + msg });
        });
    }

    function boot() {
        if (state.booted) return;
        state.booted = true;
        ensureRoot();
        initFromDom();
        installGlobalErrorHandlers();
        window.addEventListener('mtms:flashConfigure', function (e) {
            if (e && e.detail) configure(e.detail);
        });
        window.addEventListener('mtms:flashPushRequest', function (e) {
            if (e && e.detail) push(e.detail);
        });
    }

    if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', boot);
    else boot();

    window.MTMSFlash = {
        push: push,
        configure: configure,
        getConfig: getConfig,
        confirm: confirm,
        success: function (message, opts) { return push(shallowMerge({ type: 'success', message: message }, opts || {})); },
        error: function (message, opts) { return push(shallowMerge({ type: 'error', message: message }, opts || {})); },
        warning: function (message, opts) { return push(shallowMerge({ type: 'warning', message: message }, opts || {})); },
        info: function (message, opts) { return push(shallowMerge({ type: 'info', message: message }, opts || {})); }
    };
})();
