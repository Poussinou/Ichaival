package com.utazukin.ichaival


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.utazukin.ichaival.ThumbRecyclerViewAdapter.ThumbInteractionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


private const val ARCHIVE_ID = "arcid"

class GalleryPreviewFragment : Fragment(), ThumbInteractionListener {
    private var archiveId: String? = null
    private var archive: Archive? = null
    private lateinit var thumbAdapter: ThumbRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            archiveId = it.getString(ARCHIVE_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_gallery_preview, container, false)

        GlobalScope.launch(Dispatchers.Main) {
            archive = async { DatabaseReader.getArchive(archiveId!!, context!!.filesDir) }.await()
            setGalleryView(view)
        }

        return view
    }

    override fun onDetach() {
        super.onDetach()
        Glide.get(activity!!).clearMemory()
    }

    private fun setGalleryView(view: View) {
        val listener: ThumbInteractionListener = this
        val listView: RecyclerView = view.findViewById(R.id.thumb_list)
        with(listView) {
            post {
                val dpWidth = getDpWidth(width)
                val columns = Math.floor(dpWidth / 150.0).toInt()
                layoutManager = if (columns > 1) GridLayoutManager(
                    context,
                    columns
                ) else LinearLayoutManager(context)
            }
            thumbAdapter = ThumbRecyclerViewAdapter(listener, Glide.with(activity!!), archive!!)
            adapter = thumbAdapter
            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (!listView.canScrollVertically(1) && thumbAdapter.hasMorePreviews)
                        thumbAdapter.increasePreviewCount()
                }
            })
        }
    }

    override fun onThumbSelection(page: Int) {
        val intent = Intent(activity, ReaderActivity::class.java)
        val bundle = Bundle()
        bundle.putString("id", archiveId)
        bundle.putInt("page", page)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    companion object {
        @JvmStatic
        fun createInstance(id: String) =
            GalleryPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARCHIVE_ID, id)
                }
            }
    }
}