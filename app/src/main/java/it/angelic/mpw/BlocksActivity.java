package it.angelic.mpw;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import it.angelic.mpw.model.CurrencyEnum;
import it.angelic.mpw.model.MyDateTypeAdapter;
import it.angelic.mpw.model.MyTimeStampTypeAdapter;
import it.angelic.mpw.model.PoolEnum;
import it.angelic.mpw.model.db.NoobPoolDbHelper;
import it.angelic.mpw.model.jsonpojos.blocks.Block;
import it.angelic.mpw.model.jsonpojos.blocks.Matured;

import static it.angelic.mpw.Constants.BLOCKS_URL;

public class BlocksActivity extends DrawerActivity {
    private TextView textViewBlocksTitle;
    private RecyclerView mRecyclerView;
    private BlockAdapter mAdapter;

    private NoobPoolDbHelper mDbHelper;
    private TextView textViewMaxBlockTimeValue;
    private TextView textViewMinBlockTimeValue;
    private TextView textViewMeanBlockTimeValue;
    private TextView textViewBlockTimeStdDevValue;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocks);


        mDbHelper = new NoobPoolDbHelper(this,mPool,mCur);
        textViewBlocksTitle = findViewById(R.id.textViewBlocksTitle);

        textViewMaxBlockTimeValue = findViewById(R.id.textViewMaxBlockTimeValue);
        textViewMinBlockTimeValue = findViewById(R.id.textViewMinBlockTimeValue);
        textViewMeanBlockTimeValue = findViewById(R.id.textViewMeanBlockTimeValue);
        textViewBlockTimeStdDevValue = findViewById(R.id.textViewBlockTimeStdDevValue);

        GsonBuilder builder = new GsonBuilder();
        mRecyclerView = (RecyclerView) findViewById(R.id.blocks_recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        LinearLayoutManager mLayoutManager = new GridLayoutManager(this, getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 1);
        mRecyclerView.setLayoutManager(mLayoutManager);

        //gestione UNIX time lungo e non
        builder.registerTypeAdapter(Date.class, new MyDateTypeAdapter());
        builder.registerTypeAdapter(Calendar.class, new MyTimeStampTypeAdapter());

        issueRefresh(mDbHelper, builder);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationViewInterna = (NavigationView) findViewById(R.id.navigation_view);
        navigationViewInterna.setNavigationItemSelectedListener(this);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view_blocks);
        navigationView.setNavigationItemSelectedListener(this);
        navigationViewInterna.setCheckedItem(R.id.nav_blocks);
        View headerLayout = navigationViewInterna.getHeaderView(0);

        Utils.fillEthereumStats(this, mDbHelper, navigationView,mPool);
    }

    private void issueRefresh(final NoobPoolDbHelper mDbHelper, final GsonBuilder builder) {

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                "http://"+ mCur.name()+"."+mPool.getWebRoot() + BLOCKS_URL, null,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(final JSONObject response) {
                        Log.d(Constants.TAG, response.toString());
                        textViewBlocksTitle.post(new Runnable() {
                            @Override
                            public void run() {
                                Gson gson = builder.create();
                                // Register an adapter to manage the date types as long values
                                Block retrieved = gson.fromJson(response.toString(), Block.class);
                                textViewBlocksTitle.setText(retrieved.getMaturedTotal() + " "+mCur.toString()+" "+"Blocks found on "+ mPool.toString());

                                Matured[] maturi = new Matured[retrieved.getMaturedTotal()];

                                SummaryStatistics sts = doApacheMath(retrieved.getMatured());
                                retrieved.getMatured().toArray(maturi);

                                if (mAdapter == null) {
                                    mAdapter = new BlockAdapter(maturi,mCur);
                                    mRecyclerView.setAdapter(mAdapter);
                                }
                                textViewMeanBlockTimeValue.setText(Utils.getScaledTime((long) sts.getMean() / 1000));
                                textViewMaxBlockTimeValue.setText(Utils.getScaledTime((long) sts.getMax() / 1000));
                                textViewMinBlockTimeValue.setText(Utils.getScaledTime((long) sts.getMin() / 1000));
                                textViewBlockTimeStdDevValue.setText(Utils.getScaledTime((long) sts.getStandardDeviation() / 1000));

                                mAdapter.setBlocksArray(maturi);
                                mAdapter.notifyDataSetChanged();
                            }
                        });


                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(Constants.TAG, "Error: " + error.getMessage());
                // hide the progress dialog
            }
        });

        // Adding request to request queue
        NoobJSONClientSingleton.getInstance(this).addToRequestQueue(jsonObjReq);
    }

    private SummaryStatistics doApacheMath(List<Matured> maturi) {
        ArrayList<Matured> revElements = new ArrayList<>(maturi);
        Collections.reverse(revElements);
        long[] intervals = new long[maturi.size()];
        Date prevDate = revElements.get(0).getTimestamp();
        int i = 0;
        //parto da 1 a calcolare il 1o intervallo
        for (int t = 1; t < revElements.size(); t++) {
            Date curDate = revElements.get(t).getTimestamp();
            intervals[i++] = curDate.getTime() - prevDate.getTime();
            prevDate = curDate;
        }
        //a oggi, calcolo ultimo intervallo aperto
        intervals[maturi.size() - 1] = (new Date().getTime() - revElements.get(maturi.size() - 1).getTimestamp().getTime());


        // Get a DescriptiveStatistics instance
        SummaryStatistics stats = new SummaryStatistics();

        // Add the data from the array
        for (int im = 0; im < intervals.length; im++) {
            stats.addValue(intervals[im]);
        }

        // Compute some statistics
        // double mean = stats.getMean();
        // double std = stats.getStandardDeviation();
        // double median = stats.getPercentile(50);
        return stats;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent opzioni = new Intent(this, SettingsActivity.class);
            startActivity(opzioni);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

