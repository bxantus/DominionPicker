package ca.marklauman.dominionpicker;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import butterknife.BindView;
import butterknife.ButterKnife;
import ca.marklauman.dominionpicker.settings.Pref;
import ca.marklauman.tools.preferences.SmallNumberPreference;

/**
 * Fragment representing drafter setup
 * @author Botond Xantus
 */
public class FragmentDrafter extends Fragment{
    @BindView(R.id.drafting_start) Button startButton;
    @BindView(R.id.pref_number_of_choices) SmallNumberPreference numberOfChoices;
    @BindView(R.id.cb_reshuffle) CheckBox cbReshuffle;

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
        numberOfChoices.setKey(Pref.DRAFT_NUMBER_OF_CHOICES);
        numberOfChoices.setText("Number of choices"); // TODO: why doesn't android:key and android:text work in resource xml?
        return view;
    }

    private void startDrafting(){
        Intent startDrafting = new Intent(getContext(), ActivityDrafter.class);
        getContext().startActivity(startDrafting);

    }


}
