package ca.marklauman.dominionpicker;

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
import ca.marklauman.dominionpicker.database.LoaderId;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.dominionpicker.userinterface.recyclerview.AdapterCards;

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
            draftResults = new SupplyShuffler.ShuffleSupply(numKingdoms, numEvents, new SupplyShuffler.KingdomInsertAllStrategy());
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

    private int numKingdoms = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_SUPPLY, 10);
    private final int numEvents = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_EVENTS, 2);
    private int cardsToDraft = Pref.get(Pref.getAppContext()).getInt(Pref.DRAFT_NUMBER_OF_CHOICES, 3);
    private int draftIndex = 0; // the index of the currently drafted card
    private CardCollection draftCandidates; // current draft candidates
    private CardCollection draftSource;
    private SupplyShuffler.ShuffleSupply draftResults; // draft results are stored in this collection

    private void supplyReady(SupplyShuffler.ShuffleSupply supply) {
        draftSource = new CardCollection(supply.getCards());
        nextDraftCandidates();
    }

    private void nextDraftCandidates() {
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

    private void onCandidateSelected(int idx) {
        // add selected to the result supply
        if (choicesAdapter.isSpecialCard(idx)) {
            ++numKingdoms; // selecting a special card increases the number of cards picked by one
            draftResults.addSpecial(choicesAdapter.getItemId(idx), false);
        }
        else draftResults.addKingdom(choicesAdapter.getItemId(idx), choicesAdapter.getCost(idx), choicesAdapter.getSetId(idx), false);
        choicesAdapter.changeCursor(null);

        // check if all cards are drafted
        if (!draftResults.needsKingdom()) {
            SupplyShufflerTask.successfulResult(draftResults);
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
            @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) { return CardCollection.createLoader(new CardCollection(draftResults.getCards()), ActivityDrafter.this);  }
            @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) { picksAdapter.changeCursor(data); }
            @Override public void onLoaderReset(Loader<Cursor> loader) { /*nop*/  }
        });
    }

    private class DraftShufflerTask extends AsyncTask<Void, Void, SupplyShuffler.ShuffleSupply> {
        @Override
        protected SupplyShuffler.ShuffleSupply doInBackground(Void... voids) {
            final int numSpecial = numEvents;
            // events are additional cards, so we must present more kingdom cards, if events are picked
            final int numKingdomsToDraft = numKingdoms * cardsToDraft + (numEvents * cardsToDraft - numEvents);

            SupplyShuffler.ShuffleSupply supply = new SupplyShuffler.ShuffleSupply(numKingdomsToDraft, numSpecial, new SupplyShuffler.KingdomInsertAllStrategy());

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
