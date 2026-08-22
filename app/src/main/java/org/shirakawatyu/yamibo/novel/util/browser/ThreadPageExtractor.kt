package org.shirakawatyu.yamibo.novel.util.browser

/** 将 Discuz 帖子 DOM 压缩成 ForumApiParser 可消费的最小 JSON。 */
object ThreadPageExtractor {
    val SCRIPT = """
        (function() {
            function text(node) {
                return node && node.textContent ? node.textContent.replace(/\s+/g, ' ').trim() : '';
            }
            function first(root, selectors) {
                for (var i = 0; i < selectors.length; i += 1) {
                    var node = root.querySelector(selectors[i]);
                    if (node) return node;
                }
                return null;
            }
            function numberFrom(value) {
                var match = String(value || '').replace(/,/g, '').match(/\d+/);
                return match ? Number(match[0]) : 0;
            }
            function paramFrom(url, key) {
                try { return new URL(url, location.href).searchParams.get(key) || ''; }
                catch (_) { return ''; }
            }
            function uidFrom(anchor) {
                if (!anchor) return '';
                var href = anchor.getAttribute('href') || '';
                return paramFrom(href, 'uid') || ((href.match(/space-uid-(\d+)/i) || [])[1] || '');
            }
            function absolute(value) {
                try { return new URL(value, location.href).href; }
                catch (_) { return String(value || ''); }
            }
            function labeledCount(label) {
                var candidates = Array.from(document.querySelectorAll('.hm, .xg1, .xi1, .thread-stat, .viewthread'));
                for (var i = 0; i < candidates.length; i += 1) {
                    var value = text(candidates[i]);
                    var match = value.match(new RegExp(label + '\\s*[:：]?\\s*([0-9,]+)'));
                    if (match) return numberFrom(match[1]);
                }
                return 0;
            }

            var postContainers = Array.from(document.querySelectorAll('.plc[id^="pid"], [id^="post_"]'))
                .filter(function(node) {
                    return /^pid\d+$/.test(node.id || '') || /^post_\d+$/.test(node.id || '');
                })
                .filter(function(node) {
                    return first(node, ['[id^="postmessage_"]', '.message']);
                });
            if (!postContainers.length) {
                var errorNode = first(document, ['#messagetext', '.showmessage', '.alert_error', '.nfl .f_c']);
                var errorText = text(errorNode);
                var bodyText = text(document.body).toLowerCase();
                var scriptSignals = Array.from(document.scripts).some(function(script) {
                    return /__noxexpire|\/nox_|gangplank_/i.test(
                        String(script.src || '') + ' ' + String(script.textContent || '')
                    );
                });
                var needsVerification = scriptSignals ||
                    /验证|安全检查|访问过于频繁|checking your browser|just a moment|challenge/.test(bodyText);
                if (needsVerification) return JSON.stringify({ status: 'verification' });
                if (errorText) return JSON.stringify({ status: 'error', message: errorText });
                return JSON.stringify({ status: 'loading' });
            }

            var currentUrl = String(location.href || '');
            var tid = paramFrom(currentUrl, 'tid') || ((currentUrl.match(/thread-(\d+)/i) || [])[1] || '');
            var subjectNode = first(document, ['#thread_subject', '.view_tit', 'h1.ts', '.thread_subject']);
            var subjectClone = subjectNode ? subjectNode.cloneNode(true) : null;
            if (subjectClone) {
                Array.from(subjectClone.querySelectorAll('em')).forEach(function(node) { node.remove(); });
            }
            var subject = text(subjectClone);
            function forumIdFrom(anchor) {
                if (!anchor) return '';
                var href = anchor.getAttribute('href') || anchor.href || '';
                return paramFrom(href, 'fid') || ((href.match(/forum-(\d+)(?:-|\.|$)/i) || [])[1] || '');
            }
            function isBoardLink(anchor) {
                if (!forumIdFrom(anchor)) return false;
                var href = anchor.getAttribute('href') || '';
                return !/[?&](?:typeid|filter)=/i.test(href);
            }
            // 电脑版面包屑最后一个版块链接才是帖子所属大区。主题分类链接同样包含
            // mod=forumdisplay&fid，但还带 typeid/filter，不能拿来当顶部标题。
            var breadcrumbForumLinks = Array.from(document.querySelectorAll('#pt a[href]'))
                .filter(isBoardLink);
            var forumLink = breadcrumbForumLinks.length
                ? breadcrumbForumLinks[breadcrumbForumLinks.length - 1]
                : Array.from(document.querySelectorAll(
                    '.z a[href*="mod=forumdisplay"][href*="fid="], ' +
                    'a[href*="mod=forumdisplay"][href*="fid="]'
                )).filter(isBoardLink)[0];
            var forumId = forumIdFrom(forumLink);
            var forumName = text(forumLink);

            var posts = postContainers.slice(0, 20).map(function(container, index) {
                var message = first(container, ['[id^="postmessage_"]', '.message']);
                var pid = String(container.id || '').replace(/^pid|^post_/, '') ||
                    String(message.id || '').replace('postmessage_', '');
                var authorLink = first(container, [
                    '.authi a.xw1',
                    '.authi a[href*="space-uid-"]',
                    '.authi a[href*="mod=space"][href*="uid="]',
                    '.authi .mtit .z a[href*="uid="]',
                    '.avatar a[href*="uid="]'
                ]);
                var authorName = text(authorLink) || text(first(container, ['.authi .xw1', '.author'])) || '匿名';
                var authorId = uidFrom(authorLink);
                var dateNode = first(container, [
                    '.authi .mtime',
                    '.authi em[id^="authorposton"]',
                    '.authi em',
                    'em[id^="authorposton"]',
                    '.postdate'
                ]);
                var rawDate = text(dateNode).replace(/^发表于\s*/, '');
                var dateMatch = rawDate.match(/\d{4}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}/);
                var createdAt = dateMatch ? dateMatch[0] : rawDate;
                var floorNode = first(container, [
                    '.authi .mtit .y',
                    'a[id^="postnum"] em',
                    'a[id^="postnum"]',
                    '.pi strong a'
                ]);
                var floor = numberFrom(text(floorNode)) || (index + 1);
                var attachments = Array.from(container.querySelectorAll('a[href*="mod=attachment"][href*="aid="]'))
                    .map(function(link, attachmentIndex) {
                        var href = absolute(link.getAttribute('href') || link.href || '');
                        var aid = paramFrom(href, 'aid') || String(attachmentIndex + 1);
                        var nameNode = first(link, ['.link.f_b', '.f_b']);
                        var filename = text(nameNode) || text(link) || ('附件 ' + aid);
                        return {
                            aid: aid,
                            filename: filename,
                            attachment: href,
                            isimage: /\.(?:avif|bmp|gif|jpe?g|png|webp)(?:[?#]|$)/i.test(href) ? 1 : 0
                        };
                    });
                return {
                    pid: pid,
                    tid: tid,
                    authorid: authorId,
                    author: authorName,
                    anonymous: authorId ? 0 : 1,
                    dateline: createdAt,
                    number: floor,
                    position: floor,
                    first: floor === 1 ? 1 : 0,
                    message: message.innerHTML,
                    attachments: attachments
                };
            });

            var firstPost = posts[0];
            var pageNumbers = Array.from(document.querySelectorAll('.pg a, .pg strong, .page option, .page span'))
                .map(function(node) { return numberFrom(text(node)); })
                .filter(function(value) { return value > 0; });
            var currentPage = numberFrom(text(document.querySelector('.pg strong, .page span'))) ||
                numberFrom(paramFrom(currentUrl, 'page')) || 1;
            var totalPages = Math.max.apply(Math, pageNumbers.concat([currentPage, 1]));
            var firstStats = postContainers[0].querySelectorAll('.authi .mtime .y em');
            var replyCount = numberFrom(text(document.querySelector('.txtlist .mtit em'))) ||
                (firstStats.length > 1 ? numberFrom(text(firstStats[1])) : 0) ||
                labeledCount('回复');
            var viewCount = (firstStats.length > 0 ? numberFrom(text(firstStats[0])) : 0) ||
                labeledCount('查看');
            if (!replyCount && totalPages === 1) replyCount = Math.max(posts.length - 1, 0);
            var closed = /主题已关闭|本主题已关闭/.test(text(document.body)) ? 1 : 0;
            var extras = Array.from(document.querySelectorAll('[id^="ratelog_"], form#poll, [id^="comment_"]'))
                .map(function(node) { return node.outerHTML; })
                .join('');
            var forms = Array.from(document.querySelectorAll('form[id^="rateform_"], form[id^="commentform_"]'))
                .map(function(node) { return node.outerHTML; })
                .join('');

            return JSON.stringify({
                status: 'ready',
                page: currentPage,
                totalPages: totalPages,
                extrasHtml: extras,
                formsHtml: forms,
                Variables: {
                    page: currentPage,
                    ppp: Math.max(posts.length, 1),
                    forum: { fid: forumId, name: forumName },
                    thread: {
                        tid: tid,
                        fid: forumId,
                        forumname: forumName,
                        subject: subject,
                        authorid: firstPost.authorid,
                        author: firstPost.author,
                        replies: replyCount,
                        views: viewCount,
                        closed: closed
                    },
                    postlist: posts
                }
            });
        })();
    """.trimIndent()
}
