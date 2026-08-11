package org.shirakawatyu.yamibo.novel.util.blog

internal object MobileBlogJsScripts {
    private const val CURRENT_UID_PLACEHOLDER = "__YAMIBO_CURRENT_UID__"

    fun enhancementsJs(currentUid: String): String = ENHANCEMENTS_JS.replace(
        CURRENT_UID_PLACEHOLDER,
        currentUid.takeIf { it.matches(Regex("[1-9]\\d*")) }.orEmpty()
    )

    val ENHANCEMENTS_JS = """
        (function() {
            var body = document.body;
            if (!body || body.id !== 'home' || !body.classList.contains('pg_space')) return;

            var pageUrl;
            try { pageUrl = new URL(location.href); } catch (e) { return; }
            if (
                String(pageUrl.searchParams.get('mod') || '').toLowerCase() !== 'space' ||
                String(pageUrl.searchParams.get('do') || '').toLowerCase() !== 'blog' ||
                !/^[1-9]\d*$/.test(pageUrl.searchParams.get('id') || '')
            ) return;

            function installInviteNavigation() {
                if (window.__yamiboMobileBlogInviteV1) return;
                window.__yamiboMobileBlogInviteV1 = true;
                var inviteLink = document.getElementById('a_invite');
                if (inviteLink) {
                    inviteLink.classList.remove('dialog');
                    inviteLink.removeAttribute('onclick');
                }
                document.addEventListener('click', function(event) {
                    var link = event.target && event.target.closest
                        ? event.target.closest('#a_invite[href]')
                        : null;
                    if (!link) return;
                    event.preventDefault();
                    event.stopPropagation();
                    event.stopImmediatePropagation();
                    try {
                        var inviteUrl = new URL(link.href, location.href);
                        inviteUrl.searchParams.set('mobile', '2');
                        location.assign(inviteUrl.href);
                    } catch (e) {
                        location.assign(link.href);
                    }
                }, true);
            }
            installInviteNavigation();

            if (window.__yamiboMobileBlogEnhancementsV2) return;
            window.__yamiboMobileBlogEnhancementsV2 = true;

            var foot = document.querySelector('.viewthread .plc > .threadlist_foot') ||
                document.querySelector('.threadlist_foot');
            if (!foot || document.getElementById('yamibo-blog-reactions')) return;

            var ownerUid = pageUrl.searchParams.get('uid') || '';
            if (!/^[1-9]\d*$/.test(ownerUid)) {
                var author = document.querySelector(
                    '.viewthread .authi a[href*="mod=space"][href*="uid="]'
                );
                if (author) {
                    try { ownerUid = new URL(author.href, location.href).searchParams.get('uid') || ''; }
                    catch (e) {}
                }
            }
            var blogId = pageUrl.searchParams.get('id') || '';
            var currentUid = '__YAMIBO_CURRENT_UID__';
            var isOwnBlog = Boolean(currentUid && ownerUid === currentUid);
            var reactionTypes = [
                { clickId: '1', label: '路过' },
                { clickId: '2', label: '雷人' },
                { clickId: '3', label: '握手' },
                { clickId: '4', label: '鲜花' },
                { clickId: '5', label: '鸡蛋' }
            ];

            if (!document.getElementById('yamibo-mobile-blog-reaction-style')) {
                var style = document.createElement('style');
                style.id = 'yamibo-mobile-blog-reaction-style';
                style.textContent = [
                    '#yamibo-blog-reactions{margin:36px 0 10px;color:var(--dz-FC-333,#333)}',
                    '#yamibo-blog-reactions .ybr-options{display:grid;',
                    'grid-template-columns:repeat(5,minmax(0,1fr));gap:6px}',
                    '#yamibo-blog-reactions .ybr-option,',
                    '#yamibo-blog-reactions .ybr-option:hover,',
                    '#yamibo-blog-reactions .ybr-option:focus,',
                    '#yamibo-blog-reactions .ybr-option:active{appearance:none;',
                    'border:0!important;background:transparent!important;',
                    'background-image:none!important;box-shadow:none!important;',
                    'text-shadow:none!important;color:var(--dz-FC-color,#6e2b19)!important;',
                    'min-width:0;padding:0 2px;text-align:center;cursor:pointer}',
                    '#yamibo-blog-reactions .ybr-option:disabled{opacity:1;cursor:default}',
                    '#yamibo-blog-reactions .ybr-meter{height:82px;display:flex;',
                    'flex-direction:column;align-items:center;justify-content:flex-end;margin-bottom:8px}',
                    '#yamibo-blog-reactions .ybr-bar{position:relative;display:block;width:26px;',
                    'min-height:4px;border-radius:4px 4px 1px 1px;',
                    'background:var(--dz-BG-color,#551200)!important;',
                    'background-image:none!important;box-shadow:none!important;',
                    'transition:height .2s ease}',
                    '#yamibo-blog-reactions .ybr-count{display:block;margin-bottom:3px;',
                    'font-size:11px;line-height:1;',
                    'color:var(--dz-FC-666,#666);white-space:nowrap}',
                    '#yamibo-blog-reactions .ybr-label{display:block;font-size:12px;',
                    'line-height:1.4;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}',
                    '#yamibo-blog-reactions .ybr-status{min-height:17px;font-size:12px;',
                    'line-height:1.4;color:var(--dz-FC-999,#999);text-align:center;padding-top:12px}'
                ].join('');
                (document.head || document.documentElement).appendChild(style);
            }

            var section = document.createElement('section');
            section.id = 'yamibo-blog-reactions';
            section.innerHTML =
                '<div class="ybr-options"></div>' +
                '<div class="ybr-status">正在加载票数…</div>';
            foot.parentNode.insertBefore(section, foot);

            var optionsBox = section.querySelector('.ybr-options');
            var statusBox = section.querySelector('.ybr-status');
            var activeRequest = '';
            var busy = false;
            var counts = {};

            function makeRequestId() {
                return String(Date.now()) + '-' + Math.random().toString(36).slice(2);
            }

            function setBusy(nextBusy) {
                busy = nextBusy;
                section.querySelectorAll('.ybr-option').forEach(function(button) {
                    button.disabled = isOwnBlog || nextBusy;
                });
            }

            function renderOptions() {
                var knownCounts = reactionTypes.map(function(type) {
                    return Object.prototype.hasOwnProperty.call(counts, type.clickId)
                        ? Number(counts[type.clickId]) || 0
                        : 0;
                });
                var maximum = Math.max.apply(null, knownCounts.concat([1]));
                optionsBox.textContent = '';
                reactionTypes.forEach(function(type) {
                    var known = Object.prototype.hasOwnProperty.call(counts, type.clickId);
                    var count = known ? Number(counts[type.clickId]) || 0 : 0;
                    var button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'ybr-option';
                    button.disabled = isOwnBlog || busy;
                    button.setAttribute('data-clickid', type.clickId);
                    button.setAttribute('aria-label', '给帖主表态：' + type.label);

                    var meter = document.createElement('span');
                    meter.className = 'ybr-meter';
                    var bar = document.createElement('span');
                    bar.className = 'ybr-bar';
                    bar.style.height = (
                        known && count > 0
                            ? Math.max(10, Math.round(count / maximum * 68))
                            : 4
                    ) + 'px';
                    var countNode = document.createElement('span');
                    countNode.className = 'ybr-count';
                    countNode.textContent = known ? String(count) : '…';
                    meter.appendChild(countNode);
                    meter.appendChild(bar);

                    var label = document.createElement('span');
                    label.className = 'ybr-label';
                    label.textContent = type.label;
                    button.appendChild(meter);
                    button.appendChild(label);
                    optionsBox.appendChild(button);
                });
            }

            function renderPayload(payload) {
                var serverOptions = Array.isArray(payload.options) ? payload.options : [];
                var nextCounts = {};
                serverOptions.forEach(function(option) {
                    var clickId = String(option.clickId || '');
                    if (!reactionTypes.some(function(type) { return type.clickId === clickId; })) return;
                    nextCounts[clickId] = Math.max(0, Number(option.count) || 0);
                });
                counts = nextCounts;
                renderOptions();
                statusBox.textContent = String(
                    payload.message ||
                    (isOwnBlog ? '自己的日志仅可查看表态' : '点击选项即可给帖主表态')
                );
                setBusy(false);
            }

            window.__yamiboBlogReactionReceive = function(requestId, payload) {
                if (String(requestId) !== activeRequest) return;
                if (payload && payload.error) {
                    statusBox.textContent = String(payload.error) +
                        (isOwnBlog ? '' : '；点击选项可重试');
                    setBusy(false);
                    return;
                }
                renderPayload(payload || {});
            };

            optionsBox.addEventListener('click', function(event) {
                var button = event.target && event.target.closest
                    ? event.target.closest('.ybr-option[data-clickid]')
                    : null;
                if (!button || busy || isOwnBlog) return;
                var clickId = button.getAttribute('data-clickid') || '';
                if (!/^[1-5]$/.test(clickId)) return;
                if (!window.AndroidBlogReaction || !/^[1-9]\d*$/.test(ownerUid)) {
                    statusBox.textContent = '表态功能暂时不可用';
                    return;
                }
                setBusy(true);
                statusBox.textContent = '正在提交表态…';
                activeRequest = makeRequestId();
                window.AndroidBlogReaction.react(ownerUid, blogId, clickId, activeRequest);
            });

            renderOptions();
            if (!window.AndroidBlogReaction || !/^[1-9]\d*$/.test(ownerUid)) {
                statusBox.textContent = '表态功能暂时不可用';
                return;
            }
            activeRequest = makeRequestId();
            window.AndroidBlogReaction.load(ownerUid, blogId, activeRequest);
        })();
    """.trimIndent()
}
