package com.onsoftwares.zensource.fragments;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.onsoftwares.zensource.R;
import com.onsoftwares.zensource.activities.ZenCardZoomActivity;
import com.onsoftwares.zensource.adapters.HomeCardRecyclerAdapter;
import com.onsoftwares.zensource.interfaces.OnLoadMoreListener;
import com.onsoftwares.zensource.interfaces.OnZenCardAction;
import com.onsoftwares.zensource.models.ZenCardModel;
import com.onsoftwares.zensource.utils.ZenCardUtils;
import com.onsoftwares.zensource.utils.ZenSourceUtils;
import com.onsoftwares.zensource.utils.httputil.HttpUtil;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

public class LikedQuotesFragment extends Fragment implements OnLoadMoreListener, OnZenCardAction {

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView numberLikedQuotes;
    private List<ZenCardModel> likedList;
    private String likedQuoteIds;
    private HomeCardRecyclerAdapter recyclerAdapter;
    private int page = 1;
    private int perPage = 5;

    public LikedQuotesFragment() {
        // Required empty public constructor
        likedList = new ArrayList<ZenCardModel>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_liked_quotes, container, false);

        numberLikedQuotes = (TextView) view.findViewById(R.id.number_liked_quotes);

        refreshNumberLiked();

        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        recyclerAdapter = new HomeCardRecyclerAdapter(getContext(), likedList, recyclerView);

        recyclerView.setAdapter(recyclerAdapter);

        recyclerAdapter.setOnLoadMore(this);
        recyclerAdapter.setOnZenCardAction(this);

        refreshData();

        return view;
    }


    private void refreshData() {

        refreshNumberLiked();

        page = 1;

        if (likedQuoteIds != null && likedQuoteIds.length() > 0) {

            // Request for the data of the recycler view
            progressBar.setVisibility(View.VISIBLE);
            recyclerAdapter.setLoading(true);
            HttpUtil.Builder httpBuilder = HttpUtil.Builder()
                    .withUrl("http://zensource-dev.sa-east-1.elasticbeanstalk.com/api/zen/images")
                    .addQueryParameter("page", page + "")
                    .addQueryParameter("ids", likedQuoteIds)
                    .addQueryParameter("l", ZenSourceUtils.getLanguageAPICode(getContext()));

            httpBuilder.withConverter(new ZenCardModel())
                    .ifSuccess(new HttpUtil.CallbackConverted<List<ZenCardModel>>() {
                        @Override
                        public void callback(final List<ZenCardModel> list) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    // Setting all as liked
                                    for (int i = 0; i < list.size(); i++) {
                                        list.get(i).setLiked(true);
                                    }

                                    likedList.clear();
                                    page++;

                                    likedList.addAll(list);
                                    progressBar.setVisibility(View.INVISIBLE);
                                    recyclerView.setVisibility(View.VISIBLE);
                                    recyclerAdapter.setLoading(false);

                                    if (likedList.size() == 0) {
                                        recyclerView.setVisibility(View.INVISIBLE);
                                    }

                                    recyclerView.getAdapter().notifyDataSetChanged();
                                    recyclerView.getLayoutManager().scrollToPosition(0);
                                }
                            });
                        }
                    })
                    .makeGet();
        } else {
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onLoadMore() {
        if (page != 0) {

            likedList.add(null);

            recyclerAdapter.notifyItemInserted(likedList.size() - 1);

            HttpUtil.Builder httpBuilder = HttpUtil.Builder()
                    .withUrl("http://zensource-dev.sa-east-1.elasticbeanstalk.com/api/zen/images")
                    .addQueryParameter("page", (page++) + "")
                    .addQueryParameter("ids", likedQuoteIds)
                    .addQueryParameter("l", ZenSourceUtils.getLanguageAPICode(getContext()));

            httpBuilder
                    .withConverter(new ZenCardModel())
                    .ifSuccess(new HttpUtil.CallbackConverted<List<ZenCardModel>>() {
                        @Override
                        public void callback(final List<ZenCardModel> list) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    //Remove loading item
                                    likedList.remove(likedList.size() - 1);
                                    recyclerAdapter.notifyItemRemoved(likedList.size());

                                    recyclerAdapter.setLoading(false);

                                    // Setting all as liked
                                    for (int i = 0; i < list.size(); i++) {
                                        list.get(i).setLiked(true);
                                    }

                                    likedList.addAll(list);
                                    recyclerView.getAdapter().notifyDataSetChanged();
                                    progressBar.setVisibility(View.INVISIBLE);

                                    if (list.size() == 0) page = 0;
                                }
                            });
                        }
                    })
                    .makeGet();
        }
    }

    @Override
    public void onLike(final ZenCardModel z, int pos) {
       // Do nothing
    }

    @Override
    public void onDislike(ZenCardModel z, final int pos) {
        // Http Put to like the post
        ZenCardUtils.dislikeZenQuote(z, new HttpUtil.CallbackVoid() {
            @Override
            public void callback() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(recyclerView, getResources().getString(R.string.dislike_success), Snackbar.LENGTH_SHORT).show();
                        recyclerAdapter.deleteItem(pos);
                        refreshNumberLiked();
                    }
                });
            }
        });

        // Update the model
        z.setDisliked(true);
        z.setDislikes(z.getDislikes() + 1);

        if (z.isLiked()) {
            z.setLiked(false);
            z.setLikes(z.getLikes() - 1);
        }

        String likedQuotesStr = ZenSourceUtils.getSharedPreferencesValue(getActivity(), getString(R.string.shared_preferences_liked), String.class);
        HashSet<String> likedQuotes = likedQuotesStr == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(likedQuotesStr.split(";")));

        String dislikedQuotesStr = ZenSourceUtils.getSharedPreferencesValue(getActivity(), getString(R.string.shared_preferences_disliked), String.class);
        HashSet<String> dislikedQuotes = likedQuotesStr == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(dislikedQuotesStr.split(";")));

        // If it was liked, it is being disliked now and vice-versa
        String id = z.getId() + "";

        if (likedQuotes.contains(id))
            likedQuotes.remove(id);

        if (dislikedQuotes.contains(id))
            dislikedQuotes.remove(id);
        else
            dislikedQuotes.add(id);

        ZenSourceUtils.setSharedPreferenceValue(getActivity(), getString(R.string.shared_preferences_liked), TextUtils.join(";", likedQuotes), String.class);
        ZenSourceUtils.setSharedPreferenceValue(getActivity(), getString(R.string.shared_preferences_disliked), TextUtils.join(";", dislikedQuotes), String.class);
    }

    @Override
    public void onCardClick(ZenCardModel z, View v) {
        Intent intent = new Intent(getActivity(), ZenCardZoomActivity.class);

        intent.putExtra("image64encoded", z.getImage64encoded());

        String transitionName = getActivity().getString(R.string.transition_zoom_card);

        ImageView startView = (ImageView) v.findViewById(R.id.home_card_image);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation((Activity) getActivity(),
                startView,   // Starting view
                transitionName    // The String
        );
        //Start the Intent
        ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
    }

    @Override
    public void onShare(ImageView imageView) {
        // Saving image on Cache
        try {
            File cachePath = new File(getActivity().getCacheDir(), "images");
            cachePath.mkdirs(); // don't forget to make the directory
            FileOutputStream stream = new FileOutputStream(cachePath + "/" + ZenSourceUtils.IMAGE_NAME_ON_CACHE + ".png"); // overwrites this image every time
            ((BitmapDrawable)imageView.getDrawable()).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Sharing
        File imagePath = new File(getActivity().getCacheDir(), "images");
        File newFile = new File(imagePath,  ZenSourceUtils.IMAGE_NAME_ON_CACHE + ".png");
        Uri contentUri = FileProvider.getUriForFile(getActivity(), ZenSourceUtils.PACKKAGE_NAME + ".fileprovider", newFile);

        if (contentUri != null) {

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setDataAndType(contentUri, getActivity().getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            startActivity(Intent.createChooser(shareIntent, "Choose an app"));
        }
    }

    private void refreshNumberLiked() {
        likedQuoteIds = ZenSourceUtils.getSharedPreferencesValue(getActivity(), getString(R.string.shared_preferences_liked), String.class);
        if (likedQuoteIds != null) {
            likedQuoteIds = likedQuoteIds.replace(';', ',');
            int quotesNumber = 0;
            Toast.makeText(getContext(), likedQuoteIds, Toast.LENGTH_SHORT).show();
            if (likedQuoteIds.length() > 0) quotesNumber = likedQuoteIds.split(",").length;
            numberLikedQuotes.setText( quotesNumber + " " + getResources().getString(R.string.liked_number));
        }
    }
}
