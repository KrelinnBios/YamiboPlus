package org.shirakawatyu.yamibo.novel.util

import org.shirakawatyu.yamibo.novel.util.theme.DARK_MODE_CSS_RULES_CLASSIC
import org.shirakawatyu.yamibo.novel.util.theme.LIGHT_MODE_CSS_RULES_CLASSIC
import org.shirakawatyu.yamibo.novel.util.theme.MemberSpaceGuard

object PageJsScripts {

    /**
     * 隐藏论坛自带的全局底栏，避免与应用底栏重复；私信会话的发送栏同样使用
     * `.foot` / `.foot_height`，必须单独保留，否则只能查看消息而无法回复。
     */
    fun getForumChromeHideCss(): String = """
        .foot.flex-box:not(.foot_reply):not(.msg_post) { display: none !important; }
        .foot_height { display: none !important; }
        #pmform .foot_height { display: block !important; }
        #pmform .foot.msg_post { display: flex !important; }
    """.trimIndent().replace("\n", " ")

    private fun combineJs(vararg namedScripts: Pair<String, String>): String {
        return namedScripts.joinToString("\n;\n") { (name, script) ->
            """
                (function() {
                    try {
                        $script
                    } catch (e) {
                        console.error('[YamiboInject:' + '$name' + ']', e);
                    }
                })();
            """.trimIndent()
        }
    }

    private fun jsStringLiteral(value: String): String = buildString {
        append('\'')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003C")
                '>' -> append("\\u003E")
                '&' -> append("\\u0026")
                else -> append(ch)
            }
        }
        append('\'')
    }

    private val PSWP_STATE_SYNC_JS = """
        if (!window.__yamiboPswpStateTools) {
            window.__yamiboPswpStateTools = true;
            window.__yamiboPswpIsOpen = function(pswp) {
                if (!pswp) return false;
                var style = window.getComputedStyle ? window.getComputedStyle(pswp) : null;
                var rect = pswp.getBoundingClientRect ? pswp.getBoundingClientRect() : { width: 0, height: 0 };
                var visibleByStyle = style &&
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    parseFloat(style.opacity || '0') > 0.01 &&
                    rect.width > 0 &&
                    rect.height > 0 &&
                    pswp.getAttribute('aria-hidden') !== 'true';
                return pswp.classList.contains('pswp--open') ||
                    pswp.classList.contains('pswp--visible') ||
                    visibleByStyle;
            };
            window.__yamiboPswpCleanupClosed = function(pswp) {
                if (!pswp) return;
                if (window.__yamiboPswpIsOpen(pswp)) {
                    pswp.style.pointerEvents = '';
                } else {
                    pswp.style.pointerEvents = 'none';
                }
            };
        }
    """.trimIndent()

    val FIX_CAROUSEL_LAYOUT_JS = """
        (function() {
            if (document.getElementById('carousel-fix-style')) return;
            var style = document.createElement('style');
            style.id = 'carousel-fix-style';
            style.innerHTML = `
                .slidebox .swiper-wrapper,
                .scrool_img .swiper-wrapper,
                .slide .swiper-wrapper,
                #slide .swiper-wrapper,
                .img_slide .swiper-wrapper {
                    display: flex !important;
                    flex-direction: row !important;
                    flex-wrap: nowrap !important;
                }
                .slidebox .swiper-slide,
                .scrool_img .swiper-slide,
                .slide .swiper-slide,
                #slide .swiper-slide,
                .img_slide .swiper-slide {
                    width: 100% !important;
                    flex-shrink: 0 !important;
                    aspect-ratio: 363 / 126 !important;
                    background-color: rgba(212, 200, 176, 0.2) !important;
                    display: block !important;
                    box-sizing: border-box !important;
                    visibility: visible !important;
                }
                .slidebox img,
                .scrool_img img,
                .slide img,
                #slide img,
                .img_slide img {
                    width: 100% !important;
                    height: 100% !important;
                    object-fit: cover !important;
                    display: block !important;
                    visibility: visible !important;
                    opacity: 1 !important;
                }
            `;
            if(document.head) document.head.appendChild(style);
            else document.documentElement.appendChild(style);
        })();
    """.trimIndent()

    val INJECT_PSWP_AND_MANGA_JS = """
        (function(){
            $PSWP_STATE_SYNC_JS
            window.__pswpInit = function() {
                if (window.__globalPswpAttached) return;
                var pswp = document.querySelector('.pswp');
                if (!pswp) {
                    if (window.__pswpWaitingObserverAttached) return;
                    window.__pswpWaitingObserverAttached = true;
                    var bodyObserver = new MutationObserver(function(mutations, obs) {
                        if (document.querySelector('.pswp')) {
                            obs.disconnect();
                            window.__pswpWaitingObserverAttached = false;
                            window.__pswpInit();
                        }
                    });
                    bodyObserver.observe(document.body, { childList: true, subtree: true });
                    return;
                }
                window.__globalPswpAttached = true;
                var checkState = function() {
                    var isOpen = pswp.classList.contains('pswp--open') ||
                                 pswp.classList.contains('pswp--visible') || 
                                 (getComputedStyle(pswp).display !== 'none' && getComputedStyle(pswp).opacity > 0);
                    if (window.__yamiboPswpIsOpen) isOpen = window.__yamiboPswpIsOpen(pswp);
                    if (window.__yamiboPswpCleanupClosed) window.__yamiboPswpCleanupClosed(pswp);
                    if (window.__pswpLastState !== isOpen) {
                        window.__pswpLastState = isOpen;
                        if (window.AndroidFullscreen) window.AndroidFullscreen.notify(isOpen);
                        if (isOpen) {
                            setTimeout(function(){ window.dispatchEvent(new Event('resize')); }, 100);
                        }
                    }
                };
                var pswpObserver = new MutationObserver(checkState);
                pswpObserver.observe(pswp, { attributes: true, attributeFilter: ['class', 'style', 'aria-hidden'] });
                checkState();
            };
            window.__pswpInit();
            if (!window._backBtnFixed) {
                window._backBtnFixed = true;
                document.addEventListener('click', function(e) {
                    var target = e.target.closest ? e.target.closest('a[href*="history.back"]') : null;
                    if (target) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (window.NativeMangaApi && window.NativeMangaApi.goBack) {
                            window.NativeMangaApi.goBack();
                        } else {
                            window.history.back();
                        }
                    }
                }, true);
            }
            var a = document.querySelector('.header h2 a');
            var isManga = false;
            if (a) {
                var t = a.innerText;
                isManga = t.indexOf('中文百合漫画区') !== -1 || 
                          t.indexOf('百合漫画图源区') !== -1;
            }
            if (isManga) {
                if (window._mangaClickInjected) return 'true';
                window._mangaClickInjected = true;
                
                var disablePhotoSwipe = function() {
                    var links = document.querySelectorAll('a[data-pswp-width], .img_one a, .message a, td.t_f a, .postmessage a');
                    for (var i = 0; i < links.length; i++) {
                        var aNode = links[i];
                        if (aNode.querySelector('img')) {
                            aNode.removeAttribute('data-pswp-width');
                            if (aNode.href && aNode.href.indexOf('javascript') === -1) {
                                aNode.setAttribute('data-disabled-href', aNode.href);
                                aNode.removeAttribute('href');
                            }
                        }
                    }
                };
                disablePhotoSwipe();
                var observer = new MutationObserver(disablePhotoSwipe);
                observer.observe(document.body, { childList: true, subtree: true });
                
                document.addEventListener('click', function(e) {
                    var targetContainer = e.target.closest('.img_one li, .img_one a, .message a, td.t_f a, .postmessage a, .img_one img, .message img, td.t_f img, .postmessage img');
                    if (!targetContainer) return;
                    
                    var targetImg = targetContainer.tagName.toLowerCase() === 'img' ? targetContainer : targetContainer.querySelector('img');
                    
                    if (targetImg) {
                        var targetRawSrc = targetImg.getAttribute('zsrc') ||
                            targetImg.getAttribute('data-src') ||
                            targetImg.getAttribute('zoomfile') ||
                            targetImg.getAttribute('file') ||
                            targetImg.getAttribute('src') || '';
                        
                        if (targetRawSrc.indexOf('smiley') === -1) {
                            e.preventDefault(); 
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                            
                            var allImgs = document.querySelectorAll('.img_one img, .message img:not([src*="smiley"]), td.t_f img:not([src*="smiley"]), .postmessage img:not([src*="smiley"])');
                            var urls = [];
                            var clickedIndex = 0;
                            for (var i = 0; i < allImgs.length; i++) {
                                var rawSrc = allImgs[i].getAttribute('zsrc') ||
                                    allImgs[i].getAttribute('data-src') ||
                                    allImgs[i].getAttribute('zoomfile') ||
                                    allImgs[i].getAttribute('file') ||
                                    allImgs[i].getAttribute('src');
                                if (rawSrc) {
                                    var absoluteUrl = new URL(rawSrc, document.baseURI).href;
                                    urls.push(absoluteUrl);
                                    if (allImgs[i] === targetImg) clickedIndex = urls.length - 1;
                                }
                            }
                            if (window.NativeMangaApi) {
                                window.NativeMangaApi.openNativeManga(urls.join('|||'), clickedIndex, document.title);
                            }
                        }
                    }
                }, true);
            }
            return isManga ? 'true' : 'false';
        })()
    """.trimIndent()

    val THREAD_LIST_CLICK_FIX_JS = """
        (function() {
            if (window.__threadListClickFixV3) return;
            window.__threadListClickFixV3 = true;

            function closest(el, selector) {
                while (el && el !== document && el.nodeType === 1) {
                    if (el.matches && el.matches(selector)) return el;
                    el = el.parentElement;
                }
                return null;
            }

            function isSafeThreadUrl(rawHref) {
                if (!rawHref || rawHref === '#' || /^javascript:/i.test(rawHref)) return false;
                try {
                    var url = new URL(rawHref, document.baseURI);
                    if (url.hostname !== 'bbs.yamibo.com') return false;
                    var path = String(url.pathname || '').replace(/^\/+/, '').toLowerCase();
                    var query = String(url.search || '').toLowerCase();
                    return /^thread-\d+/.test(path) ||
                           (path === 'forum.php' && query.indexOf('mod=viewthread') !== -1);
                } catch (e) {
                    return false;
                }
            }

            if (!document.getElementById('yamibo-thread-list-click-style')) {
                var style = document.createElement('style');
                style.id = 'yamibo-thread-list-click-style';
                style.textContent = 'li.list { cursor: pointer; -webkit-tap-highlight-color: rgba(0,0,0,0.08); }';
                document.head.appendChild(style);
            }

            document.addEventListener('click', function(e) {
                var li = closest(e.target, 'li.list');
                if (!li) return;

                if (closest(e.target, 'a[href], button, input, textarea, select, label, .pswp')) return;

                var threadLink =
                    li.querySelector('a[href*="mod=viewthread"]') ||
                    li.querySelector('a[href^="thread-"]');

                if (!threadLink || !threadLink.href) return;
                if (!isSafeThreadUrl(threadLink.getAttribute('href'))) return;

                e.preventDefault();
                e.stopPropagation();

                location.href = threadLink.href;
            }, true);
        })();
    """.trimIndent()

    val BBS_THREAD_NAVIGATION_JS = """
        (function() {
            if (window.__yamiboBbsThreadNavigationV6) return;
            window.__yamiboBbsThreadNavigationV6 = true;
            function threadKind(link) {
                if (!link || !link.href) return '';
                try {
                    var url = new URL(link.href, document.baseURI);
                    var path = url.pathname.replace(/^\/+/, '').toLowerCase();
                    var mod = String(url.searchParams.get('mod') || '').toLowerCase();
                    var goto = String(url.searchParams.get('goto') || '').toLowerCase();
                    var tid = url.searchParams.get('tid') || '';
                    var pid = url.searchParams.get('pid') || '';
                    var ptid = url.searchParams.get('ptid') || '';
                    if (url.hostname !== 'bbs.yamibo.com') return '';
                    if (/^thread-\d+(?:-\d+){0,2}\.html${'$'}/.test(path) ||
                        (path === 'forum.php' && mod === 'viewthread' && /^[1-9]\d*${'$'}/.test(tid))) {
                        return 'viewthread';
                    }
                    if (path === 'forum.php' &&
                        ((mod === 'redirect' && /^[1-9]\d*${'$'}/.test(pid)) ||
                         (goto === 'findpost' &&
                          (/^[1-9]\d*${'$'}/.test(pid) || /^[1-9]\d*${'$'}/.test(ptid))))) {
                        return 'findpost';
                    }
                    return '';
                } catch (e) { return ''; }
            }
            function navigationTarget(link) {
                var url = new URL(link.href, document.baseURI);
                var expectedMobile = document.getElementById('toptb') ? 'no' : '2';
                url.searchParams.delete('mobile');
                url.searchParams.append('mobile', expectedMobile);
                return url.href;
            }
            function navigateToPost(link) {
                var desktopTemplate = !!document.getElementById('toptb');
                if (window.AndroidSearchNav.navigateToPostWithTemplate) {
                    window.AndroidSearchNav.navigateToPostWithTemplate(link.href, desktopTemplate);
                } else {
                    window.AndroidSearchNav.navigateToPost(link.href);
                }
            }
            document.addEventListener('click', function(event) {
                var link = event.target.closest ? event.target.closest('a[href]') : null;
                if (link) {
                    if (!threadKind(link)) return;
                } else {
                    var item = event.target.closest
                        ? event.target.closest('li.list, tbody[id^="normalthread_"], tbody[id^="stickthread_"]')
                        : null;
                    if (!item) return;
                    var links = item.querySelectorAll('a[href]');
                    for (var i = 0; i < links.length && !link; i++) if (threadKind(links[i])) link = links[i];
                }
                var kind = threadKind(link);
                if (!kind) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                if (kind === 'viewthread') {
                    // 大区主题列表只在当前 WebView 内发起一次导航，避免再绕过 Compose 状态后
                    // 调用 loadUrl，与当前点击产生加载竞态。
                    window.location.assign(navigationTarget(link));
                } else if (window.AndroidSearchNav && window.AndroidSearchNav.navigateToPost) {
                    navigateToPost(link);
                }
            }, true);
        })();
    """.trimIndent()

    val REMOVE_TRANSITION_STYLE_JS = """
        var style = document.getElementById('manga-transition-style');
        if (style) style.remove();
    """.trimIndent()

    val CLEANUP_FULLSCREEN_JS = """
        (function() {
            var style = document.getElementById('manga-transition-style');
            if (style) style.remove();
            var pswp = document.querySelector('.pswp');
            if (window.__yamiboPswpCleanupClosed) {
                window.__yamiboPswpCleanupClosed(pswp);
            } else if (pswp) {
                pswp.style.pointerEvents = 'none';
            }
            window.__pswpLastState = false;
            window.pswpObserverAttached = false;
        })();
    """.trimIndent()

    val CHECK_SECTION_JS = """
        (function() {
            var sectionHeader = document.querySelector('.header h2 a');
            if (sectionHeader) return sectionHeader.innerText.trim();
            var nav = document.querySelector('.z, .nav, .mz, .thread_nav, .sq_nav');
            if (nav) return nav.innerText.trim();
            return '';
        })();
    """.trimIndent()

    // 获取帖子历史详情专用JS
    val EXTRACT_THREAD_INFO_JS = """
        (function() {
            var title = document.title || '';
            var section = '';
            var sectionHeader = document.querySelector('.header h2 a');
            if (sectionHeader) {
                section = sectionHeader.innerText.trim();
            } else {
                var nav = document.querySelector('.z, .nav, .mz, .thread_nav, .sq_nav');
                if (nav) section = nav.innerText.trim();
            }
            var author = '';
            var authorEl = document.querySelector('.authi a.xw1, .authi a, .mtit .z a, .pi .authi a');
            if (authorEl) {
                author = authorEl.innerText.trim();
            } else {
                var byUser = document.querySelector('.by a');
                if (byUser) author = byUser.innerText.trim();
            }
            title = title.replace(/\s*-\s*百合会.*$/, '');
            return JSON.stringify({title: title, section: section, author: author});
        })();
    """.trimIndent()

    // 从 assets/icons/link-45deg.svg 加载的图标内容，由 YamiboApplication 在启动时初始化
    @Volatile var copyLinkIconSvg: String? = null

    // 仅在帖子详情页的 #nav-more-menu 中注入“复制链接”菜单项。
    // 注意：SVG 可能来自 assets，必须转成 JS 字符串字面量；否则换行/引号会让整段注入脚本语法错误。
    val INJECT_COPY_LINK_JS by lazy {
        val iconSvg = copyLinkIconSvg ?: """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-link-45deg nav-more-item-text" viewBox="0 0 16 16" style="position:relative;top:5px"><path d="M4.715 6.542 3.343 7.914a3 3 0 1 0 4.243 4.243l1.828-1.829A3 3 0 0 0 8.586 5.5L8 6.086a1.002 1.002 0 0 0-.154.199 2 2 0 0 1 .861 3.337L6.88 11.45a2 2 0 1 1-2.83-2.83l.793-.792a4.018 4.018 0 0 1-.128-1.287z"/><path d="M6.586 4.672A3 3 0 0 0 7.414 9.5l.775-.776a2 2 0 0 1-.896-3.346L9.12 3.55a2 2 0 1 1 2.83 2.83l-.793.792c.112.42.155.855.128 1.287l1.372-1.372a3 3 0 1 0-4.243-4.243L6.586 4.672z"/></svg>"""
        val iconSvgLiteral = jsStringLiteral(iconSvg)
        """
        (function() {
            var ITEM_ID = 'yamibo-copy-link-menu-item';
            var OBSERVER_KEY = '__yamiboCopyLinkObserver';

            function isThreadPage() {
                try {
                    var url = new URL(window.location.href, document.baseURI);
                    var host = String(url.hostname || '').toLowerCase();
                    var path = String(url.pathname || '').replace(/^\/+/, '').toLowerCase();
                    var query = String(url.search || '').toLowerCase();
                    var bodyIsThread = !!(document.body && document.body.classList && document.body.classList.contains('pg_viewthread'));
                    var urlIsThread = /^thread-\d+-\d+-\d+\.html$/.test(path) ||
                        (path === 'forum.php' && query.indexOf('mod=viewthread') !== -1 && query.indexOf('tid=') !== -1);
                    return (host === 'bbs.yamibo.com' || host === 'm.yamibo.com' || host === 'yamibo.com' || host === 'www.yamibo.com') && (bodyIsThread || urlIsThread);
                } catch (e) {
                    return !!(document.body && document.body.classList && document.body.classList.contains('pg_viewthread'));
                }
            }

            function cleanTitle() {
                var title = document.title || '';
                return title.replace(/\s*-\s*百合会.*${'$'}/, '').trim();
            }

            function threadUrl() {
                var canonical = document.querySelector('link[rel="canonical"]');
                if (canonical && canonical.href) return canonical.href;
                return window.location.href;
            }

            function removeItem() {
                var oldItem = document.getElementById(ITEM_ID) || document.getElementById('copy-link-menu-item');
                if (oldItem && oldItem.parentNode) oldItem.parentNode.removeChild(oldItem);
            }

            function inject() {
                if (!isThreadPage()) {
                    removeItem();
                    return true;
                }

                var menu = document.getElementById('nav-more-menu');
                if (!menu) return false;

                var legacyItem = document.getElementById('copy-link-menu-item');
                if (legacyItem && legacyItem.id !== ITEM_ID) legacyItem.id = ITEM_ID;
                if (document.getElementById(ITEM_ID)) return true;

                var item = document.createElement('a');
                item.id = ITEM_ID;
                item.className = 'nav-more-item';
                item.href = 'javascript:;';
                item.setAttribute('role', 'button');
                item.setAttribute('aria-label', '复制帖子链接');
                item.style.marginTop = '-3px';
                item.innerHTML = $iconSvgLiteral + '<span class="nav-more-item-text">复制链接</span>';
                item.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (window.AndroidFullscreen && typeof window.AndroidFullscreen.copyLink === 'function') {
                        window.AndroidFullscreen.copyLink(cleanTitle(), threadUrl());
                    }
                    return false;
                }, true);
                menu.appendChild(item);
                return true;
            }

            if (window[OBSERVER_KEY]) {
                try { window[OBSERVER_KEY].disconnect(); } catch (e) {}
                window[OBSERVER_KEY] = null;
            }

            if (inject()) return;

            var root = document.documentElement || document.body;
            if (!root || typeof MutationObserver === 'undefined') return;

            var observer = new MutationObserver(function() {
                if (inject()) {
                    observer.disconnect();
                    window[OBSERVER_KEY] = null;
                }
            });
            observer.observe(root, { childList: true, subtree: true });
            window[OBSERVER_KEY] = observer;
            setTimeout(function() {
                if (window[OBSERVER_KEY] === observer) {
                    observer.disconnect();
                    window[OBSERVER_KEY] = null;
                }
            }, 5000);
        })();
        """.trimIndent()
    }

    val AUTO_OPEN_MANGA_JS = """
        (function() {
            // 版块白名单
            var sectionHeader = document.querySelector('.header h2 a');
            var sectionName = sectionHeader ? sectionHeader.innerText.trim() : '';
            if (sectionName !== '') {
                var allowedSections = ['中文百合漫画区', '百合漫画图源区'];
                var isAllowedSection = false;
                for (var k = 0; k < allowedSections.length; k++) {
                    if (sectionName.indexOf(allowedSections[k]) !== -1) {
                        isAllowedSection = true;
                        break;
                    }
                }
                if (!isAllowedSection) {
                    if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) {
                        window.AndroidFullscreen.notifyMangaActionDone();
                    }
                    return;
                }
            }
            
            // 公告帖拦截
            var typeLabel = document.querySelector('.view_tit em');
            if (typeLabel && typeLabel.innerText.indexOf('公告') !== -1) {
                if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) {
                    window.AndroidFullscreen.notifyMangaActionDone();
                }
                return; 
            }

            // 过渡黑屏样式
            if (!document.getElementById('manga-transition-style')) {
                var style = document.createElement('style');
                style.id = 'manga-transition-style';
                style.innerHTML = 'body > *:not(.pswp) { opacity: 0 !important; pointer-events: none !important; } body { background: #000 !important; }';
                document.head.appendChild(style);
            }

            function abortAndNotify() {
                var style = document.getElementById('manga-transition-style');
                if (style) style.remove();
                if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) {
                    window.AndroidFullscreen.notifyMangaActionDone();
                }
            }

            if (window.NativeMangaApi) {
                var allImgs = document.querySelectorAll('.img_one img, .message img:not([src*="smiley"]), td.t_f img:not([src*="smiley"]), .postmessage img:not([src*="smiley"])');
                if (allImgs.length > 0) {
                    var urls = [];
                    for (var i = 0; i < allImgs.length; i++) {
                        var rawSrc = allImgs[i].getAttribute('zsrc') ||
                            allImgs[i].getAttribute('data-src') ||
                            allImgs[i].getAttribute('zoomfile') ||
                            allImgs[i].getAttribute('file') ||
                            allImgs[i].getAttribute('src');
                        if (rawSrc) {
                            urls.push(new URL(rawSrc, document.baseURI).href);
                        }
                    }
                    if (urls.length > 0) {
                        window.NativeMangaApi.openNativeManga(urls.join('|||'), 0, document.title);
                        return;
                    }
                }
            }

            var clickTimer = null;
            var timeoutTimer = null;
            
            var observer = new MutationObserver(function(mutations, obs) {
                if (document.querySelector('.pswp')) {
                    obs.disconnect();
                    clearTimeout(timeoutTimer);
                    if (clickTimer) clearInterval(clickTimer);
                    
                    if (window.AndroidFullscreen) {
                        window.AndroidFullscreen.notify(true);
                        window.AndroidFullscreen.notifyMangaActionDone();
                    }
                    return;
                }
            });

            observer.observe(document.body, { childList: true, subtree: true });

            var clickAttempts = 0;
            var maxClicks = 10;

            function tryClickTarget() {
                if (clickAttempts >= maxClicks) {
                    if (clickTimer) clearInterval(clickTimer);
                    return;
                }
                if (document.querySelector('.pswp')) return; 
                
                clickAttempts++;
                var links = document.querySelectorAll('a[data-pswp-width], .img_one a.orange, .message a.orange, td.t_f a.orange, .postmessage a.orange');
                var clicked = false;
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href') || '';
                    var innerHtml = links[i].innerHTML || '';
                    if (href.toLowerCase().indexOf('.gif') === -1 && href.indexOf('static/image/') === -1 && innerHtml.indexOf('static/image/') === -1) {
                        links[i].dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                        clicked = true;
                        break; 
                    }
                }

                if (!clicked) {
                    var fallbackImgs = document.querySelectorAll('.img_one img');
                    if(fallbackImgs.length > 0 && fallbackImgs[0].parentElement && fallbackImgs[0].parentElement.tagName === 'A'){
                        fallbackImgs[0].parentElement.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                    }
                }
            }

            tryClickTarget();

            clickTimer = setInterval(tryClickTarget, 250);

            timeoutTimer = setTimeout(function() {
                observer.disconnect();
                if (clickTimer) clearInterval(clickTimer);
                abortAndNotify();
            }, 5000);
        })();
    """.trimIndent()

    // MinePage脚本
    val MINE_INJECT_PSWP_AND_MANGA_JS = """
        (function(){
            $PSWP_STATE_SYNC_JS
            window.__pswpInit = function() {
                if (window.__globalPswpAttached) return;
                var pswp = document.querySelector('.pswp');
                if (!pswp) {
                    if (window.__pswpWaitingObserverAttached) return;
                    window.__pswpWaitingObserverAttached = true;
                    var bodyObserver = new MutationObserver(function(mutations, obs) {
                        if (document.querySelector('.pswp')) {
                            obs.disconnect();
                            window.__pswpWaitingObserverAttached = false;
                            window.__pswpInit();
                        }
                    });
                    bodyObserver.observe(document.body, { childList: true, subtree: true });
                    return;
                }
                window.__globalPswpAttached = true;
                
                var checkState = function() {
                    var isOpen = pswp.classList.contains('pswp--open') || 
                                 pswp.classList.contains('pswp--visible') || 
                                 (getComputedStyle(pswp).display !== 'none' && getComputedStyle(pswp).opacity > 0);
                    if (window.__yamiboPswpIsOpen) isOpen = window.__yamiboPswpIsOpen(pswp);
                    if (window.__yamiboPswpCleanupClosed) window.__yamiboPswpCleanupClosed(pswp);
                    if (window.__pswpLastState !== isOpen) {
                        window.__pswpLastState = isOpen;
                        if (window.AndroidFullscreen) window.AndroidFullscreen.notify(isOpen);
                        if (isOpen) {
                            setTimeout(function(){ window.dispatchEvent(new Event('resize')); }, 100);
                        }
                    }
                };
                
                var pswpObserver = new MutationObserver(checkState);
                pswpObserver.observe(pswp, { attributes: true, attributeFilter: ['class', 'style', 'aria-hidden'] });
                checkState();
            };
            window.__pswpInit();

            var rewriteHomeLink = function() {
                var homeLink = document.querySelector('.my a[href*="index.php"]');
                if (homeLink) {
                    homeLink.href = 'home.php?mod=space&do=profile&mycenter=1&mobile=2';
                }
            };
            var fixMineMessageBadge = function() {
                var style = document.getElementById('yamibo-mine-message-layout');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'yamibo-mine-message-layout';
                    (document.head || document.documentElement).appendChild(style);
                }
                style.textContent =
                    '.yamibo-mine-message-link,.yamibo-mine-message-item{white-space:nowrap!important;}' +
                    '.yamibo-mine-message-badge{display:inline-block!important;float:none!important;position:static!important;margin:0 0 0 4px!important;vertical-align:middle!important;white-space:nowrap!important;line-height:inherit!important;}';

                var links = document.querySelectorAll(
                    'a[href*="do=pm"],a[href*="do%3Dpm"],a[href*="do=notice"],a[href*="do%3Dnotice"]'
                );
                for (var i = 0; i < links.length; i++) {
                    var link = links[i];
                    link.classList.add('yamibo-mine-message-link');
                    var container = link.parentElement;
                    if (container) container.classList.add('yamibo-mine-message-item');

                    var scope = container || link;
                    var candidates = scope.querySelectorAll('em,span,i,b,strong');
                    for (var j = 0; j < candidates.length; j++) {
                        var candidate = candidates[j];
                        var text = String(candidate.textContent || '').replace(/\s+/g, '').toLowerCase();
                        var className = String(candidate.className || '').toLowerCase();
                        if (text === 'new' || className.indexOf('prompt_news') !== -1 || className === 'new') {
                            candidate.classList.add('yamibo-mine-message-badge');
                        }
                    }
                }
            };
            rewriteHomeLink();
            fixMineMessageBadge();
            if (!window._mineHomeLinkObserver) {
                window._mineHomeLinkObserver = new MutationObserver(function() {
                    rewriteHomeLink();
                    fixMineMessageBadge();
                });
                window._mineHomeLinkObserver.observe(document.body, { childList: true, subtree: true });
            }
            
            if (!window._backBtnFixed) {
                window._backBtnFixed = true;
                document.addEventListener('click', function(e) {
                    var target = e.target.closest ? e.target.closest('a[href*="history.back"], #hui-back') : null;
                    if (target) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (window.NativeMangaApi && window.NativeMangaApi.goBack) {
                            window.NativeMangaApi.goBack();
                        } else {
                            window.history.back();
                        }
                    }
                }, true);
            }
            var a = document.querySelector('.header h2 a');
            var isManga = false;
            if (a) {
                var t = a.innerText;
                isManga = t.indexOf('中文百合漫画区') !== -1 || 
                          t.indexOf('百合漫画图源区') !== -1;
            }
            if (isManga) {
                if (window._mangaClickInjected) return 'true';
                window._mangaClickInjected = true;
                
                var disablePhotoSwipe = function() {
                    var links = document.querySelectorAll('a[data-pswp-width], .img_one a, .message a, td.t_f a, .postmessage a');
                    for (var i = 0; i < links.length; i++) {
                        var aNode = links[i];
                        if (aNode.querySelector('img')) {
                            aNode.removeAttribute('data-pswp-width');
                            if (aNode.href && aNode.href.indexOf('javascript') === -1) {
                                aNode.setAttribute('data-disabled-href', aNode.href);
                                aNode.removeAttribute('href');
                            }
                        }
                    }
                };
                disablePhotoSwipe();
                var observer = new MutationObserver(disablePhotoSwipe);
                observer.observe(document.body, { childList: true, subtree: true });
                
                document.addEventListener('click', function(e) {
                    var targetContainer = e.target.closest('.img_one li, .img_one a, .message a, td.t_f a, .postmessage a, .img_one img, .message img, td.t_f img, .postmessage img');
                    if (!targetContainer) return;
                    
                    var targetImg = targetContainer.tagName.toLowerCase() === 'img' ? targetContainer : targetContainer.querySelector('img');
                    
                    if (targetImg) {
                        var targetRawSrc = targetImg.getAttribute('zsrc') ||
                            targetImg.getAttribute('data-src') ||
                            targetImg.getAttribute('zoomfile') ||
                            targetImg.getAttribute('file') ||
                            targetImg.getAttribute('src') || '';
                        
                        if (targetRawSrc.indexOf('smiley') === -1) {
                            e.preventDefault(); 
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                            
                            var allImgs = document.querySelectorAll('.img_one img, .message img:not([src*="smiley"]), td.t_f img:not([src*="smiley"]), .postmessage img:not([src*="smiley"])');
                            var urls = [];
                            var clickedIndex = 0;
                            for (var i = 0; i < allImgs.length; i++) {
                                var rawSrc = allImgs[i].getAttribute('zsrc') ||
                                    allImgs[i].getAttribute('data-src') ||
                                    allImgs[i].getAttribute('zoomfile') ||
                                    allImgs[i].getAttribute('file') ||
                                    allImgs[i].getAttribute('src');
                                if (rawSrc) {
                                    var absoluteUrl = new URL(rawSrc, document.baseURI).href;
                                    urls.push(absoluteUrl);
                                    if (allImgs[i] === targetImg) clickedIndex = urls.length - 1;
                                }
                            }
                            if (window.NativeMangaApi) {
                                window.NativeMangaApi.openNativeManga(urls.join('|||'), clickedIndex, document.title);
                            }
                        }
                    }
                }, true); 
            }
            return isManga ? 'true' : 'false';
        })()
    """.trimIndent()


    // MangaWebPage脚本

    val MANGA_WEB_HIDE_COMMAND = """
        javascript:(function() {
            var style = document.createElement('style');
            style.innerHTML = '.mz { visibility: hidden !important; pointer-events: none !important; } .nav-search { display: none !important; }';
            if (document.head) document.head.appendChild(style);
        })()
    """.trimIndent()

    val MANGA_WEB_AUTO_OPEN_JS = """
        (function() {
            // 版块与公告检查
            var sectionHeader = document.querySelector('.header h2 a');
            var sectionName = sectionHeader ? sectionHeader.innerText.trim() : '';
            if (sectionName !== '') {
                var allowedSections = ['中文百合漫画区', '百合漫画图源区'];
                var isAllowedSection = false;
                for (var k = 0; k < allowedSections.length; k++) {
                    if (sectionName.indexOf(allowedSections[k]) !== -1) { isAllowedSection = true; break; }
                }
                if (!isAllowedSection) {
                    if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) window.AndroidFullscreen.notifyMangaActionDone();
                    return;
                }
            }
            
            var typeLabel = document.querySelector('.view_tit em');
            if (typeLabel && typeLabel.innerText.indexOf('公告') !== -1) {
                if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) window.AndroidFullscreen.notifyMangaActionDone();
                return; 
            }

            // 提取图片
            function extractAndOpenNative() {
                if (!window.NativeMangaApi) return false;
                
                var allImgs = document.querySelectorAll('.img_one img, .message img:not([src*="smiley"]), td.t_f img:not([src*="smiley"]), .postmessage img:not([src*="smiley"])');
                if (allImgs.length === 0) return false;
                
                var urls = [];
                for (var i = 0; i < allImgs.length; i++) {
                    var rawSrc = allImgs[i].getAttribute('zsrc') ||
                        allImgs[i].getAttribute('data-src') ||
                        allImgs[i].getAttribute('zoomfile') ||
                        allImgs[i].getAttribute('file') ||
                        allImgs[i].getAttribute('src');
                    if (rawSrc) urls.push(new URL(rawSrc, document.baseURI).href);
                }
                
                if (urls.length > 0) {
                    window.NativeMangaApi.openNativeManga(urls.join('|||'), 0, document.title);
                    return true;
                }
                return false;
            }

            if (extractAndOpenNative()) {
                return; // 如果第一次就成功了，直接结束
            }

            var extractAttempts = 0;
            var maxExtracts = 10;
            
            var extractTimer = setInterval(function() {
                extractAttempts++;
                
                if (extractAndOpenNative()) {
                    clearInterval(extractTimer);
                    return;
                }
                
                if (extractAttempts >= maxExtracts) {
                    clearInterval(extractTimer);
                    if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) {
                        window.AndroidFullscreen.notifyMangaActionDone();
                    }
                }
            }, 250);

        })();
    """.trimIndent()


    // OtherWebPage脚本

    val OTHER_WEB_HIDE_COMMAND = """
        (function() {
            var style = document.createElement('style');
            style.innerHTML = '.mz { visibility: hidden !important; pointer-events: none !important; } .nav-search { display: none !important; }';
            if (document.head) document.head.appendChild(style);
        })()
    """.trimIndent()

    val OTHER_WEB_INIT_PSWP_JS = """
        (function(){
            $PSWP_STATE_SYNC_JS
            window.__pswpInit = function() {
                if (window.__globalPswpAttached) return;
                var pswp = document.querySelector('.pswp');
                if (!pswp) {
                    if (window.__pswpWaitingObserverAttached) return;
                    window.__pswpWaitingObserverAttached = true;
                    var bodyObserver = new MutationObserver(function(mutations, obs) {
                        if (document.querySelector('.pswp')) {
                            obs.disconnect();
                            window.__pswpWaitingObserverAttached = false;
                            window.__pswpInit();
                        }
                    });
                    bodyObserver.observe(document.body, { childList: true, subtree: true });
                    return;
                }
                window.__globalPswpAttached = true;
                
                var checkState = function() {
                    var isOpen = pswp.classList.contains('pswp--open') || 
                                 pswp.classList.contains('pswp--visible') || 
                                 (getComputedStyle(pswp).display !== 'none' && getComputedStyle(pswp).opacity > 0);
                    if (window.__yamiboPswpIsOpen) isOpen = window.__yamiboPswpIsOpen(pswp);
                    if (window.__yamiboPswpCleanupClosed) window.__yamiboPswpCleanupClosed(pswp);
                    if (window.__pswpLastState !== isOpen) {
                        window.__pswpLastState = isOpen;
                        if (window.AndroidFullscreen) window.AndroidFullscreen.notify(isOpen);
                        if (isOpen) {
                            setTimeout(function(){ window.dispatchEvent(new Event('resize')); }, 100);
                        }
                    }
                };
                
                var pswpObserver = new MutationObserver(checkState);
                pswpObserver.observe(pswp, { attributes: true, attributeFilter: ['class', 'style', 'aria-hidden'] });
                checkState();
            };
            window.__pswpInit();
        })()
    """.trimIndent()

    val OTHER_WEB_CHECK_TYPE_JS = """
        (function() {
            var sectionHeader = document.querySelector('.header h2 a');
            var sectionName = sectionHeader ? sectionHeader.innerText.trim() : '';
            var currentUrl = window.location.href;
            var mangaSections = ['中文百合漫画区', '百合漫画图源区'];
            var isManga = mangaSections.some(function(s) { return sectionName.indexOf(s) !== -1; }) || currentUrl.indexOf('fid=30') !== -1 || currentUrl.indexOf('fid=37') !== -1;
            var novelSections = ['文學區', '文学区', '轻小说/译文区', 'TXT小说区'];
            var isNovel = novelSections.some(function(s) { return sectionName.indexOf(s) !== -1; }) || currentUrl.indexOf('fid=49') !== -1 || currentUrl.indexOf('fid=55') !== -1 || currentUrl.indexOf('fid=60') !== -1;
            if (isNovel) return 1;
            if (isManga) return 2;
            return 3;
        })();
    """.trimIndent()

    val OTHER_WEB_AUTO_OPEN_JS = """
        (function() {
            var sectionHeader = document.querySelector('.header h2 a');
            var sectionName = sectionHeader ? sectionHeader.innerText.trim() : '';
            if (sectionName !== '') {
                var allowedSections = ['中文百合漫画区', '百合漫画图源区'];
                var isAllowedSection = false;
                for (var k = 0; k < allowedSections.length; k++) {
                    if (sectionName.indexOf(allowedSections[k]) !== -1) { isAllowedSection = true; break; }
                }
                if (!isAllowedSection) {
                    if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) window.AndroidFullscreen.notifyMangaActionDone();
                    return;
                }
            }
            
            var typeLabel = document.querySelector('.view_tit em');
            if (typeLabel && typeLabel.innerText.indexOf('公告') !== -1) {
                if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) window.AndroidFullscreen.notifyMangaActionDone();
                return; 
            }
            
            if (!document.getElementById('manga-transition-style')) {
                var style = document.createElement('style');
                style.id = 'manga-transition-style';
                style.innerHTML = 'body > *:not(.pswp) { opacity: 0 !important; pointer-events: none !important; } body { background: #000 !important; }';
                document.head.appendChild(style);
            }

            function abortAndNotify() {
                var style = document.getElementById('manga-transition-style');
                if (style) style.remove();
                if (window.AndroidFullscreen && window.AndroidFullscreen.notifyMangaActionDone) window.AndroidFullscreen.notifyMangaActionDone();
            }

            var isDone = false;
            var attempts = 0;
            var timer = setInterval(function() {
                if (isDone) { clearInterval(timer); return; }
                attempts++;

                var pswp = document.querySelector('.pswp');
                if (pswp) {
                    isDone = true;
                    clearInterval(timer);
                    if (window.AndroidFullscreen) {
                        window.AndroidFullscreen.notify(true);
                        window.AndroidFullscreen.notifyMangaActionDone();
                    }
                    return;
                }

                var links = document.querySelectorAll('a[data-pswp-width], .img_one a.orange, .message a.orange, td.t_f a.orange, .postmessage a.orange');
                var targetEl = null;
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href') || '';
                    var innerHtml = links[i].innerHTML || '';
                    if (href.toLowerCase().indexOf('.gif') === -1 && href.indexOf('static/image/') === -1 && innerHtml.indexOf('static/image/') === -1) {
                        targetEl = links[i]; break;
                    }
                }
                
                if (targetEl) targetEl.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                if (attempts >= 25) { isDone = true; clearInterval(timer); abortAndNotify(); }
            }, 200);
        })();
    """.trimIndent()

    fun getDarkModeSetJs(enable: Boolean, themeId: Int = 0): String {
        val rulesList = DARK_MODE_CSS_RULES_CLASSIC
        val memberSpaceUrlExpression = MemberSpaceGuard.jsExpression()
        // 规则会被嵌进 JS 单引号字符串：必须转义反斜杠和单引号，
        // 否则任何一条带 ' 的规则都会让整段注入脚本语法错误，暗黑切换静默失效。
        val styleString = rulesList.joinToString(",\n") {
            "                '${it.replace("\\", "\\\\").replace("'", "\\'")}'"
        }

        return """
            (function() {
                var styleId = 'yamibo-dark-mode';
                var existing = document.getElementById(styleId);
                // 只有「自定义 DIY 会员空间」（body#space 且用了 data/attachment 自定义背景图）
                // 才不启用暗黑，以保留作者亲自设计的版面；普通空间（无自定义背景）以及其它所有
                // 页面都照常变深色。
                var isMemberSpace = ($memberSpaceUrlExpression);
                var enable = $enable && !isMemberSpace;

                function yamiboUseResponsiveSpaceViewport() {
                    if (!document.body) return;
                    var cls = document.body.className || '';
                    var isSpaceShell = document.body.id === 'space' || /\bpg_space(?:cp)?\b/.test(cls);
                    if (!isSpaceShell) return;
                    if (/\bpg_space\b/.test(cls) && document.querySelector('#ct.ct2_a .tl')) return;
                    // ct3_a（spacecp / BLOG 个人主页）走 HTML 代理写好的 width=1200 缩放，运行时别再改回 device-width。
                    if (document.querySelector('#ct.ct3_a')) return;
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        (document.head || document.documentElement).appendChild(meta);
                    }
                    meta.setAttribute('content', 'width=device-width, user-scalable=yes');
                }

                // 富文本编辑器（参与/回复主题）正文是独立的同源 iframe 文档，
                // 主文档注入的 CSS 够不到它，需要单独往其 contentDocument 注入深色样式。
                // 只在发帖/回复页（mod=post）处理，避免在其它页面空转。
                function yamiboStyleEditorFrames(applyDark) {
                    if (location.href.indexOf('mod=post') < 0) return;
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        try {
                            var doc = frames[i].contentDocument;
                            if (!doc || !doc.body) continue;
                            var editable = doc.body.isContentEditable ||
                                doc.body.getAttribute('contenteditable') === 'true';
                            if (!editable) continue;
                            var sid = 'yamibo-dark-editor';
                            var ex = doc.getElementById(sid);
                            if (ex) ex.remove();
                            if (!applyDark) continue;
                            var st = doc.createElement('style');
                            st.id = sid;
                            st.innerHTML = 'html,body{background:#182332 !important;color:#c7d8ea !important;}a{color:#7dbdf2 !important;}';
                            (doc.head || doc.body || doc.documentElement).appendChild(st);
                        } catch (e) {}
                    }
                }
                // 编辑器在页面就绪后才初始化 iframe，做几次延迟重试以捕捉到它。
                if (location.href.indexOf('mod=post') >= 0 && !window.__yamiboEditorThemeHooked) {
                    window.__yamiboEditorThemeHooked = true;
                    var yamiboEditorTries = 0;
                    var yamiboEditorTimer = setInterval(function() {
                        yamiboStyleEditorFrames(window.__yamiboEditorDark === true);
                        if (++yamiboEditorTries > 20) clearInterval(yamiboEditorTimer);
                    }, 300);
                }

                if (!enable) {
                    if (existing) existing.remove();
                    window.__yamiboEditorDark = false;
                    yamiboStyleEditorFrames(false);
                    return;
                }
                yamiboUseResponsiveSpaceViewport();
                if (existing) existing.remove();
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = [
$styleString
                ].join('\n');
                (document.body || document.documentElement).appendChild(style);
                window.__yamiboEditorDark = true;
                yamiboStyleEditorFrames(true);
            })();
        """.trimIndent()
    }

    fun getLightModeSetJs(enable: Boolean, themeId: Int = 0): String {
        val styleString = LIGHT_MODE_CSS_RULES_CLASSIC.joinToString(",\n") {
            "                '${it.replace("\\", "\\\\").replace("'", "\\'")}'"
        }
        val memberSpaceUrlExpression = MemberSpaceGuard.jsExpression()
        return """
            (function() {
                var styleId = 'yamibo-light-mode';
                var existing = document.getElementById(styleId);
                // 只有自定义 DIY 会员空间保持原样，不做链接统一；普通空间照常处理。
                var isMemberSpace = ($memberSpaceUrlExpression);
                var enable = $enable && !isMemberSpace;
                if (!enable) {
                    if (existing) existing.remove();
                    return;
                }
                if (existing) existing.remove();
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = [
$styleString
                ].join('\n');
                (document.body || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
    }

    fun injectDarkModeCssIntoHtml(html: String, themeId: Int = 0): String {
        // 会员 DIY 空间模板的兜底守卫：URL 判不出来的场合按 HTML 内容判断，
        // body#space（个人主页/日志/相册）保持会员自己的背景和配色。
        if (MemberSpaceGuard.isMemberSpaceHtml(html)) return html
        val rulesList = DARK_MODE_CSS_RULES_CLASSIC
        val css = rulesList.joinToString("\n")
        val styleTag = "<style id=\"yamibo-dark-mode\">\n$css\n</style>"
        return when {
            html.contains("</head>") -> html.replace("</head>", "$styleTag</head>")
            html.contains("<head>") -> html.replace("<head>", "<head>$styleTag")
            html.contains("<html>") -> html.replace("<html>", "<html><head>$styleTag</head>")
            html.contains("<body") -> html.replace("<body", "$styleTag<body")
            else -> "$styleTag$html"
        }
    }

    fun injectLightModeCssIntoHtml(html: String, themeId: Int = 0): String {
        if (MemberSpaceGuard.isMemberSpaceHtml(html)) return html
        val css = LIGHT_MODE_CSS_RULES_CLASSIC.joinToString("\n")
        val styleTag = "<style id=\"yamibo-light-mode\">\n$css\n</style>"
        return when {
            html.contains("</head>") -> html.replace("</head>", "$styleTag</head>")
            html.contains("<head>") -> html.replace("<head>", "<head>$styleTag")
            html.contains("<html>") -> html.replace("<html>", "<html><head>$styleTag</head>")
            html.contains("<body") -> html.replace("<body", "$styleTag<body")
            else -> "$styleTag$html"
        }
    }

    fun getLanguageSetJs(mode: String, forceForumSwitch: Boolean = false): String {
        val normalizedMode = LanguageModeUtil.normalize(mode)
        val modeLiteral = jsStringLiteral(normalizedMode)
        return """
            (function() {
                var mode = $modeLiteral;
                var forceForumSwitch = $forceForumSwitch;
                var target = mode === 'zh-hant' ? 'traditional' : 'simplified';
                var targetFlag = mode === 'zh-hant' ? 'hant' : 'hans';
                var desktopPluginLang = mode === 'zh-hant' ? '3' : '0';
                var previousMode = '';

                function safeSetStorage(storage, key, value) {
                    try { storage.setItem(key, value); } catch (e) {}
                }

                function safeGetStorage(storage, key) {
                    try { return storage.getItem(key) || ''; } catch (e) { return ''; }
                }

                function safeSetCookie(name, value) {
                    try {
                        document.cookie = name + '=' + encodeURIComponent(value) + ';path=/;max-age=31536000;SameSite=Lax';
                    } catch (e) {}
                }

                function normalizeMode(value) {
                    value = String(value || '').toLowerCase();
                    if (value === 'zh-hant' || value === 'zh-tw' || value === 'zh-hk' || value === 'traditional' || value === 'hant' || value === '1' || value === '2' || value === '3') {
                        return 'zh-hant';
                    }
                    if (value === 'zh-hans' || value === 'zh-cn' || value === 'simplified' || value === 'hans' || value === '0' || value === '4' || value === 'none') {
                        return 'zh-hans';
                    }
                    return '';
                }

                function readPreviousPreference() {
                    var fromStorage = safeGetStorage(localStorage, 'yamibo-language-mode') ||
                        safeGetStorage(localStorage, 'yamibo_language_mode') ||
                        safeGetStorage(localStorage, 'yami_opencc_lang') ||
                        safeGetStorage(localStorage, 'yamiOpenCCMode') ||
                        safeGetStorage(localStorage, 'yamiOpenCC');
                    var fromDocument = document.documentElement
                        ? document.documentElement.getAttribute('data-yamibo-language-mode')
                        : '';
                    return normalizeMode(fromStorage) || normalizeMode(fromDocument);
                }

                function persistPreference() {
                    safeSetStorage(localStorage, 'yamibo-language-mode', mode);
                    safeSetStorage(localStorage, 'yamibo_language_mode', mode);
                    safeSetStorage(localStorage, 'yami_opencc_lang', desktopPluginLang);
                    safeSetStorage(localStorage, 'yamiOpenCCMode', target);
                    safeSetStorage(localStorage, 'yamiOpenCC', targetFlag);
                    safeSetCookie('yamibo_language', mode);
                    safeSetCookie('yamibo_language_mode', mode);
                    safeSetCookie('yami_opencc_lang', desktopPluginLang);
                    safeSetCookie('yamiOpenCCMode', target);
                    safeSetCookie('yamiOpenCC', targetFlag);
                    if (document.documentElement) {
                        document.documentElement.setAttribute('lang', mode === 'zh-hant' ? 'zh-Hant' : 'zh-Hans');
                        document.documentElement.setAttribute('data-yamibo-language-mode', mode);
                    }
                }

                function callDesktopOpenCcPlugin() {
                    try {
                        if (typeof window.yamiOpenCCSetBtnText === 'function') {
                            window.yamiOpenCCSetBtnText(parseInt(desktopPluginLang, 10));
                        }
                        if (typeof window.yamiOpenCCConvert === 'function') {
                            window.yamiOpenCCConvert();
                            return true;
                        }
                    } catch (e) {}
                    return false;
                }
                function callKnownOpenCcApi() {
                    if (callDesktopOpenCcPlugin()) return true;
                    try {
                        if (window.yamiOpenCC && typeof window.yamiOpenCC.setMode === 'function') {
                            window.yamiOpenCC.setMode(target);
                            return true;
                        }
                        if (window.yamiOpenCC && typeof window.yamiOpenCC.set === 'function') {
                            window.yamiOpenCC.set(target);
                            return true;
                        }
                        if (window.yamiOpenCC && typeof window.yamiOpenCC.convert === 'function') {
                            window.yamiOpenCC.convert(target);
                            return true;
                        }
                        if (window.OpenCC && typeof window.OpenCC === 'function' && window.__yamiboOpenCCTarget !== mode) {
                            window.__yamiboOpenCCTarget = mode;
                            return false;
                        }
                    } catch (e) {}
                    return false;
                }

                function countHits(text, chars) {
                    var score = 0;
                    for (var i = 0; i < chars.length; i++) {
                        var ch = chars.charAt(i);
                        var index = text.indexOf(ch);
                        while (index >= 0) {
                            score++;
                            index = text.indexOf(ch, index + 1);
                        }
                    }
                    return score;
                }

                function currentModeFromPageText() {
                    var body = document.body;
                    if (!body) return '';
                    var text = (body.innerText || body.textContent || '').slice(0, 8000);
                    var tradScore = countHits(text, '頁個後這發現閱讀讀設關閉檢查積戶間簡體轉換臺灣與為應過網歡請們會區舊開體寫獎貼樓軟體預設');
                    var simpScore = countHits(text, '页个后这发现阅读读设关闭检查积户间简体转换台湾与为应过网欢请们会区旧开体写奖贴楼软件预设');
                    if (tradScore >= simpScore + 2) return 'zh-hant';
                    if (simpScore >= tradScore + 2) return 'zh-hans';
                    return '';
                }

                function triggerForumSwitch(baseUrl) {
                    var link = document.querySelector('a[href*="#yamiOpenCCSW"], a[href*="yamiOpenCCSW"], #mn_N520f a');
                    if (link && typeof link.click === 'function') {
                        link.click();
                        return true;
                    }
                    try {
                        var oldHash = location.hash;
                        location.hash = 'yamiOpenCCSW';
                        if (typeof HashChangeEvent === 'function') {
                            window.dispatchEvent(new HashChangeEvent('hashchange'));
                        } else {
                            window.dispatchEvent(new Event('hashchange'));
                        }
                        setTimeout(function() {
                            var restoreUrl = oldHash && oldHash !== '#yamiOpenCCSW' ? baseUrl + oldHash : baseUrl;
                            history.replaceState(null, document.title, restoreUrl);
                        }, 80);
                        return true;
                    } catch (e) {}
                    return false;
                }

                function switchByForumEntryIfNeeded() {
                    var current = currentModeFromPageText();
                    if (current === mode) return;
                    var shouldForce = forceForumSwitch && previousMode && previousMode !== mode;
                    if (!current && !shouldForce) return;
                    var baseUrl = String(location.href || '').split('#')[0];
                    var sessionKey = '__yamiboLanguageSwitch:' + baseUrl;
                    try {
                        if (sessionStorage.getItem(sessionKey) === mode) return;
                    } catch (e) {}
                    if (triggerForumSwitch(baseUrl)) {
                        try { sessionStorage.setItem(sessionKey, mode); } catch (e) {}
                    }
                }
                previousMode = readPreviousPreference();
                persistPreference();
                if (!callKnownOpenCcApi()) {
                    setTimeout(switchByForumEntryIfNeeded, forceForumSwitch ? 40 : 120);
                    setTimeout(switchByForumEntryIfNeeded, forceForumSwitch ? 360 : 700);
                }
            })();
        """.trimIndent()
    }

    fun getThemeSetJs(isDark: Boolean, darkThemeId: Int, lightThemeId: Int): String {
        val darkJs = getDarkModeSetJs(isDark, darkThemeId)
        val lightJs = getLightModeSetJs(!isDark, lightThemeId)
        return "$darkJs\n$lightJs"
    }

    fun calculateDesktopFitScale(widthPx: Int, density: Float): Double {
        if (widthPx <= 0 || density <= 0f) return 0.0
        val widthDp = widthPx / density
        return if (widthDp > 0f) (widthDp / DESKTOP_LAYOUT_WIDTH_DP).toDouble() else 0.0
    }

    /**
     * @param desktopFitScale 电脑版页面要写入 viewport 的 initial-scale（把 1200px 布局缩放到屏宽）。
     *   传 0 表示不写显式缩放、退回 loadWithOverviewMode 自动缩放。由调用方按实际屏宽算出（屏宽dp / 1200）。
     */
    fun injectThemeCssIntoHtml(
        html: String,
        isDark: Boolean,
        darkThemeId: Int,
        lightThemeId: Int,
        desktopFitScale: Double = 0.0
    ): String {
        val compacted = compactMobileForumPageSelector(html)
        val fixed = applyDesktopViewportForWebView(compacted, desktopFitScale)
        return when {
            isDark -> injectDarkModeCssIntoHtml(fixed, darkThemeId)
            else -> injectLightModeCssIntoHtml(fixed, lightThemeId)
        }
    }

    private const val MOBILE_PAGE_OPTION_RADIUS = 40
    private val DUMP_PAGE_SELECT_REGEX = Regex(
        """<select\b(?=[^>]*\bid\s*=\s*(?:\"dumppage\"|'dumppage'|dumppage)(?=[\s>]))[^>]*>.*?</select>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val PAGE_OPTION_REGEX = Regex(
        """<option\b[^>]*>.*?</option>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val SELECTED_OPTION_REGEX = Regex(
        """\bselected(?:\s*=\s*(?:\"selected\"|'selected'|selected))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Discuz 手机版会把大区的每一页都展开成 `#dumppage` 的一个 option。海域区有三千多页，
     * 单是这个 select 就占十几万字符和数千 DOM 节点，会显著放大 Android WebView 渲染进程压力。
     *
     * 保留当前页前后各 40 页以及首末页，上一页/下一页仍由站点原按钮负责；普通短列表保持原样。
     * 该处理发生在 HTML 交给 WebView 解析前，避免先创建完整 DOM 后再用 JavaScript 删除已经太晚。
     */
    fun compactMobileForumPageSelector(html: String): String {
        return DUMP_PAGE_SELECT_REGEX.replace(html) { selectMatch ->
            val selectHtml = selectMatch.value
            val options = PAGE_OPTION_REGEX.findAll(selectHtml).toList()
            if (options.size <= MOBILE_PAGE_OPTION_RADIUS * 2 + 3) {
                return@replace selectHtml
            }

            val selectedIndex = options.indexOfFirst {
                SELECTED_OPTION_REGEX.containsMatchIn(it.value.substringBefore('>'))
            }.takeIf { it >= 0 } ?: 0
            val firstKept = (selectedIndex - MOBILE_PAGE_OPTION_RADIUS).coerceAtLeast(0)
            val lastKept = (selectedIndex + MOBILE_PAGE_OPTION_RADIUS).coerceAtMost(options.lastIndex)
            val keptIndexes = buildSet {
                add(0)
                add(options.lastIndex)
                for (index in firstKept..lastKept) add(index)
            }

            val firstOptionStart = options.first().range.first
            val lastOptionEnd = options.last().range.last + 1
            buildString(selectHtml.length.coerceAtMost(16_384)) {
                append(selectHtml, 0, firstOptionStart)
                keptIndexes.sorted().forEach { index -> append(options[index].value) }
                append(selectHtml, lastOptionEnd, selectHtml.length)
            }
        }
    }

    private const val DESKTOP_LAYOUT_WIDTH_DP = 1200f

    private val VIEWPORT_META_REGEX =
        Regex("<meta[^>]*name=[\"']viewport[\"'][^>]*>", RegexOption.IGNORE_CASE)
    private val HEAD_OPEN_REGEX = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val TOPTB_ID_REGEX =
        Regex("""\bid\s*=\s*(?:"toptb"|'toptb'|toptb)(?=[\s>])""", RegexOption.IGNORE_CASE)
    private val BODY_SPACE_ID_REGEX =
        Regex("""<body\b[^>]*\bid\s*=\s*(?:"space"|'space'|space)(?=[\s>])""", RegexOption.IGNORE_CASE)
    private val BODY_PG_SPACE_CLASS_REGEX =
        Regex("""<body\b[^>]*\bclass\s*=\s*(["'])[^"']*\bpg_space(?:cp)?\b[^"']*\1""", RegexOption.IGNORE_CASE)
    private val CT2_A_CONTAINER_REGEX =
        Regex("""<div\b[^>]*\bid\s*=\s*(?:"ct"|'ct'|ct)(?=[\s>])[^>]*\bclass\s*=\s*(["'])[^"']*\bct2_a\b[^"']*\1""", RegexOption.IGNORE_CASE)
    private val CT3_A_CONTAINER_REGEX =
        Regex("""<div\b[^>]*\bid\s*=\s*(?:"ct"|'ct'|ct)(?=[\s>])[^>]*\bclass\s*=\s*(["'])[^"']*\bct3_a\b[^"']*\1""", RegexOption.IGNORE_CASE)
    private val THREAD_LIST_CONTAINER_REGEX =
        Regex("""<div\b[^>]*\bclass\s*=\s*(["'])[^"']*\btl\b[^"']*\1""", RegexOption.IGNORE_CASE)

    private fun shouldUseResponsiveSpaceViewport(html: String): Boolean {
        if (MemberSpaceGuard.isMemberSpaceHtml(html)) return false
        // spacecp / 个人主页 BLOG 等 #ct.ct3_a 页是「.appl + .mn(固定 775px float) + .sd(固定 220px float)」
        // 的定宽多栏布局。device-width 下 .mn 溢出、侧栏摞到主内容上（暗黑下表现为单栏堆叠，与原色不一致）。
        // 这类页改走 width=1200 + initial-scale 的确定性缩放：两栏并排(775+220<1200)整页缩到屏宽，与原色一致。
        if (CT3_A_CONTAINER_REGEX.containsMatchIn(html)) return false
        if (
            BODY_PG_SPACE_CLASS_REGEX.containsMatchIn(html) &&
            CT2_A_CONTAINER_REGEX.containsMatchIn(html) &&
            THREAD_LIST_CONTAINER_REGEX.containsMatchIn(html)
        ) {
            return false
        }
        return BODY_SPACE_ID_REGEX.containsMatchIn(html) || BODY_PG_SPACE_CLASS_REGEX.containsMatchIn(html)
    }

    /**
     * 电脑版页面（Discuz 桌面模板，含 id="toptb"）的 body 是 min-width:1200px、.wp 固定 1200px 的宽版布局，
     * 但服务器仍下发 width=device-width 的 viewport meta。该 meta 会让 WebView 以 1.0 缩放在窄屏渲染 1200px 布局，
     * 导致右侧（含浮动的提交按钮等）被挤出屏幕看不到——而浏览器窗口够宽所以正常。
     * 普通 BLOG/空间页（含个人空间首页、个人资料页）使用 width=device-width，但绝不能写死
     * initial-scale=1.0——这些页面的 .wp 仍是固定 1200px，并非真正响应式，靠 loadWithOverviewMode
     * 缩到屏宽（亮色模式正是用服务器自带的 width=device-width + overview 正常显示）。一旦写死
     * initial-scale=1.0 就会顶掉 overview 缩放，导致暗黑下 1200px 版面 1:1 溢出、右侧被裁切。
     * 其它电脑版页面改成 width=1200，并写入按屏宽算出的 initial-scale，把整页确定性地缩放到屏宽。
     * 手机版页面没有 id="toptb"，保持原样。
     */
    fun applyDesktopViewportForWebView(html: String, desktopFitScale: Double = 0.0): String {
        if (!TOPTB_ID_REGEX.containsMatchIn(html)) return html
        val content = if (shouldUseResponsiveSpaceViewport(html)) {
            "width=device-width, user-scalable=yes"
        } else if (desktopFitScale > 0.0) {
            val scale = String.format(java.util.Locale.US, "%.3f", desktopFitScale)
            "width=1200, initial-scale=$scale, user-scalable=yes"
        } else {
            "width=1200, user-scalable=yes"
        }
        val viewportTag = "<meta name=\"viewport\" content=\"$content\">"
        if (VIEWPORT_META_REGEX.containsMatchIn(html)) {
            return VIEWPORT_META_REGEX.replace(html, viewportTag)
        }
        val headMatch = HEAD_OPEN_REGEX.find(html)
        if (headMatch != null) {
            return html.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, viewportTag)
        }
        return viewportTag + html
    }

    fun getForumBlockerJs(enabled: Boolean, itemsJson: String, isDark: Boolean, selfUid: String = ""): String {
        val itemsLiteral = jsStringLiteral(itemsJson)
        val selfUidLiteral = jsStringLiteral(selfUid)
        return """
            (function() {
                var incomingItems = [];
                try { incomingItems = JSON.parse($itemsLiteral); } catch (e) {}

                if (!window.__yamiboForumBlocker) {
                    var state = {
                        enabled: true,
                        dark: false,
                        items: [],
                        syncing: false,
                        timer: null,
                        currentUid: null,
                        nextAuxInstance: 1
                    };

                    function itemKey(type, id) {
                        return String(type) + ':' + String(id);
                    }

                    function blockedMap() {
                        var map = {};
                        for (var i = 0; i < state.items.length; i++) {
                            var item = state.items[i] || {};
                            map[itemKey(item.type, item.id)] = item;
                        }
                        return map;
                    }

                    function restoreElement(element) {
                        if (!element || element.getAttribute('data-yamibo-block-hidden') !== '1') return;
                        var oldDisplay = element.getAttribute('data-yamibo-old-display');
                        element.style.display = oldDisplay || '';
                        element.removeAttribute('data-yamibo-old-display');
                        element.removeAttribute('data-yamibo-block-hidden');
                        element.removeAttribute('data-yamibo-block-kind');
                        element.removeAttribute('data-yamibo-block-aux-instance');
                    }

                    function hideElement(element) {
                        if (!element || element.getAttribute('data-yamibo-block-hidden') === '1') return;
                        element.setAttribute('data-yamibo-old-display', element.style.display || '');
                        element.setAttribute('data-yamibo-block-hidden', '1');
                        element.style.setProperty('display', 'none', 'important');
                    }

                    function cleanup() {
                        var hidden = document.querySelectorAll('[data-yamibo-block-hidden="1"]');
                        for (var i = 0; i < hidden.length; i++) restoreElement(hidden[i]);
                        var generated = document.querySelectorAll('.yamibo-block-action, .yamibo-block-li, .yamibo-blocked-message, .yamibo-block-choice-backdrop, .yamibo-blocked-inline, [data-yamibo-block-aux]');
                        for (var j = 0; j < generated.length; j++) generated[j].remove();
                    }

                    function ensureStyle() {
                        var style = document.getElementById('yamibo-forum-blocker-style');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = 'yamibo-forum-blocker-style';
                            (document.head || document.documentElement).appendChild(style);
                        }
                        var background = state.dark ? '#182332' : '#f5f2ea';
                        var border = state.dark ? '#2b4058' : '#ded6c7';
                        var text = state.dark ? '#b8c6d8' : '#66605a';
                        var menuBackground = state.dark ? '#182332' : '#fffbe7';
                        var menuText = state.dark ? '#edf4fb' : '#4f2418';
                        var menuMuted = state.dark ? '#95acc4' : '#7b6259';
                        var menuButton = state.dark ? '#223247' : '#f5ead2';
                        var menuPrimary = state.dark ? '#31577a' : '#6e2b19';
                        var linkColor = state.dark ? '#7dbdf2' : '#6e2b19';
                        style.textContent =
                            // 基础重置只作用于按钮本身的 <a>，保持极简，让它在不同容器里自然继承排版。
                            'a.yamibo-block-action{background:transparent!important;border:0!important;box-shadow:none!important;font:inherit!important;text-decoration:none!important;cursor:pointer!important;}' +
                            // 列表页：容器 <li> 不加任何样式类，直接复用站点 .threadlist_foot li 的胶囊样式
                            // （float:left、内边距、圆角、深色边框），从而与浏览数/回复数按钮完全对齐。
                            // 内部 <a> 保持纯 inline（不要用 inline-flex，否则其原子盒在 baseline 上会撑高行盒，
                            // 使屏蔽按钮比浏览/评论按钮高）；SVG 图标按站点原生 .threadlist_foot li i 的 float:left
                            // 方式排版，14px 图标在 22px 行高中用 4px 上边距居中，和文字同一条中线。
                            '.threadlist_foot li.yamibo-block-li a.yamibo-block-action{display:inline!important;padding:0!important;margin:0!important;line-height:inherit!important;vertical-align:baseline!important;color:inherit!important;}' +
                            '.threadlist_foot li.yamibo-block-li .yamibo-block-icon{float:left!important;display:block!important;width:14px!important;height:14px!important;line-height:1!important;margin:4px 3px 0 0!important;vertical-align:baseline!important;fill:currentColor!important;color:inherit!important;}' +
                            '.threadlist_foot li.yamibo-block-li .yamibo-block-label{display:inline!important;line-height:inherit!important;vertical-align:baseline!important;}' +
                            // 帖子页：按钮在用户名后内联显示，间距由前面插入的四个不可断空格决定，这里清零边距。
                            '.authi>.yamibo-block-action{display:inline!important;margin-left:0!important;padding-left:0!important;font-size:12px!important;font-weight:normal!important;}' +
                            '.yamibo-blocked-message{box-sizing:border-box;margin:8px 0;padding:10px 12px;text-align:center;border-radius:4px;background:' + background + '!important;border:1px solid ' + border + '!important;color:' + text + '!important;font-size:12px;line-height:1.7;}' +
                            '.threadlist>.yamibo-blocked-message{list-style:none;margin:8px 10px;}' +
                            '.yamibo-blocked-message a.yamibo-unblock-action,#ct .yamibo-blocked-message a.yamibo-unblock-action{font-size:12px!important;color:' + linkColor + '!important;}' +
                            '.yamibo-blocked-aux-row>td{padding:0!important;border:0!important;background:transparent!important;}' +
                            '.yamibo-blocked-aux-row .yamibo-blocked-message{margin:6px 0!important;}' +
                            '.yamibo-blocked-inline{color:' + text + '!important;font-size:inherit!important;font-weight:normal!important;}' +
                            '.yamibo-block-choice-backdrop{position:fixed!important;inset:0!important;z-index:2147483646!important;background:rgba(0,0,0,.42)!important;display:flex!important;align-items:flex-end!important;justify-content:center!important;padding:16px!important;box-sizing:border-box!important;}' +
                            '.yamibo-block-choice-menu{width:min(420px,100%)!important;background:' + menuBackground + '!important;color:' + menuText + '!important;border:1px solid ' + border + '!important;border-radius:8px!important;box-shadow:0 10px 32px rgba(0,0,0,.28)!important;padding:14px!important;box-sizing:border-box!important;}' +
                            '.yamibo-block-choice-title{font-size:16px!important;font-weight:600!important;line-height:24px!important;margin:0 0 4px!important;color:' + menuText + '!important;}' +
                            '.yamibo-block-choice-subtitle{font-size:12px!important;line-height:18px!important;margin:0 0 10px!important;color:' + menuMuted + '!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;}' +
                            '.yamibo-block-choice-actions{display:flex!important;flex-wrap:nowrap!important;gap:8px!important;margin:0!important;}' +
                            '.yamibo-block-choice-button{flex:1 1 0!important;min-width:0!important;height:38px!important;margin:0!important;padding:0 6px!important;border:1px solid ' + border + '!important;border-radius:6px!important;background:' + menuButton + '!important;background-image:none!important;color:' + menuText + '!important;box-shadow:none!important;text-shadow:none!important;font-size:13px!important;line-height:36px!important;text-align:center!important;white-space:nowrap!important;}' +
                            '.yamibo-block-choice-button-primary{background:' + menuPrimary + '!important;color:#fff!important;}' +
                            '.yamibo-block-choice-button-cancel{background:transparent!important;color:' + menuMuted + '!important;}';
                    }

                    function getTid(rawUrl) {
                        var value = String(rawUrl || '');
                        var match = value.match(/thread-(\d+)/i);
                        if (match) return match[1];
                        match = value.match(/[?&]tid=(\d+)/i);
                        return match ? match[1] : null;
                    }

                    function getCurrentTid() {
                        return getTid(location.href);
                    }

                    function getRowTid(row) {
                        var link = row.querySelector('a[href*="tid="], a[href*="thread-"], a[href*="viewthread"]');
                        return link ? getTid(link.href || link.getAttribute('href')) : null;
                    }

                    function getUidFromHref(href) {
                        var value = String(href || '');
                        var match = value.match(/space-uid-(\d+)/i);
                        if (match) return match[1];
                        match = value.match(/[?&]uid=(\d+)/i);
                        return match ? match[1] : null;
                    }

                    // 当前登录用户 uid：桌面版用 discuz_uid，手机版用底部「个人中心」链接(mycenter=1)。
                    function getCurrentUid() {
                        if (window.discuz_uid && /^[1-9]\d*$/.test(String(window.discuz_uid))) {
                            return String(window.discuz_uid);
                        }
                        var selfLink = document.querySelector('a[href*="mycenter=1"]');
                        if (selfLink) {
                            var uid = getUidFromHref(selfLink.getAttribute('href') || selfLink.href);
                            if (uid) return uid;
                        }
                        return null;
                    }

                    function getRowAuthorUid(row) {
                        var link = row.querySelector('a[href*="space-uid-"], a[href*="mod=space"]');
                        return link ? getUidFromHref(link.getAttribute('href') || link.href) : null;
                    }

                    function getPostAuthorUid(post) {
                        var auth = post.querySelector('.authi');
                        var link = auth ? auth.querySelector('a[href*="space-uid-"], a[href*="mod=space"]') : null;
                        return link ? getUidFromHref(link.getAttribute('href') || link.href) : null;
                    }

                    function isOwnUid(uid) {
                        return !!uid && !!state.currentUid && String(uid) === String(state.currentUid);
                    }

                    function normalizeUserName(value) {
                        return String(value || '').replace(/\u00a0/g, ' ').trim();
                    }

                    function getBlockedUser(map, uid, name) {
                        if (uid) {
                            var uidMatch = map[itemKey('user', uid)] || null;
                            if (uidMatch) return uidMatch;
                        }
                        var normalizedName = normalizeUserName(name);
                        if (!normalizedName) return null;
                        for (var i = 0; i < state.items.length; i++) {
                            var item = state.items[i] || {};
                            if (item.type !== 'user') continue;
                            var itemName = normalizeUserName(item.authorName || item.title);
                            if (itemName && itemName === normalizedName) return item;
                        }
                        return null;
                    }

                    function getBlockedUserFromLink(map, link, explicitName) {
                        if (!link) return null;
                        var anchor = link.closest ? link.closest('a') : null;
                        var uid = anchor
                            ? getUidFromHref(anchor.getAttribute('href') || anchor.href)
                            : null;
                        var name = explicitName || link.textContent || '';
                        return getBlockedUser(map, uid, name);
                    }

                    // 当前是不是“我自己”的空间列表页（我的主题/回复/收藏）。这类页面整页都是自己的内容，
                    // 不管行内有没有作者链接，都不应出现屏蔽按钮。
                    function isOwnSpaceListPage() {
                        var href = location.href || '';
                        if (/[?&]view=me(&|$)/i.test(href)) return true;
                        if (/mod=space/i.test(href) && /[?&]do=(thread|reply|favorite)/i.test(href)) {
                            var m = href.match(/[?&]uid=(\d+)/i);
                            if (!m) return true;
                            if (isOwnUid(m[1])) return true;
                        }
                        return false;
                    }

                    function getPostPid(post) {
                        var match = String(post.id || '').match(/(?:pid|post_)(\d+)/i);
                        if (match) return match[1];
                        var dataPid = post.getAttribute('data-pid');
                        if (dataPid && /^\d+$/.test(dataPid)) return dataPid;
                        var postNum = post.querySelector('[id^="postnum"]');
                        match = postNum ? String(postNum.id).match(/postnum(\d+)/i) : null;
                        return match ? match[1] : null;
                    }

                    function placeholderSelector(type, id, instanceId) {
                        var selector = '.yamibo-blocked-message[data-type="' + type + '"][data-id="' + id + '"]';
                        if (instanceId) selector += '[data-instance="' + instanceId + '"]';
                        return selector;
                    }

                    function ensurePlaceholder(target, type, id, label, instanceId) {
                        if (!target || !target.parentNode) return;
                        var selector = placeholderSelector(type, id, instanceId);
                        var existing = target.parentNode.querySelector(selector);
                        if (existing) return;
                        var message = document.createElement(
                            target.parentNode.tagName === 'UL' ? 'li' : 'div'
                        );
                        message.className = 'yamibo-blocked-message';
                        message.setAttribute('data-type', type);
                        message.setAttribute('data-id', id);
                        if (instanceId) message.setAttribute('data-instance', instanceId);
                        message.appendChild(document.createTextNode(label + '已被屏蔽 '));
                        var undo = document.createElement('a');
                        undo.href = 'javascript:;';
                        undo.className = 'xi2 yamibo-unblock-action';
                        undo.setAttribute('data-type', type);
                        undo.setAttribute('data-id', id);
                        undo.textContent = '取消屏蔽';
                        message.appendChild(undo);
                        target.parentNode.insertBefore(message, target);
                    }

                    function removePlaceholder(target, type, id, instanceId) {
                        if (!target || !target.parentNode) return;
                        var selector = placeholderSelector(type, id, instanceId);
                        var message = target.parentNode.querySelector(selector);
                        if (message) message.remove();
                    }

                    function appendUnblockAction(container, blockedUser, label) {
                        container.appendChild(document.createTextNode(label + '已被屏蔽 '));
                        var undo = document.createElement('a');
                        undo.href = 'javascript:;';
                        undo.className = 'xi2 yamibo-unblock-action';
                        undo.setAttribute('data-type', 'user');
                        undo.setAttribute('data-id', blockedUser.id);
                        undo.textContent = '取消屏蔽';
                        container.appendChild(undo);
                    }

                    function hideAuxiliaryContent(target, blockedUser, label) {
                        if (!target || !target.parentNode || !blockedUser || isOwnUid(blockedUser.id)) return;
                        if (target.getAttribute('data-yamibo-block-hidden') === '1') return;
                        hideElement(target);
                        target.setAttribute('data-yamibo-block-kind', 'aux');
                        var instance = 'aux-' + String(state.nextAuxInstance++);
                        target.setAttribute('data-yamibo-block-aux-instance', instance);

                        var wrapper;
                        var content;
                        if (target.tagName === 'TR') {
                            wrapper = document.createElement('tr');
                            wrapper.className = 'yamibo-blocked-aux-row';
                            var cell = document.createElement('td');
                            cell.colSpan = Math.max(target.children.length, 1);
                            content = document.createElement('div');
                            content.className = 'yamibo-blocked-message';
                            cell.appendChild(content);
                            wrapper.appendChild(cell);
                        } else {
                            wrapper = document.createElement(
                                target.parentNode.tagName === 'UL' ? 'li' : 'div'
                            );
                            wrapper.className = 'yamibo-blocked-message';
                            content = wrapper;
                        }
                        wrapper.setAttribute('data-yamibo-block-aux', '1');
                        wrapper.setAttribute('data-instance', instance);
                        appendUnblockAction(content, blockedUser, label);
                        target.parentNode.insertBefore(wrapper, target);
                    }

                    function hideBlockedUserName(target, blockedUser) {
                        if (!target || !target.parentNode || !blockedUser || isOwnUid(blockedUser.id)) return;
                        if (target.getAttribute('data-yamibo-block-hidden') === '1') return;
                        hideElement(target);
                        target.setAttribute('data-yamibo-block-kind', 'aux');
                        var instance = 'aux-' + String(state.nextAuxInstance++);
                        target.setAttribute('data-yamibo-block-aux-instance', instance);
                        var replacement = document.createElement('span');
                        replacement.className = 'yamibo-blocked-inline';
                        replacement.setAttribute('data-yamibo-block-aux', '1');
                        replacement.setAttribute('data-instance', instance);
                        replacement.textContent = '已屏蔽用户';
                        target.parentNode.insertBefore(replacement, target);
                    }

                    function restoreAuxiliaryContent(target) {
                        if (!target || target.getAttribute('data-yamibo-block-kind') !== 'aux') return;
                        var instance = target.getAttribute('data-yamibo-block-aux-instance');
                        var parent = target.parentNode;
                        restoreElement(target);
                        if (!instance || !parent) return;
                        var generated = parent.querySelector(
                            '[data-yamibo-block-aux][data-instance=' + instance + ']'
                        );
                        if (generated) generated.remove();
                    }

                    function makeAction(type, id, title, blocked, authorUid, authorName) {
                        var action = document.createElement('a');
                        action.href = 'javascript:;';
                        action.className = 'xi2 yamibo-block-action';
                        action.setAttribute('data-type', type);
                        action.setAttribute('data-id', id);
                        action.setAttribute('data-title', title || '');
                        action.setAttribute('data-author-uid', authorUid || '');
                        action.setAttribute('data-author-name', authorName || '');
                        action.textContent = blocked ? '取消屏蔽' : '屏蔽';
                        return action;
                    }

                    // 列表页（大区帖子列表）的屏蔽按钮加上闭眼图标，与旁边的浏览数(dm-eye-fill)、
                    // 评论数(dm-chat-s-fill)按钮保持一致；颜色和字号由 .threadlist_foot 继承。
                    // 帖子页内的屏蔽按钮不走这里，保持纯文字。
                    // 屏蔽后整行会被隐藏、由占位提示里的「取消屏蔽」撤销，所以这里固定只显示「屏蔽」。
                    function setListActionLabel(action) {
                        var icon = action.querySelector('.yamibo-block-icon');
                        var label = action.querySelector('.yamibo-block-label');
                        if (icon && label && label.textContent === '屏蔽') return;
                        action.innerHTML = '<svg class="yamibo-block-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z"></path></svg><span class="yamibo-block-label">屏蔽</span>';
                    }

                    // 从行/楼层里尽量取作者用户名（带文字的「空间链接」）。
                    function getAuthorName(scope) {
                        if (!scope) return '';
                        var links = scope.querySelectorAll('a[href*="space-uid-"], a[href*="mod=space"]');
                        for (var i = 0; i < links.length; i++) {
                            var text = String(links[i].textContent || '').trim();
                            if (text) return text;
                        }
                        return '';
                    }

                    function syncListPage(map) {
                        var rows = document.querySelectorAll('.threadlist li.list, .threadlist li.list_top');
                        // 我自己的空间列表页（我的主题/回复/收藏）整页都是自己的内容，不加屏蔽按钮，
                        // 并清掉历史上误加的，避免依赖行内作者链接（部分模板该页不带作者头像链接）。
                        var ownSpace = isOwnSpaceListPage();
                        for (var i = 0; i < rows.length; i++) {
                            var row = rows[i];
                            var tid = getRowTid(row);
                            if (!tid) continue;
                            if (ownSpace) {
                                var ownSpaceHolder = row.querySelector('.yamibo-block-li');
                                if (ownSpaceHolder) ownSpaceHolder.remove();
                                continue;
                            }
                            var authorUid = getRowAuthorUid(row);
                            var authorName = getAuthorName(row);
                            // 自己发布的主题不显示屏蔽按钮（也清掉历史上误加的）。
                            if (isOwnUid(authorUid)) {
                                var ownHolder = row.querySelector('.yamibo-block-li');
                                if (ownHolder) ownHolder.remove();
                                continue;
                            }
                            var key = itemKey('thread', tid);
                            var isBlocked = !!map[key];
                            var blockedUser = getBlockedUser(map, authorUid, authorName);
                            var action = row.querySelector('.yamibo-block-action[data-type="thread"]');
                            if (!action) {
                                var foot = row.querySelector('.threadlist_foot ul');
                                if (foot) {
                                    // 用普通的 foot <li> 作为容器，让屏蔽按钮与旁边的浏览/回复数
                                    // 按钮（同为 .threadlist_foot li）保持同样的对齐与外观，不再单独重置样式。
                                    var holder = document.createElement('li');
                                    holder.className = 'yamibo-block-li';
                                    var titleLink = row.querySelector('a[href*="tid="], a[href*="thread-"], a[href*="viewthread"]');
                                    // 标题只取 .threadlist_tit em：投票/悬赏/活动等特殊主题在标题前还有
                                    // 一个 .micon 徽标（如“投票”），直接取整个链接的 textContent 会把徽标
                                    // 文字和真实标题拼在一起，徽标与标题间的换行还会让展示层截断成只剩徽标。
                                    var titleEm = titleLink ? titleLink.querySelector('.threadlist_tit em') : null;
                                    var title = titleEm
                                        ? String(titleEm.textContent || '').trim()
                                        : (titleLink ? String(titleLink.textContent || '').trim() : '');
                                    action = makeAction('thread', tid, title, isBlocked, authorUid, authorName);
                                    setListActionLabel(action);
                                    holder.appendChild(action);
                                    foot.appendChild(holder);
                                }
                            } else {
                                setListActionLabel(action);
                            }

                            if (isBlocked) {
                                if (authorUid) removePlaceholder(row, 'user', authorUid, 'thread-' + tid);
                                hideElement(row);
                                ensurePlaceholder(row, 'thread', tid, '该主题');
                            } else if (blockedUser) {
                                removePlaceholder(row, 'thread', tid);
                                hideElement(row);
                                ensurePlaceholder(row, 'user', blockedUser.id, '该用户的主题', 'thread-' + tid);
                            } else {
                                restoreElement(row);
                                removePlaceholder(row, 'thread', tid);
                                if (authorUid) removePlaceholder(row, 'user', authorUid, 'thread-' + tid);
                            }
                        }
                    }

                    // 电脑版列表页（forumdisplay 的 #threadlisttableid 行、标签页 .tl 相关帖子表格）：
                    // 只做隐藏/恢复，不注入屏蔽按钮——按钮排版依赖手机版模板的 .threadlist_foot，
                    // 电脑版表格里没有安全的挂载点；取消屏蔽走黑名单弹窗或手机版页面。
                    // 占位提示也不加：占位元素插进 <table> 会被浏览器移出表格错位显示。
                    function syncPcListPage(map) {
                        if (isOwnSpaceListPage()) return;
                        var rows = document.querySelectorAll(
                            'tbody[id^="normalthread_"], tbody[id^="stickthread_"], #threadlist tbody[id], .tl tbody[id], body.pg_tag .tl table tr'
                        );
                        for (var i = 0; i < rows.length; i++) {
                            var row = rows[i];
                            var tid = getRowTid(row);
                            if (!tid) continue;
                            var authorUid = getRowAuthorUid(row);
                            var authorName = getAuthorName(row);
                            if (isOwnUid(authorUid)) continue;
                            var blocked = !!map[itemKey('thread', tid)] ||
                                !!getBlockedUser(map, authorUid, authorName);
                            if (blocked) {
                                hideElement(row);
                            } else {
                                restoreElement(row);
                            }
                        }
                    }

                    function syncPostPage(map) {
                        var currentTid = getCurrentTid();
                        var threadContainer = document.querySelector('#postlist, .viewthread');
                        var threadBlocked = currentTid && !!map[itemKey('thread', currentTid)];
                        if (threadContainer) {
                            if (threadBlocked) {
                                hideElement(threadContainer);
                                ensurePlaceholder(threadContainer, 'thread', currentTid, '该主题');
                                return;
                            }
                            restoreElement(threadContainer);
                            removePlaceholder(threadContainer, 'thread', currentTid);
                        }

                        var posts = document.querySelectorAll('[id^="pid"], .plc[data-pid]');
                        for (var i = 0; i < posts.length; i++) {
                            var post = posts[i];
                            var pid = getPostPid(post);
                            if (!pid) continue;
                            var authorUid = getPostAuthorUid(post);
                            var authorName = getAuthorName(post.querySelector('.authi') || post);
                            // 自己发布的楼层（含主题楼）不显示屏蔽按钮（也清掉历史上误加的）。
                            if (isOwnUid(authorUid)) {
                                var ownPostAction = post.querySelector('.yamibo-block-action[data-type="post"]');
                                if (ownPostAction) ownPostAction.remove();
                                continue;
                            }
                            var isBlocked = !!map[itemKey('post', pid)];
                            var blockedUser = getBlockedUser(map, authorUid, authorName);
                            var action = post.querySelector('.yamibo-block-action[data-type="post"]');
                            if (!action) {
                                var auth = post.querySelector('.authi');
                                var userLink = auth ? auth.querySelector('a') : null;
                                if (auth && userLink) {
                                    action = makeAction(
                                        'post',
                                        pid,
                                        (document.title || '') + ' · 楼层 ' + pid,
                                        isBlocked,
                                        authorUid,
                                        authorName || String(userLink.textContent || '').trim()
                                    );
                                    // 先插入按钮，再在用户名与按钮之间补四个不可断空格（普通空格会被 HTML 折叠成一个），
                                    // 使屏蔽文字按钮与用户名保持四个空格的距离。
                                    userLink.insertAdjacentElement('afterend', action);
                                    userLink.insertAdjacentText('afterend', String.fromCharCode(160, 160, 160, 160));
                                }
                            } else {
                                var nextLabel = isBlocked ? '取消屏蔽' : '屏蔽';
                                if (action.textContent !== nextLabel) action.textContent = nextLabel;
                            }

                            if (isBlocked) {
                                if (authorUid) removePlaceholder(post, 'user', authorUid, 'post-' + pid);
                                hideElement(post);
                                ensurePlaceholder(post, 'post', pid, '该楼层');
                            } else if (blockedUser) {
                                removePlaceholder(post, 'post', pid);
                                hideElement(post);
                                ensurePlaceholder(post, 'user', blockedUser.id, '该用户的楼层', 'post-' + pid);
                            } else {
                                restoreElement(post);
                                removePlaceholder(post, 'post', pid);
                                if (authorUid) removePlaceholder(post, 'user', authorUid, 'post-' + pid);
                            }
                        }
                    }

                    function syncAuxiliaryUserContent(map) {
                        var blockRules = [];
                        if (/thread-\d+|[?&]mod=viewthread/i.test(location.href)) {
                            blockRules.push({
                                selector: 'div.pstl.xs1.cl a.xi2.xw1',
                                container: 'div.pstl.xs1.cl',
                                label: '该用户的点评'
                            });
                            blockRules.push({
                                selector: '[id^="comment_"] > [id^="commentdetail_"] .authi a[href*="space-uid-"], ' +
                                    '[id^="comment_"] > [id^="commentdetail_"] .authi a[href*="mod=space"][href*="uid="]',
                                container: '[id^="commentdetail_"]',
                                label: '该用户的点评'
                            });
                            blockRules.push({
                                selector: 'tr[id] td > a[target=_blank]',
                                container: 'tr',
                                label: '该用户的评分'
                            });
                            blockRules.push({
                                selector: '[id^="ratelog_"] .post_box > li a[href*="mod=space"][href*="uid="], ' +
                                    '#floatlayout_topicadmin #return_rate ~ .post_box > li a[href*="mod=space"][href*="uid="]',
                                container: 'li',
                                label: '该用户的评分'
                            });
                        }
                        for (var ruleIndex = 0; ruleIndex < blockRules.length; ruleIndex++) {
                            var rule = blockRules[ruleIndex];
                            var links = document.querySelectorAll(rule.selector);
                            for (var linkIndex = 0; linkIndex < links.length; linkIndex++) {
                                var link = links[linkIndex];
                                var blockedUser = getBlockedUserFromLink(map, link);
                                var container = link.closest(rule.container);
                                if (blockedUser) {
                                    hideAuxiliaryContent(container, blockedUser, rule.label);
                                } else {
                                    restoreAuxiliaryContent(container);
                                }
                            }
                        }

                        syncBlogUserContent(map);

                        var quoteHeaders = document.querySelectorAll(
                            'div.quote > blockquote > font:first-child'
                        );
                        for (var quoteIndex = 0; quoteIndex < quoteHeaders.length; quoteIndex++) {
                            var quoteHeader = quoteHeaders[quoteIndex];
                            var quoteName = quoteHeader.querySelector('a[target=_blank]') || quoteHeader;
                            var displayedName = normalizeUserName(quoteName.textContent).split(/\s+/)[0];
                            var quoteUser = getBlockedUserFromLink(map, quoteName, displayedName);
                            var quote = quoteHeader.closest('blockquote');
                            if (quoteUser) {
                                hideAuxiliaryContent(quote, quoteUser, '该用户被引用的回复');
                            } else {
                                restoreAuxiliaryContent(quote);
                            }
                        }

                        var isForumHome = /^\/(?:forum\.php|index\.php)?$/i.test(location.pathname) &&
                            !/[?&](?:mod|id)=/i.test(location.search);
                        var recentReplyLinks = document.querySelectorAll(
                            isForumHome ? 'cite > a' : 'td.by > cite > a[c]'
                        );
                        for (var replyIndex = 0; replyIndex < recentReplyLinks.length; replyIndex++) {
                            var replyLink = recentReplyLinks[replyIndex];
                            if (replyLink.hasAttribute('c') && replyLink.getAttribute('c') !== '1') continue;
                            var replyUser = getBlockedUserFromLink(map, replyLink);
                            if (replyUser) {
                                hideBlockedUserName(replyLink, replyUser);
                            } else {
                                restoreAuxiliaryContent(replyLink);
                            }
                        }

                        var recentReplyNames = isForumHome
                            ? document.querySelectorAll('p > em')
                            : [];
                        for (var nameIndex = 0; nameIndex < recentReplyNames.length; nameIndex++) {
                            var nameNode = recentReplyNames[nameIndex];
                            var nameUser = getBlockedUser(
                                map,
                                null,
                                normalizeUserName(nameNode.textContent)
                            );
                            if (nameUser) {
                                hideBlockedUserName(nameNode, nameUser);
                            } else {
                                restoreAuxiliaryContent(nameNode);
                            }
                        }
                    }

                    function syncBlogUserContent(map) {
                        var comments = document.querySelectorAll(
                            '#comment_ul > dl[id^="comment_"], ' +
                            '.doing_list_box.threadlist > ul > li[id^="comment_"]'
                        );
                        for (var commentIndex = 0; commentIndex < comments.length; commentIndex++) {
                            var comment = comments[commentIndex];
                            var commentAuthor = comment.querySelector(
                                'a[id^="author_"][href*="space-uid-"], ' +
                                'a[id^="author_"][href*="mod=space"][href*="uid="], ' +
                                'dd.m.avt > a[href*="space-uid-"], ' +
                                'dd.m.avt > a[href*="mod=space"][href*="uid="]'
                            );
                            var commentUser = getBlockedUserFromLink(map, commentAuthor);
                            if (commentUser) {
                                hideAuxiliaryContent(comment, commentUser, '该用户的评论');
                            } else {
                                restoreAuxiliaryContent(comment);
                            }
                        }

                        var legacyTargets = document.querySelectorAll(
                            '#feed_div li[id^="feed_"] > div.cl[data-yamibo-block-kind="aux"]'
                        );
                        for (var legacyIndex = 0; legacyIndex < legacyTargets.length; legacyIndex++) {
                            restoreAuxiliaryContent(legacyTargets[legacyIndex]);
                        }

                        var groups = document.querySelectorAll('#feed_div dl.bbda.cl');
                        for (var groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                            var group = groups[groupIndex];
                            var groupActor = group.querySelector(
                                'dd.m.avt > a[target="_blank"][href*="space-uid-"], ' +
                                'dd.m.avt > a[target="_blank"][href*="mod=space"][href*="uid="]'
                            );
                            var groupUser = getBlockedUserFromLink(map, groupActor);
                            if (groupUser) {
                                hideAuxiliaryContent(group, groupUser, '该用户的 BLOG ');
                            } else {
                                restoreAuxiliaryContent(group);
                            }
                        }

                        var standaloneItems = document.querySelectorAll(
                            '#feed_div > ul.el > li[id^="feed_"]'
                        );
                        for (var itemIndex = 0; itemIndex < standaloneItems.length; itemIndex++) {
                            var item = standaloneItems[itemIndex];
                            var itemActor = item.querySelector(
                                'div.cl > a[target="_blank"][href*="space-uid-"], ' +
                                'div.cl > a[target="_blank"][href*="mod=space"][href*="uid="]'
                            );
                            var itemUser = getBlockedUserFromLink(map, itemActor);
                            if (itemUser) {
                                hideAuxiliaryContent(item, itemUser, '该用户的 BLOG ');
                            } else {
                                restoreAuxiliaryContent(item);
                            }
                        }
                    }

                    function sync() {
                        if (state.syncing) return;
                        state.syncing = true;
                        try {
                            ensureStyle();
                            // 缓存当前登录 uid：手机版帖子页不带任何自身标识，但论坛列表/个人中心页带
                            // mycenter 链接。无论屏蔽开关是否开启都要捕获，确保登录完成后立即持久化。
                            var detectedUid = getCurrentUid();
                            if (detectedUid) {
                                state.currentUid = detectedUid;
                                // 回传给 App 端持久化，下次启动/冷进帖子页也能直接用。
                                if (window.AndroidForumBlocklist && window.AndroidForumBlocklist.setUid) {
                                    try { window.AndroidForumBlocklist.setUid(detectedUid); } catch (e) {}
                                }
                            }
                            if (!state.enabled) {
                                cleanup();
                                return;
                            }
                            var map = blockedMap();
                            syncListPage(map);
                            syncPcListPage(map);
                            syncPostPage(map);
                            syncAuxiliaryUserContent(map);
                        } finally {
                            state.syncing = false;
                        }
                    }

                    function scheduleSync() {
                        if (state.timer) clearTimeout(state.timer);
                        state.timer = setTimeout(function() {
                            state.timer = null;
                            sync();
                        }, 80);
                    }

                    function updateLocal(type, id, title, shouldBlock, authorUid, authorName) {
                        var key = itemKey(type, id);
                        var next = [];
                        for (var i = 0; i < state.items.length; i++) {
                            var item = state.items[i];
                            if (itemKey(item.type, item.id) !== key) next.push(item);
                        }
                        if (shouldBlock) next.push({
                            type: type,
                            id: String(id),
                            title: title || '',
                            authorUid: authorUid || '',
                            authorName: authorName || ''
                        });
                        state.items = next;
                        sync();
                    }

                    function closeBlockChoiceMenu() {
                        var backdrop = document.querySelector('.yamibo-block-choice-backdrop');
                        if (backdrop) backdrop.remove();
                    }

                    function showBlockChoiceMenu(action) {
                        closeBlockChoiceMenu();
                        var type = action.getAttribute('data-type') || '';
                        var id = action.getAttribute('data-id') || '';
                        var title = action.getAttribute('data-title') || '';
                        var authorUid = action.getAttribute('data-author-uid') || '';
                        var authorName = action.getAttribute('data-author-name') || '';

                        var backdrop = document.createElement('div');
                        backdrop.className = 'yamibo-block-choice-backdrop';
                        var menu = document.createElement('div');
                        menu.className = 'yamibo-block-choice-menu';
                        menu.setAttribute('data-content-type', type);
                        menu.setAttribute('data-content-id', id);
                        menu.setAttribute('data-content-title', title);
                        menu.setAttribute('data-author-uid', authorUid);
                        menu.setAttribute('data-author-name', authorName);

                        var heading = document.createElement('div');
                        heading.className = 'yamibo-block-choice-title';
                        heading.textContent = '选择屏蔽方式';
                        menu.appendChild(heading);

                        var subtitle = document.createElement('div');
                        subtitle.className = 'yamibo-block-choice-subtitle';
                        subtitle.textContent = authorName ? ('用户：' + authorName) : (title || ('ID ' + id));
                        menu.appendChild(subtitle);

                        var actions = document.createElement('div');
                        actions.className = 'yamibo-block-choice-actions';

                        var contentButton = document.createElement('button');
                        contentButton.type = 'button';
                        contentButton.className = 'yamibo-block-choice-button yamibo-block-choice-button-primary';
                        contentButton.setAttribute('data-yamibo-block-choice', 'content');
                        contentButton.textContent = type === 'thread' ? '屏蔽主题' : '屏蔽楼层';
                        actions.appendChild(contentButton);

                        if (authorUid && authorName && !isOwnUid(authorUid)) {
                            var userButton = document.createElement('button');
                            userButton.type = 'button';
                            userButton.className = 'yamibo-block-choice-button';
                            userButton.setAttribute('data-yamibo-block-choice', 'user');
                            userButton.textContent = '屏蔽用户';
                            actions.appendChild(userButton);
                        }

                        var cancelButton = document.createElement('button');
                        cancelButton.type = 'button';
                        cancelButton.className = 'yamibo-block-choice-button yamibo-block-choice-button-cancel';
                        cancelButton.setAttribute('data-yamibo-block-choice', 'cancel');
                        cancelButton.textContent = '取消';
                        actions.appendChild(cancelButton);

                        menu.appendChild(actions);

                        backdrop.appendChild(menu);
                        document.body.appendChild(backdrop);
                    }

                    function commitBlock(type, id, title, authorUid, authorName) {
                        updateLocal(type, id, title, true, authorUid, authorName);
                        if (window.AndroidForumBlocklist && window.AndroidForumBlocklist.block) {
                            window.AndroidForumBlocklist.block(
                                type,
                                id,
                                title,
                                authorUid || '',
                                authorName || ''
                            );
                        }
                    }

                    document.addEventListener('click', function(event) {
                        var unblock = event.target.closest ? event.target.closest('.yamibo-unblock-action') : null;
                        if (unblock) {
                            event.preventDefault();
                            event.stopPropagation();
                            var unblockType = unblock.getAttribute('data-type');
                            var unblockId = unblock.getAttribute('data-id');
                            updateLocal(unblockType, unblockId, '', false, '', '');
                            if (window.AndroidForumBlocklist && window.AndroidForumBlocklist.unblock) {
                                window.AndroidForumBlocklist.unblock(unblockType, unblockId);
                            }
                            return;
                        }

                        var choice = event.target.closest ? event.target.closest('[data-yamibo-block-choice]') : null;
                        if (choice) {
                            event.preventDefault();
                            event.stopPropagation();
                            var menu = choice.closest('.yamibo-block-choice-menu');
                            var selectedChoice = choice.getAttribute('data-yamibo-block-choice');
                            if (!menu || selectedChoice === 'cancel') {
                                closeBlockChoiceMenu();
                                return;
                            }
                            var contentType = menu.getAttribute('data-content-type') || '';
                            var contentId = menu.getAttribute('data-content-id') || '';
                            var contentTitle = menu.getAttribute('data-content-title') || '';
                            var authorUid = menu.getAttribute('data-author-uid') || '';
                            var authorName = menu.getAttribute('data-author-name') || '';
                            closeBlockChoiceMenu();
                            if (selectedChoice === 'user') {
                                commitBlock('user', authorUid, authorName, authorUid, authorName);
                            } else {
                                commitBlock(
                                    contentType,
                                    contentId,
                                    contentTitle,
                                    authorUid,
                                    authorName
                                );
                            }
                            return;
                        }

                        var backdrop = event.target.closest ? event.target.closest('.yamibo-block-choice-backdrop') : null;
                        if (backdrop && event.target === backdrop) {
                            event.preventDefault();
                            event.stopPropagation();
                            closeBlockChoiceMenu();
                            return;
                        }

                        var action = event.target.closest ? event.target.closest('.yamibo-block-action[data-type][data-id]') : null;
                        if (!action) return;
                        event.preventDefault();
                        event.stopPropagation();
                        showBlockChoiceMenu(action);
                    }, true);

                    var observer = new MutationObserver(scheduleSync);
                    if (document.body) observer.observe(document.body, { childList: true, subtree: true });

                    window.__yamiboForumBlocker = {
                        update: function(enabledValue, itemsValue, darkValue, uidValue) {
                            state.enabled = !!enabledValue;
                            state.items = Array.isArray(itemsValue) ? itemsValue : [];
                            state.dark = !!darkValue;
                            // App 端持久化的 uid 优先；为空时保留已有缓存，交给页面内探测兜底。
                            if (uidValue && /^[1-9]\d*$/.test(String(uidValue))) {
                                state.currentUid = String(uidValue);
                            }
                            sync();
                        },
                        sync: sync
                    };
                }

                window.__yamiboForumBlocker.update($enabled, incomingItems, $isDark, $selfUidLiteral);
            })();
        """.trimIndent()
    }

    val SEARCH_DIRECT_NAV_JS = """
        (function() {
            if (window.__yamiboSearchNav) return;
            window.__yamiboSearchNav = true;

            // 事件委托在 document 上，避免导航后 DOM 替换导致监听丢失
            document.addEventListener('submit', function(e) {
                var form = e.target;
                if (!form || !form.classList.contains('searchform')) return;
                if (!/search\.php/.test(window.location.href)) return;

                var input = document.getElementById('scform_srchtxt');
                if (!input) return;

                var keyword = input.value.trim();
                if (!keyword) return;

                // 包含中文或空白字符则不匹配（必须是纯网址）
                if (/[一-鿿㐀-䶿豈-﫿\s]/.test(keyword)) return;

                var url = null;

                // 只匹配帖子网址:
                // https://bbs.yamibo.com/forum.php?mod=viewthread&tid=XXX...
                // https://m.yamibo.com/forum.php?mod=viewthread&tid=XXX...
                if (/^https?:\/\/(bbs|m)\.yamibo\.com\/forum\.php\?mod=viewthread&tid=\d+/.test(keyword)) {
                    url = keyword.replace(/&highlight=[^&]*/g, '');
                }

                // https://bbs.yamibo.com/thread-XXX-X-X.html
                if (!url && /^https?:\/\/(bbs|m)\.yamibo\.com\/thread-\d+-\d+-\d+\.html$/.test(keyword)) {
                    url = keyword;
                }

                if (url && window.AndroidSearchNav) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.AndroidSearchNav.navigateToPost(url);
                }
            }, true);
        })();
    """.trimIndent()

    val PRESERVE_DESKTOP_SPACE_LINKS_JS = """
        (function() {
            function isDesktopSpacePage() {
                var href = location.href || '';
                if (/[?&]mobile=no(?:&|${'$'})/i.test(href)) return true;
                if (/[?&]mobile=(?:2|yes)(?:&|${'$'})/i.test(href)) return false;
                var body = document.body;
                if (!body) return false;
                if (body.id === 'space') return true;
                var bodyClass = ' ' + (body.className || '') + ' ';
                return /\spg_space(?:cp)?\s/.test(bodyClass);
            }

            function shouldRewrite(url) {
                var host = (url.hostname || '').toLowerCase();
                if (host !== 'bbs.yamibo.com' && host !== 'm.yamibo.com') return false;
                if (!/\/home\.php$/i.test(url.pathname)) return false;
                return (url.searchParams.get('mod') || '').toLowerCase() === 'space';
            }

            function rewriteLink(link) {
                var raw = link.getAttribute('href') || '';
                if (!raw || raw.charAt(0) === '#' || /^javascript:/i.test(raw) || /^mailto:/i.test(raw)) return;
                var url;
                try {
                    url = new URL(raw, document.baseURI || location.href);
                } catch (e) {
                    return;
                }
                if (!shouldRewrite(url)) return;
                if ((url.hostname || '').toLowerCase() === 'm.yamibo.com') {
                    url.hostname = 'bbs.yamibo.com';
                }
                var mobile = (url.searchParams.get('mobile') || '').toLowerCase();
                if (mobile !== 'no') {
                    url.searchParams.set('mobile', 'no');
                    link.href = url.href;
                }
            }

            function rewriteAllSpaceLinks() {
                if (!isDesktopSpacePage()) return;
                var links = document.querySelectorAll('a[href*="home.php"][href*="mod=space"],a[href*="home.php"][href*="mod%3Dspace"]');
                for (var i = 0; i < links.length; i++) {
                    rewriteLink(links[i]);
                }
            }

            function boot() {
                if (!isDesktopSpacePage()) return;
                rewriteAllSpaceLinks();
                if (window.__yamiboDesktopSpaceLinksObserver) return;
                var pending = false;
                window.__yamiboDesktopSpaceLinksObserver = new MutationObserver(function() {
                    if (pending) return;
                    pending = true;
                    setTimeout(function() {
                        pending = false;
                        rewriteAllSpaceLinks();
                    }, 80);
                });
                window.__yamiboDesktopSpaceLinksObserver.observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['href']
                });
            }

            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', boot, { once: true });
            }
            boot();
        })();
    """.trimIndent()

    val PULL_REFRESH_EDIT_FOCUS_JS = """
        (function() {
            function yamiboIsEditableTarget(node) {
                var el = node;
                while (el && el.nodeType === 1) {
                    var tag = (el.tagName || '').toLowerCase();
                    if (tag === 'textarea' || tag === 'select') return true;
                    if (tag === 'input') {
                        var type = (el.type || '').toLowerCase();
                        return type !== 'button' &&
                            type !== 'checkbox' &&
                            type !== 'radio' &&
                            type !== 'submit' &&
                            type !== 'reset' &&
                            type !== 'file' &&
                            type !== 'image' &&
                            type !== 'color' &&
                            type !== 'range';
                    }
                    if (el.isContentEditable || el.getAttribute('contenteditable') === 'true') return true;
                    el = el.parentElement;
                }
                return false;
            }

            function yamiboNotifyPullRefreshGuard(focused) {
                if (!window.AndroidPullRefreshGuard || !window.AndroidPullRefreshGuard.setEditableFocused) return;
                try {
                    window.AndroidPullRefreshGuard.setEditableFocused(!!focused);
                } catch (e) {
                }
            }

            function yamiboSyncPullRefreshGuard() {
                yamiboNotifyPullRefreshGuard(yamiboIsEditableTarget(document.activeElement));
            }

            if (!window.__yamiboPullRefreshGuardInstalled) {
                window.__yamiboPullRefreshGuardInstalled = true;
                document.addEventListener('focusin', yamiboSyncPullRefreshGuard, true);
                document.addEventListener('focusout', function() {
                    setTimeout(yamiboSyncPullRefreshGuard, 120);
                }, true);
                document.addEventListener('touchstart', function(event) {
                    if (yamiboIsEditableTarget(event.target)) {
                        yamiboNotifyPullRefreshGuard(true);
                    }
                }, true);
                document.addEventListener('pointerdown', function(event) {
                    if (yamiboIsEditableTarget(event.target)) {
                        yamiboNotifyPullRefreshGuard(true);
                    }
                }, true);
                document.addEventListener('visibilitychange', yamiboSyncPullRefreshGuard, true);
            }

            yamiboSyncPullRefreshGuard();
        })();
    """.trimIndent()

    val PRESERVE_RATE_POSITION_JS = """
        (function() {
            if (window.__yamiboPreserveRatePositionV1) return;
            window.__yamiboPreserveRatePositionV1 = true;

            var CONTEXT_KEY = 'yamibo:rate-context:v1';
            var PENDING_KEY = 'yamibo:pending-rate-return:v1';
            var MAX_AGE_MS = 120000;

            function parseUrl(raw) {
                try { return new URL(raw, document.baseURI); } catch (e) { return null; }
            }

            function read(key) {
                try {
                    var value = sessionStorage.getItem(key);
                    return value ? JSON.parse(value) : null;
                } catch (e) { return null; }
            }

            function write(key, value) {
                try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
            }

            function remove(key) {
                try { sessionStorage.removeItem(key); } catch (e) {}
            }

            function threadId(url) {
                if (!url) return '';
                var tid = url.searchParams.get('tid') || '';
                if (
                    String(url.searchParams.get('mod') || '').toLowerCase() === 'viewthread' &&
                    /^\d+${'$'}/.test(tid)
                ) return tid;
                var path = String(url.pathname || '').replace(/^\/+/, '');
                var match = /^thread-(\d+)(?:-\d+){0,2}\.html${'$'}/i.exec(path);
                return match ? match[1] : '';
            }

            function fresh(value) {
                return value && Date.now() - Number(value.createdAt || 0) <= MAX_AGE_MS;
            }

            function targetUrl(value) {
                var url = parseUrl(value && value.returnUrl);
                if (!url) return '';
                url.hash = 'pid' + String(value.pid || '');
                return url.href;
            }

            function rememberLink(link) {
                var rateUrl = parseUrl(link && link.href);
                var currentUrl = parseUrl(location.href);
                if (!rateUrl || !currentUrl) return;
                if (
                    String(rateUrl.searchParams.get('mod') || '').toLowerCase() !== 'misc' ||
                    String(rateUrl.searchParams.get('action') || '').toLowerCase() !== 'rate'
                ) return;
                var tid = rateUrl.searchParams.get('tid') || '';
                var pid = rateUrl.searchParams.get('pid') || '';
                if (!/^\d+${'$'}/.test(pid) || threadId(currentUrl) !== tid) return;
                currentUrl.hash = '';
                write(CONTEXT_KEY, {
                    tid: tid,
                    pid: pid,
                    returnUrl: currentUrl.href,
                    createdAt: Date.now()
                });
            }

            function pendingFor(form) {
                var tidNode = form.querySelector('input[name="tid"]');
                var pidNode = form.querySelector('input[name="pid"]');
                var tid = tidNode ? String(tidNode.value || '') : '';
                var pid = pidNode ? String(pidNode.value || '') : '';
                if (!/^\d+${'$'}/.test(tid) || !/^\d+${'$'}/.test(pid)) return null;

                var context = read(CONTEXT_KEY);
                var currentUrl = parseUrl(location.href);
                var returnUrl = '';
                if (fresh(context) && String(context.tid) === tid && String(context.pid) === pid) {
                    returnUrl = context.returnUrl;
                } else if (threadId(currentUrl) === tid) {
                    currentUrl.hash = '';
                    returnUrl = currentUrl.href;
                }
                return returnUrl ? {
                    tid: tid,
                    pid: pid,
                    returnUrl: returnUrl,
                    createdAt: Date.now()
                } : null;
            }

            function wrapSuccessHandler() {
                var original = window.succeedhandle_rate;
                if (typeof original !== 'function' || original.__yamiboPreserveRatePosition) return;
                var wrapped = function() {
                    var args = Array.prototype.slice.call(arguments);
                    var pending = read(PENDING_KEY);
                    var target = fresh(pending) ? targetUrl(pending) : '';
                    if (target) args[0] = target;
                    var result = original.apply(this, args);
                    if (fresh(pending)) {
                        setTimeout(function() {
                            var current = parseUrl(location.href);
                            if (threadId(current) === String(pending.tid || '')) {
                                scrollToPost(pending.pid, function() {
                                    remove(PENDING_KEY);
                                    remove(CONTEXT_KEY);
                                });
                            }
                        }, 0);
                    }
                    return result;
                };
                wrapped.__yamiboPreserveRatePosition = true;
                window.succeedhandle_rate = wrapped;
            }

            function prepareSubmit(form) {
                var pending = pendingFor(form);
                if (!pending) return;
                write(PENDING_KEY, pending);
                var referer = form.querySelector('input[name="referer"]');
                if (referer) referer.value = targetUrl(pending);
                wrapSuccessHandler();

                var createdAt = pending.createdAt;
                setTimeout(function() {
                    var latest = read(PENDING_KEY);
                    if (
                        latest &&
                        Number(latest.createdAt) === createdAt &&
                        document.visibilityState !== 'hidden'
                    ) remove(PENDING_KEY);
                }, 15000);
            }

            function scrollToPost(pid, onFound) {
                var normalizedPid = String(pid || '');
                var attempts = 0;
                function scroll() {
                    var post =
                        document.getElementById('pid' + normalizedPid) ||
                        document.getElementById('post_' + normalizedPid) ||
                        document.getElementById('postmessage_' + normalizedPid);
                    if (post) {
                        try { post.scrollIntoView({ block: 'center', inline: 'nearest' }); }
                        catch (e) { post.scrollIntoView(); }
                        if (typeof onFound === 'function') onFound();
                    } else if (++attempts < 12) {
                        setTimeout(scroll, attempts * 150);
                    }
                }
                scroll();
            }

            document.addEventListener('click', function(event) {
                var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
                if (link) rememberLink(link);
            }, true);

            document.addEventListener('submit', function(event) {
                var form = event.target;
                if (!form || form.nodeType !== 1) return;
                var action = parseUrl(form.getAttribute('action') || '');
                if (
                    form.id === 'rateform' ||
                    (action &&
                        String(action.searchParams.get('mod') || '').toLowerCase() === 'misc' &&
                        String(action.searchParams.get('action') || '').toLowerCase() === 'rate')
                ) prepareSubmit(form);
            }, true);

            var pending = read(PENDING_KEY);
            if (!fresh(pending)) {
                if (pending) remove(PENDING_KEY);
                return;
            }
            if (threadId(parseUrl(location.href)) !== String(pending.tid || '')) return;

            var target = targetUrl(pending);
            var current = parseUrl(location.href);
            var original = parseUrl(pending.returnUrl);
            if (!target || !current || !original) {
                remove(PENDING_KEY);
                return;
            }
            current.hash = '';
            original.hash = '';
            if (current.href === original.href) {
                scrollToPost(pending.pid, function() {
                    remove(PENDING_KEY);
                    remove(CONTEXT_KEY);
                });
            } else {
                window.location.replace(target);
            }
        })();
    """.trimIndent()

    val BBS_COMMIT_BOOTSTRAP_JS by lazy {
        combineJs(
            "INJECT_PSWP_AND_MANGA_JS" to INJECT_PSWP_AND_MANGA_JS,
            "FIX_CAROUSEL_LAYOUT_JS" to FIX_CAROUSEL_LAYOUT_JS,
            "BBS_THREAD_NAVIGATION_JS" to BBS_THREAD_NAVIGATION_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "SEARCH_DIRECT_NAV_JS" to SEARCH_DIRECT_NAV_JS,
            "PRESERVE_DESKTOP_SPACE_LINKS_JS" to PRESERVE_DESKTOP_SPACE_LINKS_JS,
            "PULL_REFRESH_EDIT_FOCUS_JS" to PULL_REFRESH_EDIT_FOCUS_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val BBS_MANGA_REINJECT_JS by lazy {
        combineJs(
            "INJECT_PSWP_AND_MANGA_JS" to INJECT_PSWP_AND_MANGA_JS,
            "FIX_CAROUSEL_LAYOUT_JS" to FIX_CAROUSEL_LAYOUT_JS,
            "BBS_THREAD_NAVIGATION_JS" to BBS_THREAD_NAVIGATION_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "PRESERVE_DESKTOP_SPACE_LINKS_JS" to PRESERVE_DESKTOP_SPACE_LINKS_JS,
            "PULL_REFRESH_EDIT_FOCUS_JS" to PULL_REFRESH_EDIT_FOCUS_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val OTHER_COMMIT_BOOTSTRAP_JS by lazy {
        combineJs(
            "OTHER_WEB_INIT_PSWP_JS" to OTHER_WEB_INIT_PSWP_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val MINE_COMMIT_BOOTSTRAP_JS by lazy {
        combineJs(
            "MINE_INJECT_PSWP_AND_MANGA_JS" to MINE_INJECT_PSWP_AND_MANGA_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "SEARCH_DIRECT_NAV_JS" to SEARCH_DIRECT_NAV_JS,
            "PULL_REFRESH_EDIT_FOCUS_JS" to PULL_REFRESH_EDIT_FOCUS_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val MINE_MANGA_REINJECT_JS by lazy {
        combineJs(
            "MINE_INJECT_PSWP_AND_MANGA_JS" to MINE_INJECT_PSWP_AND_MANGA_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "PULL_REFRESH_EDIT_FOCUS_JS" to PULL_REFRESH_EDIT_FOCUS_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val MANGA_BOOTSTRAP_JS by lazy {
        combineJs(
            "INJECT_PSWP_AND_MANGA_JS" to INJECT_PSWP_AND_MANGA_JS,
            "THREAD_LIST_CLICK_FIX_JS" to THREAD_LIST_CLICK_FIX_JS,
            "PRESERVE_RATE_POSITION_JS" to PRESERVE_RATE_POSITION_JS,
            "INJECT_COPY_LINK_JS" to INJECT_COPY_LINK_JS
        )
    }

    val RELOAD_BROKEN_IMAGES_JS = """
        (function(){
            var imgs = document.querySelectorAll('img');
            for(var i=0; i<imgs.length; i++) {
                var img = imgs[i];
                if(!img.complete || typeof img.naturalWidth === 'undefined' || img.naturalWidth === 0 || img.style.opacity === '0') {
                    img.onload = function() {
                        this.style.transition = 'opacity 0.2s ease-in';
                        this.style.opacity = '1';
                    };
                    var src = img.src;
                    img.src = '';
                    img.src = src;
                }
            }
        })();
    """.trimIndent()

}
