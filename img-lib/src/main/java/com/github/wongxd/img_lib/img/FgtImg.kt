package com.github.wongxd.img_lib.img

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.widget.ArrayAdapter
import com.billy.cc.core.component.CC
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.wongxd.core_lib.ComponentAppAction
import com.github.wongxd.core_lib.RequestState
import com.github.wongxd.core_lib.base.kotin.extension.database.parseList
import com.github.wongxd.core_lib.base.kotin.extension.database.toVarargArray
import com.github.wongxd.core_lib.custom.directselect.DSListView
import com.github.wongxd.core_lib.fragmenaction.MainTabFragment
import com.github.wongxd.img_lib.R
import com.github.wongxd.img_lib.data.bean.ImgSiteBean
import com.github.wongxd.img_lib.data.bean.ImgTypeBean
import com.github.wongxd.img_lib.data.bean.TuListBean
import com.github.wongxd.img_lib.img.adapter.RvFgtImgAdapter
import com.github.wongxd.img_lib.img.event.TuFavoriteEvent
import com.github.wongxd.img_lib.img.siteSelecte.SiteAdapter
import com.github.wongxd.img_lib.img.siteSelecte.SitePickerBox
import com.tapadoo.alerter.Alerter
import com.wongxd.absolutedomain.data.database.Tu
import com.wongxd.absolutedomain.data.database.TuTable
import com.wongxd.absolutedomain.data.database.tuDB
import kotlinx.android.synthetic.main.fgt_img.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.db.insert
import org.jetbrains.anko.db.select
import org.jetbrains.anko.db.transaction


/**
 * Created by wongxd on 2018/1/5.
 */
class FgtImg : MainTabFragment() {

    override fun getLayout(): Int {
        return R.layout.fgt_img
    }

    companion object {

        fun newInstance(args: Bundle = Bundle()): FgtImg {
            val fragment = FgtImg()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var siteAdapter: ArrayAdapter<ImgSiteBean>
    private lateinit var adapter: RvFgtImgAdapter
    private lateinit var mVm: TuViewModel

    private val siteList: MutableList<ImgSiteBean> = ArrayList()

    @SuppressLint("SetTextI18n")
    override fun initView(mView: View?) {
        EventBus.getDefault().register(this)
        mVm = ViewModelProviders.of(_mActivity).get(TuViewModel::class.java)
        initRecycle()

        initSiteSwitch()

        srl_fgt_img.setOnRefreshListener { rv_fgt_img.scrollToPosition(0);mVm.refreshList() }
        srl_fgt_img.setOnLoadmoreListener { mVm.addPageList() }

        mVm.siteList.observe(this, Observer {
            it?.let {
                siteList.clear()
                siteList.addAll(it)
                siteAdapter.notifyDataSetChanged()

                val current = it.filter { it.site::class.java.name == TuViewModel.defaultTuSite::class.java.name }
                if (siteList.isNotEmpty() && current.isNotEmpty())
                    siteListView.selectedIndex = it.indexOf(current[0])
            }
        })

        mVm.tuList.observe(this, Observer<MutableList<TuListBean>> { t ->
            t?.let {
                if (mVm.currentPage != 1)
                    adapter.addData(it)
                else
                    adapter.setNewData(it)

            }
        })
        mVm.getListState.observe(this, object : Observer<RequestState> {
            override fun onChanged(t: RequestState?) {
                t?.let {
                    if (it == RequestState.REFRESH) {
                        srl_fgt_img.finishRefresh()
                    } else srl_fgt_img.finishLoadmore()

                    if (it.state == 0) {
                        activity?.let { it1 ->
                            Alerter.create(it1)
                                    .setTitle("get wrong img ---")
                                    .setText(it.info)
                                    .enableSwipeToDismiss()
                                    .setBackgroundColorInt(Color.RED)
                                    .show()
                        }
                    }
                }
            }

        })


        mVm.typeList.observe(this, Observer {
            it?.let {
                initTab(it)
                srl_fgt_img.autoRefresh()
            }
        })


    }

    val siteListView: DSListView<ImgSiteBean> by lazy { ds_picker_img as DSListView<ImgSiteBean> }

    private fun initSiteSwitch() {
        siteAdapter = SiteAdapter(context!!, siteList)
        siteListView.setAdapter(siteAdapter)

        siteListView.setPickerBox(site_picker_img)

        site_picker_img.setSelectedListener(object : SitePickerBox.SiteSelecterListener {
            override fun onCall(item: ImgSiteBean, selectedIndex: Int) {
                mVm.changeSite(item.site)
                siteAdapter.notifyDataSetChanged()
            }
        })
    }


    private fun initTab(typeList: List<ImgTypeBean>) {
        tab_fgt_img.removeAllTabs()
        typeList.forEach { tab_fgt_img.addTab(tab_fgt_img.newTab().setText(it.title)) }
        tab_fgt_img.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {
                rv_fgt_img.scrollToPosition(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    mVm.changeType(it.position)
                    srl_fgt_img.autoRefresh()
                }
            }
        })

    }


    private fun initRecycle() {

        adapter = RvFgtImgAdapter { view, tuListBean ->

            val b = Bundle()
            b.putString("url", tuListBean.url)
            b.putString("preview", tuListBean.preview)
            b.putString("title", tuListBean.title)
            b.putString("siteClass", TuViewModel.defaultTuSite.javaClass.name)
            CC.obtainBuilder("cApp")
                    .setActionName(ComponentAppAction.FgtMainStartNewFgt)
                    .addParam("fgt", FgtSeePic.newInstance(b))
                    .build()
                    .call()

        }
        adapter.setEnableLoadMore(true)
        adapter.setOnItemLongClickListener { adapter1, view1, position ->
            //收藏
            adapter.data.let {
                val bean = it[position]
                activity?.tuDB?.use {
                    transaction {
                        val items = select(TuTable.TABLE_NAME).whereSimple(TuTable.ADDRESS + "=?", bean.url)
                                .parseList({ Tu(HashMap(it)) })

                        if (items.isEmpty()) {  //如果是空的
                            val tu = Tu()
                            tu.address = bean.url
                            tu.name = bean.title
                            tu.preview = bean.preview
                            tu.time = System.currentTimeMillis()
                            tu.siteClass = TuViewModel.defaultTuSite.javaClass.name
                            insert(TuTable.TABLE_NAME, *tu.map.toVarargArray())
                        } else {
                            delete(TuTable.TABLE_NAME, TuTable.ADDRESS + "=?", arrayOf(bean.url))
                        }
                        adapter.notifyItemChanged(position, "changeFavorite")
                    }
                }
            }

            return@setOnItemLongClickListener true
        }


        rv_fgt_img.adapter = adapter
        val layoutManager = GridLayoutManager(context, 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (adapter.data.size == 0)
                    return 2
                return 1
            }

        }
        rv_fgt_img.layoutManager = layoutManager
        adapter.openLoadAnimation(BaseQuickAdapter.SLIDEIN_BOTTOM)
//        adapter.setEmptyView(R.layout.item_rv_empty, rv_fgt_img)

    }


    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    @Subscribe
    fun doSyncFavorite(event: TuFavoriteEvent) {
        adapter.notifyDataSetChanged()
    }


}