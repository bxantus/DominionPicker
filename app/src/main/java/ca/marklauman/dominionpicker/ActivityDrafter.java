package ca.marklauman.dominionpicker;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import ca.marklauman.dominionpicker.database.LoaderId;
import ca.marklauman.dominionpicker.database.TableCard;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.dominionpicker.userinterface.recyclerview.AdapterCards;

import static ca.marklauman.dominionpicker.SupplyShufflerTask.*;

/**
 * @author Botond Xantus
 */
public class ActivityDrafter extends AppCompatActivity  implements AdapterCards.Listener {
    @BindView(R.id.list_choices) RecyclerView cardChoices;
    @BindView(R.id.list_picks) RecyclerView cardPicks;
    @BindView(R.id.pick_progress) TextView textPickProgress;
    @BindView(R.id.pick_title) TextView textPickTitle; // title of card to pick
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
            cardsNeeded = savedInstanceState.getInt("cards_needed", numKingdoms);
            draftResults = savedInstanceState.getParcelable("draft_results");
            draftSource = savedInstanceState.getParcelable("draft_source");
            selectingBane = savedInstanceState.getBoolean("selecting_bane");
            if (selectingBane) {
                baneCards = savedInstanceState.getParcelable("bane_cards");
                baneCardsReady(baneCards);
            }
            else nextDraftCandidates(); // show draft candidates
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
        outState.putInt("cards_needed", cardsNeeded);
        outState.putParcelable("draft_results", draftResults);
        outState.putParcelable("draft_source", draftSource);
        outState.putBoolean("selecting_bane", selectingBane);
        if (selectingBane) outState.putParcelable("bane_cards", baneCards);
    }

    private void updatePickProgress() {
        textPickProgress.setText(String.format("(%d/%d)", draftResults.getNumberOfCards(), cardsNeeded));
    }

    private final int numKingdoms = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_SUPPLY, 10);
    private int cardsNeeded = numKingdoms; // total number of cards needed (if landmarks or events get selected, this can increase)
    private final int numEvents = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_EVENTS, 2);
    private final int autoPicks = Pref.get(Pref.getAppContext()).getInt(Pref.DRAFT_NUMBER_OF_PICKS, 0);
    private int cardsToDraft = Pref.get(Pref.getAppContext()).getInt(Pref.DRAFT_NUMBER_OF_CHOICES, 3);
    private int draftIndex = 0; // the index of the currently drafted card
    private CardCollection draftCandidates; // current draft candidates
    private CardCollection draftSource;
    private SupplyShuffler.ShuffleSupply draftResults; // draft results are stored in this collection

    private void supplyReady(SupplyShuffler.ShuffleSupply supply) {
        draftSource = new CardCollection(supply.getCards());
        // make auto picks
        if (autoPicks > 0) {
            // have to load cursor first with card infos
            getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARD_CHOICES, null, new LoaderManager.LoaderCallbacks<Cursor>() {
                    @Override
                    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                        CardCollection autopickedCards = draftSource.subCollection(0, autoPicks);
                        return CardCollection.createLoader(autopickedCards, ActivityDrafter.this);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                        AdapterCards cardInfo = new AdapterCards(cardPicks); // TODO: kind of hack, would need something like the adapter, just handling card information. This could be extracted from the adapter
                        cardInfo.changeCursor(data);
                        for (int i = 0; i < autoPicks; ++i) {
                            addCardToResults(cardInfo, i);
                        }
                        draftIndex = autoPicks;
                        nextDraftCandidates();
                        updatePickProgress();
                        // update picks
                        updateDraftResults();
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> loader) { /* nop */    }
                }
            );

        } else nextDraftCandidates();

    }

    private void nextDraftCandidates() {
        // show the next 3 cards of the result
        getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARD_CHOICES, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                final int rangeStart = draftIndex;
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

    private void addCardToResults(AdapterCards cards, int idx) {
        if (cards.isSpecialCard(idx)) {
            ++cardsNeeded; // selecting a special card increases the number of cards picked by one
            draftResults.addSpecial(cards.getItemId(idx), false);
        }
        else {
            long cardId = cards.getItemId(idx);
            if (cardId == TableCard.ID_YOUNG_WITCH) ++cardsNeeded; // extra bane card needed for YW
            if (selectingBane) draftResults.setBane(cardId);
            draftResults.addKingdom(cardId, cards.getCost(idx), cards.getSetId(idx), false);
        }
    }

    private void onCandidateSelected(int idx) {
        // add selected to the result supply
        addCardToResults(choicesAdapter, idx);
        choicesAdapter.changeCursor(null);

        // check if all cards are drafted
        if (!draftResults.needsKingdom()) {
            if (draftResults.waitingForBane()) {
                // special case, bane card must be picked before finishing
                new BaneShufflerTask().execute();
                // update picks
                updateDraftResults();
                updatePickProgress();
            } else { // done
                SupplyShufflerTask.successfulResult(draftResults);
                // drafter is done, remove it from activity stack
                finish();
            }
        } else { // if not, show the next N cards
            draftIndex += cardsToDraft;
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
        private int numKingdomsToDraft;
        @Override
        protected SupplyShuffler.ShuffleSupply doInBackground(Void... voids) {
            // events are additional cards, so we must present more kingdom cards, if events are picked
            numKingdomsToDraft = autoPicks + (numKingdoms - autoPicks) * cardsToDraft + (numEvents * cardsToDraft - numEvents);

            SupplyShuffler.ShuffleSupply supply = new SupplyShuffler.ShuffleSupply(numKingdomsToDraft, numEvents, new SupplyShuffler.KingdomInsertAllStrategy());

            SupplyShuffler.ShuffleResult result = SupplyShuffler.fillSupply(supply, this);
            return supply;
        }

        @Override
        protected void onPostExecute(SupplyShuffler.ShuffleSupply shuffleSupply) {
            // notify caller about the new fancy supply
            if (shuffleSupply.getNumberOfCards() == numKingdomsToDraft)
                supplyReady(shuffleSupply);
            else {
                // send not enough cards warning
                Intent msg = new Intent(MSG_INTENT);
                msg.putExtra(MSG_RES, RES_MORE);
                msg.putExtra(MSG_SHORT, String.format("%d/%d", shuffleSupply.getNumberOfCards(), numKingdomsToDraft));
                LocalBroadcastManager.getInstance(ActivityDrafter.this).sendBroadcast(msg);
                finish();
            }
        }
    }

    // Special handling for YoungWitch, bane cards should be selected
    private boolean selectingBane = false;
    private CardCollection baneCards = null;

    private class BaneShufflerTask extends AsyncTask<Void, Void, SupplyShuffler.ShuffleSupply> {
        @Override
        protected SupplyShuffler.ShuffleSupply doInBackground(Void... voids) {
            return SupplyShuffler.createSupplyWithBaneCards(cardsToDraft, draftResults.getCardCollection(), this);
        }

        @Override
        protected void onPostExecute(SupplyShuffler.ShuffleSupply shuffleSupply) {
            if (shuffleSupply.getNumberOfCards() == cardsToDraft) {
                selectingBane = true;
                baneCards = shuffleSupply.getCardCollection();
                baneCardsReady(baneCards);
            } else {
                // show no bane warning
                Intent msg = new Intent(MSG_INTENT);
                msg.putExtra(MSG_RES, RES_NO_YW);
                LocalBroadcastManager.getInstance(ActivityDrafter.this).sendBroadcast(msg);
                finish();
            }
        }
    }

    private void baneCardsReady(final CardCollection banes) {
        getSupportLoaderManager().restartLoader(LoaderId.DRAFT_CARD_CHOICES, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return CardCollection.createLoader(banes, ActivityDrafter.this);
            }
            @Override public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                choicesAdapter.changeCursor(data);
                // update pick text
                textPickTitle.setText("Pick a bane"); // TODO: use string resource instead
            }
            @Override public void onLoaderReset(Loader<Cursor> loader) { /*nop */ }
        });
    }

}
