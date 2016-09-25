package ca.marklauman.dominionpicker;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import ca.marklauman.dominionpicker.database.Provider;
import ca.marklauman.dominionpicker.database.TableCard;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.tools.Utils;


/**
 * Base functionality included to shuffle cards
 */
class SupplyShuffler {
    enum ShuffleResult {
        SUCCESS,
        CANCELLED,
        FAILED
    }


    /**
     * Fill the provided supply with cards, based on user settings (selected card sets, filtered cards etc..)
     * @param supply the supply to fill. The supply specifies the number of cards and special cards required
     * @param task optional. check if this task is cancelled for a premature return
     * @return SUCCESS when everything was ok, CANCELLED is task was cancelled, FAILED if shuffling failed
     */
    static ShuffleResult fillSupply(ShuffleSupply supply, @Nullable AsyncTask<?, ?, ?> task) {
        if(!supply.needsKingdom())
            return ShuffleResult.SUCCESS;

        // load applicable filters.
        SharedPreferences pref = Pref.get(Pref.getAppContext());
        String filt_pre = FragmentPicker.getFilter(pref);
        String filt_req = pref.getString(Pref.REQ_CARDS, "");
        String filt_card = pref.getString(Pref.FILT_CARD, "");

        // Load the required cards into the supply
        if(0 < filt_req.length())
            loadCards(supply, joinFilters(filt_pre, TableCard._ID+" IN ("+filt_req+")"), true, task);
        if(task != null && task.isCancelled())
            return ShuffleResult.CANCELLED;
        if (!supply.needsKingdom())
            return ShuffleResult.SUCCESS;

        // Filter out both required and excluded cards
        String filt = filt_req+filt_card;
        if(0 < filt_req.length() && 0 < filt_card.length())
            filt = filt_req+","+filt_card;
        if(0 < filt.length())
            filt = TableCard._ID+" NOT IN ("+filt+")";

        // Shuffle the remaining cards into the supply
        loadCards(supply, joinFilters(filt_pre, filt), false, task);
        if(task != null && task.isCancelled())
            return ShuffleResult.CANCELLED;
        if (!supply.needsKingdom())
            return ShuffleResult.SUCCESS;
        else return ShuffleResult.FAILED;
    }

    /// fill the provided supply only with eligible bane cards numCards in size
    /// @param cardsToExclude the cards to exclude from the search (dor ex. current picks)
    static ShuffleSupply createSupplyWithBaneCards(int numCards, CardCollection cardsToExclude, @Nullable AsyncTask<?, ?, ?> task) {
        ShuffleSupply result = new ShuffleSupply(numCards, 0, new KingdomInsertOnlyBaneCardsStrategy());
        SharedPreferences pref = Pref.get(Pref.getAppContext());
        String filt_pre = FragmentPicker.getFilter(pref);

        String filt_excluded = TableCard._ID+" NOT IN ("+ Utils.join(",", cardsToExclude.cards) +")";
        loadCards(result, joinFilters(filt_pre, filt_excluded), false, task);
        return result;
    }

    /** Joins a collection of filters together with AND statements */
    private static String joinFilters(String... filters) {
        if(filters.length == 0) return "";
        if(filters.length == 1) return filters[0];

        // Find the first non-null string in the filter list
        int i =0;
        while(filters[i] == null || "".equals(filters[i])) {
            if(i == filters.length) return "";
            i++;
        }

        // Start of the joined filter
        StringBuilder res = new StringBuilder(filters[i].length());
        res.append(filters[i]);
        i++;

        // Add any remaining non-null filters
        for(;i<filters.length; i++) {
            if(filters[i] != null && !"".equals(filters[i])) {
                res.append(" AND ");
                res.append(filters[i]);
            }
        }
        return res.toString();
    }


    /** Load all cards matching the filter and add them to the supply.
     *  @param s The supply object that you want to add to.
     *  @param filter The filter for the cards you wish to add.
     *  @param cardsRequired True if all matching cards must be in the supply.
     *  If this is false, cards will be added to the supply until it has enough kingdom cards. */
    static private void loadCards(ShuffleSupply s, String filter, boolean cardsRequired,  @Nullable AsyncTask<?, ?, ?> task) {
        // Query the cards in the database
        Cursor c = Pref.getAppContext()
                       .getContentResolver()
                       .query(Provider.URI_CARD_DATA,
                               new String[]{TableCard._ID, TableCard._TYPE_EVENT,
                                            TableCard._TYPE_LANDMARK, TableCard._SET_ID,
                                            TableCard._COST},
                               filter, null, "random()");
        if(c == null) return;

        try {
            int _id = c.getColumnIndex(TableCard._ID);
            int _event = c.getColumnIndex(TableCard._TYPE_EVENT);
            int _landmark = c.getColumnIndex(TableCard._TYPE_LANDMARK);
            int _cost = c.getColumnIndex(TableCard._COST);
            int _set_id = c.getColumnIndex(TableCard._SET_ID);

            c.moveToPosition(-1);
            while(c.moveToNext() && (cardsRequired || s.needsKingdom())) {
                if(task != null && task.isCancelled())
                    return;

                // We handle special and kingdom cards differently (specials first)
                long id = c.getLong(_id);
                if(c.getInt(_event) != 0 || c.getInt(_landmark) != 0)
                    s.addSpecial(id, cardsRequired);
                else s.addKingdom(id, c.getString(_cost), c.getInt(_set_id), cardsRequired);
            }
        } finally {
            c.close();
        }
    }

    interface IKingdomInsertStrategy {
        /// @return true if card with id should be inserted. supply may be used to do all kind of operations (side effects)
        boolean handleKingdomInsertion(ShuffleSupply supply, long id, String cost, int set_id);
    }

    static class KingdomInsertAllStrategy implements IKingdomInsertStrategy {
        @Override
        public boolean handleKingdomInsertion(ShuffleSupply supply, long id, String cost, int set_id) {
            return true;
        }
    }

    /** Represents a supply in the process of being shuffled. */
    static class ShuffleSupply implements Parcelable {
        /** Minimum amount of kingdom cards needed for this supply to be complete. */
        public int minKingdom;
        /** Maximum amount of special cards allowed. */
        public final int maxSpecial;
        private int numSpecials = 0; // special cards added (events, and landmarks)
        /** If this is a high cost game or not. */
        public boolean high_cost = false;
        /** If this game uses shelters or not. */
        public boolean shelters = false;

        /** Position of the kingdom card that determines if this is a high cost game. */
        private final int costCard;
        /** Position of the kingdom card that determines if this game uses shelters. */
        private final int shelterCard;

        /** Kingdom cards in this supply */
        private final CardCollection kingdom;

        /** Id for a possible bane card */
        private long bane = -1L;
        private boolean waitingForBane = false; // Check if the supply is waiting for a valid bane card.
        private IKingdomInsertStrategy insertStrategy;


        public ShuffleSupply(int numKingdoms, int numSpecials, IKingdomInsertStrategy insertStrategy) {
            minKingdom = numKingdoms;
            maxSpecial = numSpecials;
            kingdom = new CardCollection();
            costCard = (int)(Math.random() * (minKingdom))+1;
            shelterCard = (int)(Math.random() * minKingdom)+1;

            this.insertStrategy = insertStrategy;
        }


        /** Add an event to the supply */
        public void addSpecial(long id, boolean required) {
            if(required || numSpecials < maxSpecial) {
                kingdom.cards.add(id);
                ++numSpecials;
            }
        }


        /** Check if this shuffler needs a kingdom card */
        public boolean needsKingdom() {
            return kingdom.cards.size() - numSpecials < minKingdom;
        }


        /** Add a kingdom card to the supply */
        public void addKingdom(long id, String cost, int set_id, boolean required) {
            if(!required && !needsKingdom())
                return;
            if (id == TableCard.ID_YOUNG_WITCH) waitingForBane = true; // YW will require a bane

            if (insertStrategy.handleKingdomInsertion(this, id, cost, set_id))
                kingdom.cards.add(id);

            // determine if this is a high cost/shelters game
            if(kingdom.cards.size() - numSpecials == costCard)
                high_cost = set_id == TableCard.SET_PROSPERITY;
            if(kingdom.cards.size() - numSpecials == shelterCard)
                shelters = set_id == TableCard.SET_DARK_AGES;
        }


        /** Get all cards in this supply */
        public long[] getCards() {
            long[] res = new long[kingdom.cards.size()];
            int i = 0;
            for(Long card : kingdom.cards) {
                res[i] = card;
                i++;
            }
            return res;
        }

        public int getNumberOfCards() {
            return kingdom.cards.size();
        }

        public CardCollection getCardCollection() { return kingdom; }

        /** Get the bane card of this supply */
        public long getBane() {
            return bane;
        }

        /// Set the bane card for this supply
        public void setBane(long id) {
            bane = id;
            waitingForBane = false;
            minKingdom++; // bane is an extra card in the supply
        }

        /** Get how many more kingdom cards we need */
        public int getShortfall() {
            return minKingdom - kingdom.cards.size();
        }

        /** Check if the supply is waiting for a valid bane card. */
        public boolean waitingForBane() {
            return waitingForBane;
        }

        public ShuffleSupply(Parcel parcel) {
            minKingdom = parcel.readInt();
            maxSpecial = parcel.readInt();
            high_cost = parcel.readInt() != 0;
            shelters = parcel.readInt() != 0;

            costCard = parcel.readInt();
            shelterCard = parcel.readInt();
            bane = parcel.readLong();
            waitingForBane = parcel.readInt() != 0;

            numSpecials = parcel.readInt();
            kingdom = parcel.readParcelable(getClass().getClassLoader());
            insertStrategy = new KingdomInsertAllStrategy(); // default to insert all strategy
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(minKingdom);
            parcel.writeInt(maxSpecial);
            parcel.writeInt(high_cost ? 1 : 0);
            parcel.writeInt(shelters? 1 : 0);

            parcel.writeInt(costCard);
            parcel.writeInt(shelterCard);
            parcel.writeLong(bane);
            parcel.writeInt(waitingForBane ? 1 : 0);

            parcel.writeInt(numSpecials);
            parcel.writeParcelable(kingdom, flags);
        }

        @Override
        public int describeContents() { return 0; }

        public static final Parcelable.Creator<ShuffleSupply> CREATOR = new Creator<ShuffleSupply>() {
            @Override
            public ShuffleSupply createFromParcel(Parcel parcel) {
                return new ShuffleSupply(parcel);
            }

            @Override
            public ShuffleSupply[] newArray(int size) {
                return new ShuffleSupply[size];
            }
        };
    }

    static class KingdomInsertWithYWStrategy implements IKingdomInsertStrategy {
        /** Possible value of {@link #baneStatus}. There is no young witch in the supply. */
        private static final int BANE_INACTIVE = 0;
        /** Possible value of {@link #baneStatus}.
         *  The young witch was drawn, but we haven't seen a bane yet */
        private static final int BANE_WAITING = 1;
        /** Possible value of {@link #baneStatus}. The bane and the young witch have been set */
        private static final int BANE_ACTIVE = 2;

        /** Current status of the bane card */
        private int baneStatus = BANE_INACTIVE;
        /** Id for a possible bane card */
        private long bane = -1L;
        @Override
        public boolean handleKingdomInsertion(ShuffleSupply supply, long id, String cost, int set_id) {
            // Special handling for the young witch
            if(id == TableCard.ID_YOUNG_WITCH) {
                if(bane == -1L) {
                    // Do not add the young witch, wait for a bane card first
                    baneStatus = BANE_WAITING;
                    return false;
                } else {
                    // Add the young witch, we have a bane card
                    baneStatus = BANE_ACTIVE;
                    supply.setBane(bane); // mark the bane card in the supply
                }
            }

            // Special handling for the young witch's bane
            else if("2".equals(cost) || "3".equals(cost)) {
                bane = id;
                if(baneStatus == BANE_WAITING)
                    supply.addKingdom(TableCard.ID_YOUNG_WITCH, "4", 3, true);
            }
            return true;
        }
    }

    static class KingdomInsertOnlyBaneCardsStrategy implements IKingdomInsertStrategy {
        @Override
        public boolean handleKingdomInsertion(ShuffleSupply supply, long id, String cost, int set_id) {
            return "2".equals(cost) || "3".equals(cost); // only cards with cost 2 or 3
        }
    }
}