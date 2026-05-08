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

public class AsocijacijeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnSubmit = view.findViewById(R.id.btnSubmit);
        btnSubmit.setOnClickListener(v -> RetroButtonAnimation.flash(btnSubmit, () -> {
            // TODO KT2: validate final answer, calculate points
            if (getActivity() instanceof GameActivity) {
                ((GameActivity) getActivity()).showSkocko();
            }
        }));

        view.findViewById(R.id.btnSurrender).setOnClickListener(v -> {
            // TODO KT2: confirm dialog
            if (getActivity() != null) getActivity().finish();
        });
    }
}
