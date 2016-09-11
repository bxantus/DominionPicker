package ca.marklauman.dominionpicker;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Fragment representing drafter setup
 * @author Botond Xantus
 */
public class FragmentDrafter extends Fragment{
    @BindView(R.id.drafting_start) Button startButton;

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
        Intent startDrafting = new Intent(getContext(), ActivityDrafter.class);
        getContext().startActivity(startDrafting);

    }


}
