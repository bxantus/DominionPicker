package ca.marklauman.dominionpicker;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
 * Fragment representing drafter setup
 * @author Botond Xantus
 */
public class FragmentDrafter extends Fragment implements AdapterCards.Listener{
    @BindView(R.id.drafting_start) Button startButton;
    @BindView(android.R.id.list) RecyclerView cardList;
    private AdapterCards cardsAdapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drafter, container, false);
        ButterKnife.bind(this, view);

        cardsAdapter = new AdapterCards(cardList);
        cardList.setAdapter(cardsAdapter);
        cardList.setLayoutManager(new LinearLayoutManager(getContext()));
        // listen for card clicks
        cardsAdapter.setListener(this);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDrafting();
            }
        });
        return view;
    }

    // TODO: move the code below to DraftingActivity

    private void startDrafting(){
        // start shuffling of 3* kingdom cards required
        DraftShufflerTask shuffleTask = new DraftShufflerTask();
        shuffleTask.execute();
    }

    final int numKingdoms = Pref.get(Pref.getAppContext()).getInt(Pref.LIMIT_SUPPLY, 10);
    final int cardsToDraft = 3; // TODO: make this a setting in FragmentDrafter
    int draftIndex = 0; // the index of the currently drafted card
    CardCollection draftCandidates; // current draft candidates
    CardCollection draftSource;
    List<Long> draftResults; // draft results are stored in this collection

    void supplyReady(SupplyShuffler.ShuffleSupply supply) {
        draftSource = new CardCollection(supply.getCards());

        draftResults = new ArrayList<>();
        draftIndex = 0;
        nextDraftCandidates();
    }

    void nextDraftCandidates() {
        // show the next 3 cards of the result
        getLoaderManager().restartLoader(LoaderId.DRAFT_CARDS, null, new CardLoaderCallbacks());
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
            return CardCollection.createLoader(draftCandidates, FragmentDrafter.this.getContext());
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
