package org.shirakawatyu.yamibo.novel.repository

import org.shirakawatyu.yamibo.novel.bean.space.SpaceListPage
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.BlogDetail
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.SpaceApi
import org.shirakawatyu.yamibo.novel.parser.SpaceMobileParser
import org.shirakawatyu.yamibo.novel.util.AppErrorLog

class SpaceRepository(
    private val api: SpaceApi = YamiboRetrofit.getInstance().create(SpaceApi::class.java)
) {
    suspend fun getList(request: SpaceListRequest, page: Int): SpaceListPage {
        val html = when (request.kind) {
            SpacePageKind.PRIVATE_MESSAGE -> api.getSpacePage(
                doParam = "pm",
                page = page
            ).string()
            SpacePageKind.NOTICE -> api.getSpacePage(
                doParam = "notice",
                page = page
            ).string()
            SpacePageKind.FRIEND -> api.getSpacePage(
                doParam = "friend",
                view = request.view.ifBlank { null },
                type = request.type.ifBlank { null },
                page = page
            ).string()
            SpacePageKind.DOING -> api.getSpacePage(
                doParam = "doing",
                view = request.view,
                page = page
            ).string()
            SpacePageKind.BLOG -> api.getSpacePage(
                uid = request.uid,
                doParam = "blog",
                view = request.view,
                classId = request.categoryId.ifBlank { null },
                page = page
            ).string()
            SpacePageKind.USER_THREAD -> api.getSpacePage(
                uid = request.uid,
                doParam = "thread",
                view = "me",
                type = request.type.ifBlank { null },
                page = page
            ).string()
        }
        val result = SpaceMobileParser.parsePage(request.kind, html)
        if (result.items.isEmpty()) {
            if (SpaceMobileParser.isLoginRequired(html)) {
                throw IllegalStateException("需要登录后才能查看此页面")
            }
            AppErrorLog.record(
                "空间页解析为空 kind=${request.kind} view=${request.view} page=$page"
            )
        }
        return result
    }

    suspend fun getListByUrl(request: SpaceListRequest, url: String): SpaceListPage {
        val html = api.getPageByUrl(url).string()
        val result = SpaceMobileParser.parsePage(request.kind, html)
        if (result.items.isEmpty()) {
            if (SpaceMobileParser.isLoginRequired(html)) {
                throw IllegalStateException("需要登录后才能查看此页面")
            }
            AppErrorLog.record("空间页解析为空 kind=${request.kind} url=$url")
        }
        return result
    }

    suspend fun getBlogDetail(url: String): BlogDetail {
        val html = api.getPageByUrl(url).string()
        return SpaceMobileParser.parseBlogDetail(html, url)
    }
}
