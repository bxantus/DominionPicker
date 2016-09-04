package ca.marklauman.dominionpicker;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import butterknife.BindView;
import butterknife.ButterKnife;
import ca.marklauman.dominionpicker.settings.Pref;

/**
 * Fragment representing drafter setup
 * @author Botond Xantus
 */
public class FragmentDrafter extends Fragment {
    @BindView(R.id.drafting_start) Button startButton;
    @BindView(android.R.id.list) RecyclerView cardList;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drafter, container, false);
        ButterKnife.bind(this, view);

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
        // when done, show the first 3 cards of the result
        // while not all cards selected
        //    select one of the 3 cards -> add it to the result supply
        //    show the next 3 cards


    }

    private class DraftShufflerTask extends AsyncTask<Void, Void, SupplyShuffler.ShuffleSupply> {
        @Override
        protected SupplyShuffler.ShuffleSupply doInBackground(Void... voids) {
            SharedPreferences pref = Pref.get(Pref.getAppContext());
            final int cardsToDraft = 3; // TODO: make this a setting in FragmentDrafter
            final int numKingdoms = pref.getInt(Pref.LIMIT_SUPPLY, 10) * cardsToDraft;
            final int numSpecial = 0; // TODO: decide how to draft special cards (events/landmarks), have to check the rules
            SupplyShuffler.ShuffleSupply supply = new SupplyShuffler.ShuffleSupply(numKingdoms, numSpecial);

            SupplyShuffler.ShuffleResult result = SupplyShuffler.fillSupply(supply, this);
            if (result == SupplyShuffler.ShuffleResult.SUCCESS) return supply;
            else return null;
        }

        @Override
        protected void onPostExecute(SupplyShuffler.ShuffleSupply shuffleSupply) {
            // notify caller about the new fancy supply
        }
    }
}
