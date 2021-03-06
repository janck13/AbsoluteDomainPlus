package com.github.wongxd.img_lib.img

import com.github.wongxd.core_lib.base.utils.utilcode.util.CacheUtils
import com.github.wongxd.img_lib.data.bean.ImgTypeBean
import com.github.wongxd.img_lib.data.bean.TuListBean
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Created by wongxd on 2018/1/5.
 */
interface BaseTuSite {

    fun getTypeList(): List<ImgTypeBean>

    fun getSpecificPage(tViewModel: TuViewModel, url: String, page: Int): MutableList<TuListBean>

    fun getChildDetail(tViewModel: SeePicViewModel, url: String, page: Int): List<String>?


    fun BaseTuSite.setCache(url: String, list: MutableList<String>?) {
        list?.let {
            val json = Gson().toJson(list)
            CacheUtils.getInstance("String").put(url, json)
        }
    }

    fun BaseTuSite.useCache(url: String): List<String>? {
        val cacheStr = CacheUtils.getInstance("String").getString(url)
        val list: List<String>? = Gson().fromJson(cacheStr, object : TypeToken<List<String>>() {}.type)
        return if (list?.size ?: 0 == 0) null else list
    }

    fun BaseTuSite.CleanCache(url: String) {
        val cacheStr = CacheUtils.getInstance("String").remove(url)
    }

    fun BaseTuSite.getSiteName() {
        val cacheStr = this.javaClass.simpleName
    }
}