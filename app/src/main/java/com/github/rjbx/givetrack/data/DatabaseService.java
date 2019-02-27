package com.github.rjbx.givetrack.data;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import com.github.rjbx.givetrack.AppExecutors;
import com.github.rjbx.givetrack.AppUtilities;
import com.github.rjbx.givetrack.R;
import com.github.rjbx.givetrack.AppWidget;
import com.github.rjbx.givetrack.data.entry.Giving;
import com.github.rjbx.givetrack.data.entry.Record;
import com.github.rjbx.givetrack.data.entry.Search;
import com.github.rjbx.givetrack.data.entry.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.Executor;

import static com.github.rjbx.givetrack.data.DatabaseAccessor.DEFAULT_VALUE_STR;

// TODO: Extrapolate executors from service thread if possible or consolidate logic into the former or the latter
/**
 * Handles asynchronous task requests in a service on a separate handler thread.
 */
public class DatabaseService extends IntentService {

    private static final Executor DISK_IO = AppExecutors.getInstance().getDiskIO();
    private static final Executor NETWORK_IO = AppExecutors.getInstance().getNetworkIO();

    private static final String ACTION_FETCH_SEARCH = "com.github.rjbx.givetrack.data.action.FETCH_SEARCH";
    private static final String ACTION_FETCH_GIVING = "com.github.rjbx.givetrack.data.action.FETCH_GIVING";
    private static final String ACTION_FETCH_RECORD = "com.github.rjbx.givetrack.data.action.FETCH_RECORD";
    private static final String ACTION_FETCH_USER = "com.github.rjbx.givetrack.data.action.FETCH_USER";
    private static final String ACTION_REMOVE_SEARCH = "com.github.rjbx.givetrack.data.action.REMOVE_SEARCH";
    private static final String ACTION_REMOVE_GIVING = "com.github.rjbx.givetrack.data.action.REMOVE_GIVING";
    private static final String ACTION_REMOVE_RECORD = "com.github.rjbx.givetrack.data.action.REMOVE_RECORD";
    private static final String ACTION_REMOVE_USER = "com.github.rjbx.givetrack.data.action.REMOVE_USER";     
    private static final String ACTION_RESET_SEARCH = "com.github.rjbx.givetrack.data.action.RESET_SEARCH";
    private static final String ACTION_RESET_GIVING = "com.github.rjbx.givetrack.data.action.RESET_GIVING";
    private static final String ACTION_RESET_RECORD = "com.github.rjbx.givetrack.data.action.RESET_RECORD";
    private static final String ACTION_RESET_USER = "com.github.rjbx.givetrack.data.action.RESET_USER";
    private static final String ACTION_GIVE_SEARCH = "com.github.rjbx.givetrack.data.action.GIVE_SEARCH";
    private static final String ACTION_GIVE_RECORD = "com.github.rjbx.givetrack.data.action.GIVE_RECORD";
    private static final String ACTION_RECORD_GIVE = "com.github.rjbx.givetrack.data.action.RECORD_GIVE";
    private static final String ACTION_UPDATE_GIVING = "com.github.rjbx.givetrack.data.action.UPDATE_GIVING";
    private static final String ACTION_UPDATE_CONTACT = "com.github.rjbx.givetrack.data.action.UPDATE_CONTACT";
    private static final String ACTION_UPDATE_RECORD = "com.github.rjbx.givetrack.data.action.UPDATE_RECORD";
    private static final String ACTION_UPDATE_USER = "com.github.rjbx.givetrack.data.action.UPDATE_USER";
    private static final String ACTION_RESET_DATA = "com.github.rjbx.givetrack.data.action.RESET_DATA";
    private static final String EXTRA_API_REQUEST = "com.github.rjbx.givetrack.data.extra.API_REQUEST";
    private static final String EXTRA_ITEM_VALUES = "com.github.rjbx.givetrack.data.extra.ITEM_VALUES";
    private static final String EXTRA_LIST_VALUES = "com.github.rjbx.givetrack.data.extra.LIST_VALUES";
    private static final String EXTRA_ITEM_ID = "com.github.rjbx.givetrack.data.extra.ITEM_ID";



    /**
     * Creates an {@link IntentService} instance.
     */
    public DatabaseService() { super(DatabaseService.class.getSimpleName()); }

    /**
     * Starts this service to perform action FetchSearch with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionFetchSearch(Context context, HashMap<String, String> apiRequest) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_FETCH_SEARCH);
        intent.putExtra(EXTRA_API_REQUEST, apiRequest);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action FetchGiving with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionFetchGiving(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_FETCH_GIVING);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action FetchRecord with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionFetchRecord(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_FETCH_RECORD);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action FetchUser with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionFetchUser(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_FETCH_USER);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action GiveSearch with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionGiveSearch(Context context, Search search) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_GIVE_SEARCH);
        intent.putExtra(EXTRA_ITEM_VALUES, search);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action GiveRecord with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionGiveRecord(Context context, Record record) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_GIVE_RECORD);
        intent.putExtra(EXTRA_ITEM_VALUES, record);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action RecordGive with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionRecordGive(Context context, Giving giving) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RECORD_GIVE);
        intent.putExtra(EXTRA_ITEM_VALUES, giving);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action RemoveSearch with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionRemoveSearch(Context context, Search... search) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_REMOVE_SEARCH);
        if (search.length > 1) intent.putExtra(EXTRA_LIST_VALUES, search);
        else intent.putExtra(EXTRA_ITEM_VALUES, search[0]);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action RemoveGiving with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionRemoveGiving(Context context, Giving... giving) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_REMOVE_GIVING);
        if (giving.length > 1) intent.putExtra(EXTRA_LIST_VALUES, giving);
        else intent.putExtra(EXTRA_ITEM_VALUES, giving[0]);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action RemoveRecord with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionRemoveRecord(Context context, Record... record) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_REMOVE_RECORD);
        if (record.length > 1) intent.putExtra(EXTRA_LIST_VALUES, record);
        else intent.putExtra(EXTRA_ITEM_VALUES, record[0]);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action RemoveUser with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionRemoveUser(Context context, User... user) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_REMOVE_USER);
        if (user.length > 1) intent.putExtra(EXTRA_LIST_VALUES, user);
        else intent.putExtra(EXTRA_ITEM_VALUES, user[0]);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action ResetSearch with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionResetSearch(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RESET_SEARCH);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action ResetGiving with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionResetGiving(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RESET_GIVING);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action ResetRecord with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionResetRecord(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RESET_RECORD);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action ResetUser with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionResetUser(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RESET_USER);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action UpdateGiving with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateGiving(Context context, Giving... giving) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_UPDATE_GIVING);
        intent.putExtra(EXTRA_ITEM_VALUES, giving);
        if (giving.length > 1) intent.putExtra(EXTRA_LIST_VALUES, giving);
        else intent.putExtra(EXTRA_ITEM_VALUES, giving[0]);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action UpdateRecord with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateRecord(Context context, Record... record) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_UPDATE_RECORD);
        intent.putExtra(EXTRA_ITEM_VALUES, record);
        if (record.length > 1) intent.putExtra(EXTRA_LIST_VALUES, record);
        else intent.putExtra(EXTRA_ITEM_VALUES, record[0]);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action UpdateUser with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateUser(Context context, User... user) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_UPDATE_USER);
        intent.putExtra(EXTRA_ITEM_VALUES, user);
        if (user.length > 1) intent.putExtra(EXTRA_LIST_VALUES, user);
        else intent.putExtra(EXTRA_ITEM_VALUES, user[0]);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action UpdateFrequency with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateFrequency(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_UPDATE_GIVING);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action ResetData with the given parameters.
     * If the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionResetData(Context context) {
        Intent intent = new Intent(context, DatabaseService.class);
        intent.setAction(ACTION_RESET_DATA);
        context.startService(intent);
    }

    /**
     * Syncs data inside a worker thread on requests to process {@link Intent}.
     * @param intent launches this {@link IntentService}.
     */
    @Override protected void onHandleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String action = intent.getAction();
        switch (action) {
            case ACTION_FETCH_SEARCH:
                final HashMap fetchSearchMap = (HashMap) intent.getSerializableExtra(EXTRA_API_REQUEST);
                handleActionFetchSearch(fetchSearchMap);
                break;
            case ACTION_FETCH_GIVING:
                handleActionFetchGiving();
                break;
            case ACTION_FETCH_RECORD:
                handleActionFetchRecord();
                break;
            case ACTION_GIVE_SEARCH:
                handleActionGiveSearch(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_GIVE_RECORD:
                handleActionGiveRecord(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_RECORD_GIVE:
                handleActionRecordGive(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_REMOVE_SEARCH:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionRemoveSearch(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), Search.class));
                else handleActionRemoveSearch(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_REMOVE_GIVING:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionRemoveGiving(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), Giving.class));
                else handleActionRemoveGiving(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_REMOVE_RECORD:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionRemoveRecord(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), Record.class));
                else handleActionRemoveRecord(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_REMOVE_USER:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionRemoveUser(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), User.class));
                else handleActionRemoveUser(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_RESET_SEARCH:
                handleActionResetSearch();
                break;
            case ACTION_RESET_GIVING:
                handleActionResetGiving();
                break;
            case ACTION_RESET_RECORD:
                handleActionResetRecord();
                break;
            case ACTION_RESET_USER:
                handleActionResetUser();
                break;
            case ACTION_UPDATE_CONTACT:
                break;
            case ACTION_UPDATE_GIVING:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionUpdateGiving(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), Giving.class));
                else handleActionUpdateGiving(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_UPDATE_RECORD:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionUpdateRecord(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), Record.class));
                else handleActionUpdateRecord(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_UPDATE_USER:
                if (intent.hasExtra(EXTRA_LIST_VALUES))
                    handleActionUpdateUser(AppUtilities.getTypedArrayFromParcelables(intent.getParcelableArrayExtra(EXTRA_LIST_VALUES), User.class));
                else handleActionUpdateUser(intent.getParcelableExtra(EXTRA_ITEM_VALUES));
                break;
            case ACTION_RESET_DATA: handleActionResetData();
        }
        // TODO: Decide whether AppWidget refresh should occur here, inside accessor local update helpers or ContentProvider notify helper
        AppWidget.refresh(this);
    }

    /**
     * Handles action FetchSearch in the provided background thread with the provided parameters.
     */
    private void handleActionFetchSearch(HashMap apiRequest) {

        NETWORK_IO.execute(() -> DatabaseAccessor.fetchSearch(this, apiRequest));

        
    }

    /**
     * Handles action FetchGiving in the provided background thread.
     */
    private void handleActionFetchGiving() {

        NETWORK_IO.execute(() -> DatabaseAccessor.fetchGiving(this));
    }

    /**
     * Handles action FetchRecord in the provided background thread.
     */
    private void handleActionFetchRecord() {
        NETWORK_IO.execute(() -> DatabaseAccessor.fetchRecord(this));
        
    }

    private void handleActionFetchUser() {
        NETWORK_IO.execute(() -> DatabaseAccessor.fetchUser(this));
    }

    /**
     * Handles action GiveSearch in the provided background thread with the provided parameters.
     */
    private void handleActionGiveSearch(Search search) {

        NETWORK_IO.execute(() -> {

            float impact = 0f;
            int frequency = 0;

            List<Record> records = DatabaseAccessor.getRecord(this, null);
            for (Record record : records) {
                if (record.getEin().equals(search.getEin())) {
                    impact += Float.parseFloat(record.getImpact());
                    frequency++;
                }
            }

            List<Giving> givings = DatabaseAccessor.getGiving(this, null);
            int size = givings.size();
            double percent = size == 1 ? size : 0d;
            Giving giving = Giving.fromSuper(search);
            giving.setFrequency(frequency);
            giving.setPercent(percent);
            giving.setImpact(String.format(Locale.getDefault(), "%.2f", impact));

            String phoneNumber = urlToPhoneNumber(giving.getNavigatorUrl());
            giving.setPhone(phoneNumber);

            String emailAddress = urlToEmailAddress(giving.getHomepageUrl());
            giving.setEmail(emailAddress);

            DatabaseAccessor.addGiving(this, giving);
        });

        AppWidget.refresh(this);
    }

    private void handleActionGiveRecord(Record record) {
        int code = record.hashCode();
    }

    private void handleActionRecordGive(Giving giving) {

         long time = System.currentTimeMillis();
         Record record = Record.fromSuper(giving.getSuper());
         record.setStamp(time);
         record.setTime(time);
         DatabaseAccessor.addRecord(this, record);
    }

    /**
     * Handles action RemoveSearch in the provided background thread with the provided parameters.
     */
    private void handleActionRemoveSearch(Search... searches) {

        DISK_IO.execute(() -> DatabaseAccessor.removeSearch(this, searches));

        AppWidget.refresh(this);
    }

    /**
     * Handles action RemoveGiving in the provided background thread with the provided parameters.
     */
    private void handleActionRemoveGiving(Giving... givings) {

        DISK_IO.execute(() -> DatabaseAccessor.removeGiving(this, givings));

        AppWidget.refresh(this);
    }

    /**
     * Handles action RemoveRecord in the provided background thread with the provided parameters.
     */
    private void handleActionRemoveRecord(Record... records) {

        DISK_IO.execute(() -> {
            DatabaseAccessor.removeRecord(this, records);

            List<Giving> givings = DatabaseAccessor.getGiving(this, null);
            for (Giving giving : givings) {
                for (Record record : records) {
                    if (record.getEin().equals(giving.getEin())) {
                        giving.setFrequency(giving.getFrequency() - 1);
                        float impact = Float.parseFloat(giving.getImpact()) - Float.parseFloat(record.getImpact());
                        giving.setImpact(String.format(Locale.getDefault(), "%.2f", impact));
                        DatabaseAccessor.addGiving(this, giving);
                        break;
                    }
                }
            }
        });

        AppWidget.refresh(this);
    }

    private void handleActionRemoveUser(User... users) {
        
        DISK_IO.execute(() -> {
               
            List<Search> searches = DatabaseAccessor.getSearch(this, null);
            List<Giving> givings = DatabaseAccessor.getGiving(this, null);
            List<Record> records = DatabaseAccessor.getRecord(this, null);
            
            for (User user : users) {
                for (Search search : searches) if (!search.getUid().equals(user.getUid())) DatabaseAccessor.removeSearch(this, search);
                for (Giving giving : givings) if (!giving.getUid().equals(user.getUid())) DatabaseAccessor.removeGiving(this, giving);
                for (Record record : records) if (!record.getUid().equals(user.getUid())) DatabaseAccessor.removeRecord(this, record);
            } DatabaseAccessor.removeUser(this, users);
        });
        
    }

    /**
     * Handles action ResetSearch in the provided background thread with the provided parameters.
     */
    private void handleActionResetSearch() {

        DISK_IO.execute(() -> DatabaseAccessor.removeSearch(this, (Search) null));

        AppWidget.refresh(this);
    }

    /**
     * Handles action ResetGiving in the provided background thread with the provided parameters.
     */
    private void handleActionResetGiving() {

        DISK_IO.execute(() -> DatabaseAccessor.removeGiving(this, (Giving) null));

        AppWidget.refresh(this);
    }

    /**
     * Handles action ResetRecord in the provided background thread with the provided parameters.
     */
    private void handleActionResetRecord() {

        DISK_IO.execute(() -> {
            DatabaseAccessor.removeRecord(this, (Record) null);
            List<Giving> givings = DatabaseAccessor.getGiving(this, null);
            for (Giving giving : givings) {
                giving.setImpact("0");
                giving.setFrequency(0);
            } DatabaseAccessor.addGiving(this, givings.toArray(new Giving[givings.size()]));
        });

        AppWidget.refresh(this);
    }

    private void handleActionResetUser() {
        DISK_IO.execute(() -> {
            DatabaseAccessor.removeSearch(this, (Search) null);
            DatabaseAccessor.removeGiving(this, (Giving) null);
            DatabaseAccessor.removeRecord(this, (Record) null);
            DatabaseAccessor.removeUser(this, (User) null);
        });
    }

    /**
     * Handles action UpdatePercent in the provided background thread with the provided parameters.
     */
    private void handleActionUpdateGiving(Giving... givings) {

        boolean recalibrate = givings[0].getPercent() == -1d;
        // TODO: Dereference array element to update
        if (recalibrate) for (Giving giving : givings) giving.setPercent(1d / givings.length);

        DISK_IO.execute(() -> DatabaseAccessor.addGiving(this, givings));


        AppWidget.refresh(this);
    }

    private void handleActionUpdateRecord(Record... records) {}

    private void handleActionUpdateUser(User... user) {
        DISK_IO.execute(() -> DatabaseAccessor.addUser(this, user));

        AppWidget.refresh(this);
    }


    /**
     * Handles action ResetData in the provided background thread.
     */
    private void handleActionResetData() {

       DISK_IO.execute(() -> {
            DatabaseAccessor.removeSearch(this, (Search) null);
            DatabaseAccessor.removeGiving(this, (Giving) null);
            DatabaseAccessor.removeRecord(this, (Record) null);
            DatabaseAccessor.removeUser(this, (User) null);
        });
        PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();
        AppWidget.refresh(this);
    }

    private String urlToEmailAddress(String url) {
        String emailAddress = DEFAULT_VALUE_STR;
        try {
            if (url.isEmpty()) return DEFAULT_VALUE_STR;
            Document homepage = Jsoup.connect(url).get();
            Elements homeInfo = homepage.select("a");
            List<String> emailAddresses;
            List<String> visitedLinks = new ArrayList<>();
            emailAddresses = parseKeysFromPages(url, homeInfo, "Donate", visitedLinks, "mailto:");
            if (emailAddresses.isEmpty())
                emailAddresses = parseKeysFromPages(url, homeInfo, "Contact", visitedLinks, "mailto:");
            if (emailAddresses.isEmpty())
                emailAddresses = parseKeys(homeInfo, "mailto:", null, " ");
            if (!emailAddresses.isEmpty()) {
                for (String address : emailAddresses) Timber.v("Email: %s", address);
                emailAddress = emailAddresses.get(0);
            }
        } catch (IOException e) { Timber.e(e);
        } return emailAddress;
    }

    private String urlToPhoneNumber(String url) {
        String phoneNumber = DEFAULT_VALUE_STR;
        try {
            Document webpage = Jsoup.connect(url).get();
            Elements info = webpage.select("div[class=cn-appear]");
            List<String> phoneNumbers;
            phoneNumbers = parseKeys(info, "tel:", 15, "[^0-9]");
            if (!phoneNumbers.isEmpty()) {
                for (String number : phoneNumbers) Timber.v("Phone: %s", number);
                phoneNumber = phoneNumbers.get(0);
            }
        } catch (IOException e) { Timber.e(e);
        } return phoneNumber;
    }

    private List<String> parseKeysFromPages(String homeUrl, Elements anchors, String pageName, List<String> visitedLinks, String key) throws IOException {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Element anchor = anchors.get(i);
            if (anchor.text().contains(pageName)) {
                if (!anchor.hasAttr("href")) continue;
                String pageLink = anchors.get(i).attr("href");
                if (pageLink.startsWith("/")) pageLink = homeUrl + pageLink.substring(1);
                if (visitedLinks.contains(pageLink)) continue;
                else visitedLinks.add(pageLink);
                Document page = Jsoup.connect(pageLink).get();
                Elements pageAnchors = page.select("a");

                emails.addAll(parseKeys(pageAnchors, key, null, " "));
            }
        }
        return emails;
    }

    private List<String> parseKeys(Elements anchors, String key, @Nullable Integer endIndex, @Nullable String removeRegex) {
        List<String> values = new ArrayList<>();
        for (int j = 0; j < anchors.size(); j++) {
            Element anchor = anchors.get(j);
            if (anchor.hasAttr("href")) {
                if (anchor.attr("href").contains(key))
                    values.add(anchor.attr("href").split(key)[1].trim());
            } else if (anchor.text().contains(key)) {
                String text = anchor.text();
                String value = text.split(key)[1].trim();
                if (endIndex != null) value = value.substring(0, endIndex);
                if (removeRegex != null) value = value.replaceAll(removeRegex, "");
                values.add(value);
            }
        }
        return values;
    }
}