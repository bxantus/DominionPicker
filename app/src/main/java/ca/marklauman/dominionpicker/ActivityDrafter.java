package ca.marklauman.dominionpicker;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import ca.marklauman.dominionpicker.database.DataDb;
import ca.marklauman.dominionpicker.database.LoaderId;
import ca.marklauman.dominionpicker.database.Provider;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.dominionpicker.userinterface.recyclerview.AdapterCards;
import ca.marklauman.tools.Utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author Botond Xantus
 */
public class ActivityDrafter extends AppCompatActivity  implements AdapterCards.Listener {
    @BindView(android.R.id.list)
    RecyclerView cardList;
    private AdapterCards cardsAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drafter);
        ButterKnife.bind(this);

        cardsAdapter = new AdapterCards(cardList);
        cardList.setAdapter(cardsAdapter);
        cardList.setLayoutManager(new LinearLayoutManager(this));
        // listen for card clicks
        cardsAdapter.setListener(this);

        // start shuffling of DRAFT_NUMBER_OF_CHOICES * kingdom cards required
        cardsToDraft = Pref.get(this).getInt(Pref.DRAFT_NUMBER_OF_CHOICES, 3);
        DraftShufflerTask shuffleTask = new DraftShufflerTask();
        shuffleTask.execute();

        // TODO: extract current state form savedInstanceState

        draftResults = new ArrayList<>();
        draftIndex = 0;
    }

    final int numKingdoms = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_SUPPLY, 10);
    int cardsToDraft;
    int draftIndex = 0; // the index of the currently drafted card
    CardCollection draftCandidates; // current draft candidates
    CardCollection draftSource;
    List<Long> draftResults; // draft results are stored in this collection

    void supplyReady(SupplyShuffler.ShuffleSupply supply) {
        draftSource = new CardCollection(supply.getCards());
        nextDraftCandidates();
    }

    void nextDraftCandidates() {
        // show the next 3 cards of the result
        getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARDS, null, new CardLoaderCallbacks());
    }

    @Override
    public void onItemClick(AdapterCards.ViewHolder holder, int position, long id, boolean longClick) {
        onCandidateSelected(position);
    }

    void onCandidateSelected(int idx) {
        // add selected to the result supply
        draftResults.add(cardsAdapter.getItemId(idx));
        cardsAdapter.changeCursor(null);

        // TODO: update draft progress display
        // check if all cards are drafted
        if (draftResults.size() == numKingdoms) {
            // TODO: extract common notification code (currently this is copied from SupplyShufflerTask
            long time = Calendar.getInstance().getTimeInMillis();
            ContentValues values = new ContentValues();
            values.putNull(DataDb._H_NAME);
            values.put(DataDb._H_TIME,      time);
            values.put(DataDb._H_CARDS,     Utils.join(",", draftResults));
            values.put(DataDb._H_BANE,      -1);
            values.put(DataDb._H_HIGH_COST, false);
            values.put(DataDb._H_SHELTERS,  false);
            Pref.getAppContext()
                    .getContentResolver()
                    .insert(Provider.URI_HIST, values);

            // let the listeners know the result
            Intent msg = new Intent(SupplyShufflerTask.MSG_INTENT);
            msg.putExtra(SupplyShufflerTask.MSG_RES, SupplyShufflerTask.RES_OK);
            msg.putExtra(SupplyShufflerTask.MSG_SUPPLY_ID, time);
            SupplyShufflerTask.sendMsg(msg);

            // drafter is done, remove it from activity stack
            finish();
        } else { // if not, show the next 3 cards
            // TODO: empty candidate list, while loading (or freeze it)
            draftIndex++;
            nextDraftCandidates();
        }
    }

    private class CardLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            final int rangeStart = draftIndex * cardsToDraft;
            draftCandidates = draftSource.subCollection(rangeStart, rangeStart + cardsToDraft);
            return CardCollection.createLoader(draftCandidates, ActivityDrafter.this);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            cardsAdapter.changeCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // nop
        }
    }

    private class DraftShufflerTask extends AsyncTask<Void, Void, SupplyShuffler.ShuffleSupply> {
        @Override
        protected SupplyShuffler.ShuffleSupply doInBackground(Void... voids) {
            SharedPreferences pref = Pref.get(Pref.getAppContext());

            final int numKingdomsToDraft = numKingdoms * cardsToDraft;
            final int numSpecial = 0; // TODO: decide how to draft special cards (events/landmarks), have to check the rules
            SupplyShuffler.ShuffleSupply supply = new SupplyShuffler.ShuffleSupply(numKingdomsToDraft, numSpecial);

            SupplyShuffler.ShuffleResult result = SupplyShuffler.fillSupply(supply, this);
            if (result == SupplyShuffler.ShuffleResult.SUCCESS) return supply;
            else return null;
        }

        @Override
        protected void onPostExecute(SupplyShuffler.ShuffleSupply shuffleSupply) {
            // notify caller about the new fancy supply
            // TODO: handle errors!
            supplyReady(shuffleSupply);
        }
    }
}
