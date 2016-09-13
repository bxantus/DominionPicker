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
import android.widget.TextView;
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
    @BindView(R.id.list_choices) RecyclerView cardChoices;
    @BindView(R.id.list_picks) RecyclerView cardPicks;
    @BindView(R.id.pick_progress) TextView textPickProgress;
    private AdapterCards choicesAdapter;
    private AdapterCards picksAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drafter);
        ButterKnife.bind(this);

        choicesAdapter = new AdapterCards(cardChoices);
        cardChoices.setAdapter(choicesAdapter);
        cardChoices.setLayoutManager(new LinearLayoutManager(this));
        // listen for card clicks
        choicesAdapter.setListener(this);

        picksAdapter = new AdapterCards(cardPicks);
        cardPicks.setAdapter(picksAdapter);
        cardPicks.setLayoutManager(new LinearLayoutManager(this));

        // extract current state form savedInstanceState
        if (savedInstanceState != null) {
            draftIndex = savedInstanceState.getInt("draft_index", 0);
            draftResults = savedInstanceState.getParcelable("draft_results");
            draftSource = savedInstanceState.getParcelable("draft_source");

            nextDraftCandidates(); // show draft candidates
            updateDraftResults();  // show current results
        } else { // no saved state
            draftResults = new CardCollection();
            // start shuffling of DRAFT_NUMBER_OF_CHOICES * kingdom cards required
            DraftShufflerTask shuffleTask = new DraftShufflerTask();
            shuffleTask.execute();
        }
        updatePickProgress();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("draft_index", draftIndex);
        outState.putParcelable("draft_results", draftResults);
        outState.putParcelable("draft_source", draftSource);
    }

    private void updatePickProgress() {
        textPickProgress.setText(String.format("(%d/%d)", draftIndex, numKingdoms));
    }

    final int numKingdoms = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_SUPPLY, 10);
    int cardsToDraft = Pref.get(Pref.getAppContext()).getInt(Pref.DRAFT_NUMBER_OF_CHOICES, 3);
    int draftIndex = 0; // the index of the currently drafted card
    CardCollection draftCandidates; // current draft candidates
    CardCollection draftSource;
    CardCollection draftResults; // draft results are stored in this collection

    void supplyReady(SupplyShuffler.ShuffleSupply supply) {
        draftSource = new CardCollection(supply.getCards());
        nextDraftCandidates();
    }

    void nextDraftCandidates() {
        // show the next 3 cards of the result
        getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARD_CHOICES, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                final int rangeStart = draftIndex * cardsToDraft;
                draftCandidates = draftSource.subCollection(rangeStart, rangeStart + cardsToDraft);
                return CardCollection.createLoader(draftCandidates, ActivityDrafter.this);
            }
            @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) { choicesAdapter.changeCursor(data); }
            @Override public void onLoaderReset(Loader<Cursor> loader) { /*nop */ }
        });
    }

    @Override
    public void onItemClick(AdapterCards.ViewHolder holder, int position, long id, boolean longClick) {
        onCandidateSelected(position);
    }

    void onCandidateSelected(int idx) {
        // add selected to the result supply
        draftResults.cards.add(choicesAdapter.getItemId(idx));
        choicesAdapter.changeCursor(null);

        // check if all cards are drafted
        if (draftResults.cards.size() == numKingdoms) {
            // TODO: extract common notification code (currently this is copied from SupplyShufflerTask
            long time = Calendar.getInstance().getTimeInMillis();
            ContentValues values = new ContentValues();
            values.putNull(DataDb._H_NAME);
            values.put(DataDb._H_TIME,      time);
            values.put(DataDb._H_CARDS,     Utils.join(",", draftResults.cards));
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
        } else { // if not, show the next N cards
            draftIndex++;
            nextDraftCandidates();
            updatePickProgress();
            // update picks
            updateDraftResults();
        }
    }

    private void updateDraftResults() {
        getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARD_PICKS, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) { return CardCollection.createLoader(draftResults, ActivityDrafter.this);  }
            @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) { picksAdapter.changeCursor(data); }
            @Override public void onLoaderReset(Loader<Cursor> loader) { /*nop*/  }
        });
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
