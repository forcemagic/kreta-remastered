package com.speedyblur.kretaremastered.fragments;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.speedyblur.kretaremastered.R;
import com.speedyblur.kretaremastered.activities.MainActivity;
import com.speedyblur.kretaremastered.adapters.BulletinAdapter;
import com.speedyblur.kretaremastered.models.Bulletin;
import com.speedyblur.kretaremastered.shared.Common;
import com.speedyblur.kretaremastered.shared.DataStore;
import com.speedyblur.kretaremastered.shared.DecryptionException;
import com.speedyblur.kretaremastered.shared.IDataStore;
import com.speedyblur.kretaremastered.shared.IRefreshHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainBulletinsFragment extends Fragment {
    private BulletinAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.frag_main_bulletins, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final MainActivity parent = (MainActivity) getActivity();
        final ListView aList = parent.findViewById(R.id.announcementsList);

        adapter = new BulletinAdapter(getContext(), new ArrayList<Bulletin>());

        updateFromDS(parent);
        parent.setRefreshHandler(new IRefreshHandler() {
            @Override
            public void onRefreshComplete() {
                updateFromDS(parent);
            }
        });

        // Setup view
        aList.setEmptyView(parent.findViewById(R.id.announcementsEmptyView));
        aList.setAdapter(adapter);
        aList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {

            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                parent.setSwipeRefreshEnabled(!absListView.canScrollVertically(-1));
            }
        });
        aList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final Bulletin a = (Bulletin) adapterView.getItemAtPosition(i);
                if (!a.isSeen()) {
                    try {
                        DataStore ds = new DataStore(getContext(), parent.p.getCardid(), Common.SQLCRYPT_PWD);
                        ArrayList<Bulletin> toUpsert = new ArrayList<>();
                        toUpsert.add(new Bulletin(a.getTeacher(), a.getContent(), a.getDate(), true));
                        ds.upsertAnnouncementsData(toUpsert, true);
                        ds.close();
                    } catch (DecryptionException e) {e.printStackTrace();}
                }
                View dialView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_bulletin_details, null);

                TextView mAuthor = dialView.findViewById(R.id.announcementInfoAuthor);
                TextView mDate = dialView.findViewById(R.id.announcementInfoDate);
                TextView mContent = dialView.findViewById(R.id.announcementInfoContent);

                mAuthor.setText(a.getTeacher());
                mDate.setText(new SimpleDateFormat("yyyy. MMM. dd.", Locale.getDefault()).format(new Date((long) a.getDate()*1000)));
                mContent.setText(a.getContent());

                new AlertDialog.Builder(getContext()).setView(dialView)
                        .setPositiveButton(R.string.dialog_close, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (!a.isSeen()) updateFromDS(parent);
                                dialogInterface.dismiss();
                            }
                        }).show();
            }
        });
    }

    private void updateFromDS(MainActivity parent) {
        DataStore.asyncQuery(parent, parent.p.getCardid(), Common.SQLCRYPT_PWD, new IDataStore<ArrayList<Bulletin>>() {
            @Override
            public ArrayList<Bulletin> requestFromStore(DataStore ds) {
                return ds.getAnnouncementsData();
            }

            @Override
            public void processRequest(ArrayList<Bulletin> data) {
                adapter.clear();
                adapter.addAll(data);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onDecryptionFailure(DecryptionException e) {
                e.printStackTrace();
            }
        });
    }
}
