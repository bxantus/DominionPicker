package ca.marklauman.dominionpicker;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import ca.marklauman.dominionpicker.database.DataDb;
import ca.marklauman.dominionpicker.database.Provider;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.tools.Utils;
import static ca.marklauman.dominionpicker.SupplyShuffler.ShuffleSupply;
import static ca.marklauman.dominionpicker.SupplyShuffler.ShuffleResult;

import java.util.Calendar;

/** This task is used to shuffle new supplies.
 *  It reads the current setting configuration when called, and attempts to create a supply
 *  with the available cards.
 *  The result of the shuffle is communicated to the main activity with broadcast intents.
 *  @author Mark Lauman */
public class SupplyShufflerTask extends AsyncTask<Void, Void, Void> {
    /** When the shuffler is done, an intent of this type broadcasts
     *  the results back to the activity.                         */
    public static final String MSG_INTENT = "ca.marklauman.dominionpicker.shuffler";
    /** The extra in the result intent containing the result id.
     *  Will be a constant defined by this class starting with "RES_" */
    public static final String MSG_RES = "result";
    /** Extra containing card shortfall in the event of {@link #RES_MORE}.
     *  String formatted as "X/Y" cards.  */
    public static final String MSG_SHORT = "shortfall";
    /** The extra containing the supply id. Only available on {@link #RES_OK}. */
    public static final String MSG_SUPPLY_ID ="supply";

    /** Shuffle succeeded. Supply available in {@link #MSG_SUPPLY_ID} */
    public static final int RES_OK = 0;
    /** Shuffle failed. No young witch targets. */
    public static final int RES_NO_YW = 1;
    /** Shuffle failed. Insufficient kingdom cards.
     *  Shortfall in {@link #MSG_SHORT}. */
    public static final int RES_MORE = 2;
    /** Shuffle cancelled by outside source. */
    @SuppressWarnings("WeakerAccess")
    public static final int RES_CANCEL = 100;

    @Override
    protected Void doInBackground(Void... ignored) {
        // Create the supply we will populate, and do a check for minKingdoms == 0
        SharedPreferences pref = Pref.get(Pref.getAppContext());
        final int minKingdom = pref.getInt(Pref.LIMIT_SUPPLY, 10);
        final int maxSpecial = pref.getInt(Pref.LIMIT_EVENTS, 2);
        ShuffleSupply supply = new ShuffleSupply(minKingdom, maxSpecial, new SupplyShuffler.KingdomInsertWithYWStrategy());

        ShuffleResult result = SupplyShuffler.fillSupply(supply, this);
        switch (result)
        {
            case SUCCESS:   successfulResult(supply); return null;
            case CANCELLED: return cancelResult();
        }

        // Shuffle has failed. (assume result == ShuffleResult.FAILED)
        Intent msg = new Intent(MSG_INTENT);
        int shortfall = supply.getShortfall();
        // Shuffle failed because there were no bane cards for the young witch
        if(supply.waitingForBane() && shortfall == 1) {
            msg.putExtra(MSG_RES, RES_NO_YW);
            return sendMsg(msg);
        } else {
            msg.putExtra(MSG_RES, RES_MORE);
            msg.putExtra(MSG_SHORT, supply.minKingdom-shortfall+"/"+supply.minKingdom);
            return sendMsg(msg);
        }
    }

    /** The shuffle was cancelled prematurely. */
    private Void cancelResult() {
        Intent cancel = new Intent(MSG_INTENT);
        cancel.putExtra(MSG_RES, RES_CANCEL);
        return sendMsg(cancel);
    }


    /** Generating the supply was successful.
     *  Write the result into the history database and tell the app its id number */
    public static void successfulResult(ShuffleSupply supply) {
        // Insert the new supply
        long time = Calendar.getInstance().getTimeInMillis();
        ContentValues values = new ContentValues();
        values.putNull(DataDb._H_NAME);
        values.put(DataDb._H_TIME,      time);
        values.put(DataDb._H_CARDS,     Utils.join(",", supply.getCards()));
        values.put(DataDb._H_BANE,      supply.getBane());
        values.put(DataDb._H_HIGH_COST, supply.high_cost);
        values.put(DataDb._H_SHELTERS,  supply.shelters);
        Pref.getAppContext()
                .getContentResolver()
                .insert(Provider.URI_HIST, values);

        // let the listeners know the result
        Intent msg = new Intent(MSG_INTENT);
        msg.putExtra(MSG_RES, RES_OK);
        msg.putExtra(MSG_SUPPLY_ID, time);
        sendMsg(msg);
    }

    /** Broadcast a given message back to the activity */
    @SuppressWarnings("SameReturnValue")
    public static Void sendMsg(Intent msg) {
        try {
            LocalBroadcastManager.getInstance(Pref.getAppContext())
                    .sendBroadcast(msg);
        } catch(Exception ignored) {}
        return null;
    }

}
