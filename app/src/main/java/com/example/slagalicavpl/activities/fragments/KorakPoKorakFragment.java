package com.example.slagalicavpl.activities.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;
import com.example.slagalicavpl.activities.GameActivity;

public class KorakPoKorakFragment extends Fragment {

    private EditText etAnswer;
    private Button btnConfirm;
    private Button btnDelete;
    private Button btnSurrender;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etAnswer = view.findViewById(R.id.etAnswer);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnDelete = view.findViewById(R.id.btnDelete);
        btnSurrender = view.findViewById(R.id.btnSurrender);

        btnDelete.setOnClickListener(v -> etAnswer.setText(""));

        btnConfirm.setOnClickListener(v -> RetroButtonAnimation.flash(btnConfirm, () -> {
            // TODO KT2: validate answer, calculate points, advance game
            if (getActivity() instanceof GameActivity) {
                ((GameActivity) getActivity()).showMojBroj();
            }
        }));

        btnSurrender.setOnClickListener(v -> {
            // TODO KT2: confirm dialog then end game
            if (getActivity() != null) getActivity().finish();
        });
    }
}
