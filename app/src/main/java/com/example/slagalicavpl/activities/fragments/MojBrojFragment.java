package com.example.slagalicavpl.activities.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;

public class MojBrojFragment extends Fragment {

    private TextView tvExpression;
    private Button btnConfirm;
    private Button btnReset;
    private Button btnSurrender;

    // Static demo expression state
    private StringBuilder expression = new StringBuilder("25 × 8 + ");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvExpression = view.findViewById(R.id.tvExpression);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnReset = view.findViewById(R.id.btnReset);
        btnSurrender = view.findViewById(R.id.btnSurrender);

        // TODO KT2: wire number and operator tile clicks to build expression

        btnReset.setOnClickListener(v -> {
            expression = new StringBuilder();
            tvExpression.setText("");
        });

        btnConfirm.setOnClickListener(v -> RetroButtonAnimation.flash(btnConfirm, () -> {
            // TODO KT2: evaluate expression, compare with target, award points
            if (getActivity() != null) getActivity().finish();
        }));

        btnSurrender.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().finish();
        });
    }
}
