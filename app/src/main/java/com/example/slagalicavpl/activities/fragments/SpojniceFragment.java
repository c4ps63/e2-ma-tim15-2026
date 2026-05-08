package com.example.slagalicavpl.activities.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;
import com.example.slagalicavpl.activities.GameActivity;

public class SpojniceFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        btnConfirm.setOnClickListener(v -> RetroButtonAnimation.flash(btnConfirm, () -> {
            // TODO KT2: validate connections, calculate points
            if (getActivity() instanceof GameActivity) {
                ((GameActivity) getActivity()).showAsocijacije();
            }
        }));

        view.findViewById(R.id.btnSurrender).setOnClickListener(v -> {
            // TODO KT2: confirm dialog
            if (getActivity() != null) getActivity().finish();
        });
    }
}
