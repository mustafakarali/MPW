package it.angelic.noobpoolstats;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import it.angelic.noobpoolstats.model.MyDateTypeAdapter;
import it.angelic.noobpoolstats.model.MyTimeStampTypeAdapter;
import it.angelic.noobpoolstats.model.db.NoobPoolDbHelper;
import it.angelic.noobpoolstats.model.jsonpojos.wallet.Payment;
import it.angelic.noobpoolstats.model.jsonpojos.wallet.Wallet;

public class PaymentsActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static final String minerStatsUrl = "http://www.noobpool.com/api/accounts/";
    private static final SimpleDateFormat yearFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
    private static final SimpleDateFormat yearFormatExtended = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private String minerAddr;

    private GsonBuilder builder;
    private TextView textViewWalletValue;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payments);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        minerAddr = pref.getString("wallet_addr", null);


        final NoobPoolDbHelper mDbHelper = new NoobPoolDbHelper(this);
        builder = new GsonBuilder();

        textViewWalletValue = (TextView) findViewById(R.id.textViewWalletValue);
        textViewWalletValue.setText(minerAddr);

        builder.registerTypeAdapter(Date.class, new MyDateTypeAdapter());
        builder.registerTypeAdapter(Calendar.class, new MyTimeStampTypeAdapter());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(this.getTitle());
        setSupportActionBar(toolbar);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Data request sent", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                issueRefresh(mDbHelper, builder, minerStatsUrl + minerAddr);
            }
        });


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view_payment);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_payment);

    }

    @Override
    protected void onStart() {
        super.onStart();
        final NoobPoolDbHelper mDbHelper = new NoobPoolDbHelper(this);
        issueRefresh(mDbHelper, builder, minerStatsUrl + minerAddr);
    }

    private void issueRefresh(final NoobPoolDbHelper mDbHelper, final GsonBuilder builder, String url) {
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                url, null,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(MainActivity.TAG, response.toString());

                        Gson gson = builder.create();
                        // Register an adapter to manage the date types as long values
                        Wallet retrieved = gson.fromJson(response.toString(), Wallet.class);
                        mDbHelper.logWalletStats(retrieved);
                        //dati semi grezzi

                        drawPaymentsTable(retrieved);

                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(MainActivity.TAG, "Error: " + error.getMessage());
                // hide the progress dialog
            }
        });

        // Adding request to request queue
        NoobJSONClientSingleton.getInstance(this).addToRequestQueue(jsonObjReq);
    }

    private void drawPaymentsTable(Wallet retrieved) {
        TableLayout minersTable = (TableLayout) findViewById(R.id.tableLayoutPayments);
        minersTable.removeAllViews();
        //table header
        TableRow row = (TableRow) LayoutInflater.from(PaymentsActivity.this).inflate(R.layout.row_payment, null);
        (row.findViewById(R.id.buttonPay)).setVisibility(View.INVISIBLE);
        minersTable.addView(row);
        for (final Payment thispay : retrieved.getPayments()) {

            TableRow rowt = (TableRow) LayoutInflater.from(PaymentsActivity.this).inflate(R.layout.row_payment, null);
            ((TextView) rowt.findViewById(R.id.textViewWorkerName)).setText(yearFormatExtended.format(thispay.getTimestamp()));
            ((TextView) rowt.findViewById(R.id.textViewWorkerHashrate)).setText(Utils.formatEthCurrency(thispay.getAmount()));
            ((TextView) rowt.findViewById(R.id.buttonPay)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("https://etherscan.io/tx/" + thispay.getTx()));
                    startActivity(i);
                }
            });

            minersTable.addView(rowt);
        }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
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

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            Intent opzioni = new Intent(PaymentsActivity.this, MainActivity.class);
            startActivity(opzioni);
        } else if (id == R.id.nav_wallet) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            String minerAddr = pref.getString("wallet_addr", null);

            if (minerAddr == null || minerAddr.length() == 0) {
                Snackbar.make(textViewWalletValue, "Insert Public Address in Preferences", Snackbar.LENGTH_LONG)
                        .setAction("GO", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent miner = new Intent(PaymentsActivity.this, SettingsActivity.class);
                                startActivity(miner);
                            }
                        }).show();
            } else {
                Intent miner = new Intent(this, MinerActivity.class);
                startActivity(miner);
            }
        } else if (id == R.id.nav_payment) {
            //siamo gia qui
        } else if (id == R.id.nav_send) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse("https://telegram.me/Noobpool"));
            final String appName = "org.telegram.messenger";

            if (Utils.isAppAvailable(this.getApplicationContext(), appName))
                i.setPackage(appName);

            startActivity(i);
        } else if (id == R.id.nav_support) {
            Intent opzioni = new Intent(PaymentsActivity.this, EncourageActivity.class);
            startActivity(opzioni);
        } else {
            Snackbar.make(textViewWalletValue, "Function not implemented yet. Please encourage development", Snackbar.LENGTH_LONG)
                    .setAction("WHAT?", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent miner = new Intent(PaymentsActivity.this, EncourageActivity.class);
                            startActivity(miner);
                        }
                    }).show();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}