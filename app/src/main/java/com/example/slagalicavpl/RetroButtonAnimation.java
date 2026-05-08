package com.example.slagalicavpl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.content.ContextCompat;

public class RetroButtonAnimation {

    public static void flash(View button, Runnable onComplete) {
        if (!(button.getBackground() instanceof GradientDrawable)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        GradientDrawable bg = (GradientDrawable) button.getBackground().mutate();
        int acc = ContextCompat.getColor(button.getContext(), R.color.retro_acc);
        int original = bg.getColor() != null
                ? bg.getColor().getDefaultColor()
                : ContextCompat.getColor(button.getContext(), R.color.retro_ink);

        ValueAnimator anim = ValueAnimator.ofArgb(original, acc, original);
        anim.setDuration(350);
        anim.addUpdateListener(a -> bg.setColor((int) a.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                button.setEnabled(true);
                if (onComplete != null) onComplete.run();
            }
        });

        button.setEnabled(false);
        anim.start();
    }
}
