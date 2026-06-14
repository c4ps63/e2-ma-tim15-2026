package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;

public class ProfileActivity extends AppCompatActivity {

    private static final String[] COLOR_NAMES = {"Plava", "Crvena", "Zelena", "Narandžasta", "Ljubičasta", "Tirkizna", "Ružičasta", "Siva"};
    private static final String[] COLOR_HEX   = {"#5C85FF", "#FF6B6B", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4", "#E91E63", "#607D8B"};

    private TextView tvUsername, tvEmail, tvRegion;
    private TextView tvTokens, tvStars, tvLeague;
    private TextView tvTotalGames, tvWinsLosses, tvWinPercent;
    private TextView tvStatKoZnaZnaTacnih, tvStatKoZnaZnaPromasenih;
    private TextView tvStatSpojnice, tvStatAsocijacije;
    private TextView tvStatSkocko, tvStatKorak, tvStatMojBroj;
    private TextView tvAvatarInitial;
    private View     viewAvatarCircle;
    private String   currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvUsername              = findViewById(R.id.tvUsername);
        tvEmail                 = findViewById(R.id.tvEmail);
        tvRegion                = findViewById(R.id.tvRegion);
        tvTokens                = findViewById(R.id.tvTokens);
        tvStars                 = findViewById(R.id.tvStars);
        tvLeague                = findViewById(R.id.tvLeague);
        tvTotalGames            = findViewById(R.id.tvTotalGames);
        tvWinsLosses            = findViewById(R.id.tvWinsLosses);
        tvWinPercent            = findViewById(R.id.tvWinPercent);
        tvStatKoZnaZnaTacnih    = findViewById(R.id.tvStatKoZnaZnaTacnih);
        tvStatKoZnaZnaPromasenih= findViewById(R.id.tvStatKoZnaZnaPromasenih);
        tvStatSpojnice          = findViewById(R.id.tvStatSpojnice);
        tvStatAsocijacije       = findViewById(R.id.tvStatAsocijacije);
        tvStatSkocko            = findViewById(R.id.tvStatSkocko);
        tvStatKorak             = findViewById(R.id.tvStatKorak);
        tvStatMojBroj           = findViewById(R.id.tvStatMojBroj);
        tvAvatarInitial         = findViewById(R.id.tvAvatarInitial);
        viewAvatarCircle        = findViewById(R.id.viewAvatarCircle);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        Button btnEditAvatar = findViewById(R.id.btnEditAvatar);
        if (btnEditAvatar != null)
            btnEditAvatar.setOnClickListener(v -> showColorPickerDialog());

        FirebaseUser current = AuthService.getInstance().getCurrentUser();
        if (current != null) currentUid = current.getUid();

        loadProfile();
    }

    // ── Učitaj profil iz Firestore-a ──────────────────────────────────────────

    private void loadProfile() {
        FirebaseUser current = AuthService.getInstance().getCurrentUser();
        if (current == null) return;

        UserRepository.getInstance().loadProfile(current.getUid(),
                new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                tvUsername.setText(u.username != null ? u.username : "—");

                // avatar inicijal i boja
                if (tvAvatarInitial != null && u.username != null && !u.username.isEmpty())
                    tvAvatarInitial.setText(String.valueOf(Character.toUpperCase(u.username.charAt(0))));
                if (viewAvatarCircle != null && u.avatarColor != null) {
                    try {
                        int color = Color.parseColor(u.avatarColor);
                        Drawable d = DrawableCompat.wrap(viewAvatarCircle.getBackground()).mutate();
                        DrawableCompat.setTint(d, color);
                        viewAvatarCircle.setBackground(d);
                    } catch (Exception ignored) {}
                }

                tvEmail.setText(u.email != null ? u.email : "—");
                tvRegion.setText(u.region != null ? u.region.toUpperCase() : "—");
                tvTokens.setText(String.valueOf(u.tokens));
                tvStars.setText(String.valueOf(u.stars));

                // liga po broju zvezda: 0=nulta, 1=1.liga@100, 2=2.liga@200...
                tvLeague.setText(leagueNumber(u.stars) + ".");

                tvTotalGames.setText(u.gamesPlayed + " partija");

                int losses = u.gamesPlayed - u.gamesWon;
                tvWinsLosses.setText(u.gamesWon + " / " + losses);

                int winPct = u.gamesPlayed > 0
                        ? (int) (u.gamesWon * 100.0 / u.gamesPlayed) : 0;
                tvWinPercent.setText(winPct + "%");

                tvStatKoZnaZnaTacnih.setText(String.valueOf(u.kzzCorrect));
                tvStatKoZnaZnaPromasenih.setText(String.valueOf(u.kzzTotal - u.kzzCorrect));

                tvStatSpojnice.setText(pct(u.spojniceConnected, u.spojniceTotal) + "%");
                tvStatAsocijacije.setText(u.asocijacijeSolved + "/" + u.asocijacijeTotal + " rešenih");
                tvStatSkocko.setText(pct(u.skockoCorrect, u.skockoTotal) + "%");
                tvStatKorak.setText(pct(u.korakCorrect, u.korakTotal) + "%");
                tvStatMojBroj.setText(pct(u.mojBrojCorrect, u.mojBrojTotal) + "% tačnih");
            }

            @Override
            public void onError(String message) {
                // prikaži podrazumevane vrednosti
            }
        });
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    private void confirmLogout() {
        new AlertDialog.Builder(this)
            .setTitle("Odjava")
            .setMessage("Da li si siguran da se želiš odjaviti?")
            .setPositiveButton("Odjavi se", (d, w) -> {
                AuthService.getInstance().logout();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Otkaži", null)
            .show();
    }

    // ── Promena lozinke (otvara dialog) ──────────────────────────────────────

    public void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_change_password, null);
        EditText etOld     = dialogView.findViewById(R.id.etOldPassword);
        EditText etNew     = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirm = dialogView.findViewById(R.id.etNewPasswordConfirm);

        new AlertDialog.Builder(this)
            .setTitle("Promena lozinke")
            .setView(dialogView)
            .setPositiveButton("Promeni", (d, w) -> {
                AuthService.getInstance().changePassword(
                        etOld.getText().toString(),
                        etNew.getText().toString(),
                        etConfirm.getText().toString(),
                        new AuthService.Callback() {
                            @Override public void onSuccess(String msg) {
                                Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String msg) {
                                Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        });
            })
            .setNegativeButton("Otkaži", null)
            .show();
    }

    // ── Color picker za avatar ────────────────────────────────────────────────

    private void showColorPickerDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Odaberi boju avatara")
            .setItems(COLOR_NAMES, (dialog, which) -> {
                String hex = COLOR_HEX[which];
                if (currentUid == null) return;
                UserRepository.getInstance().saveAvatarColor(currentUid, hex,
                    new UserRepository.Callback() {
                        @Override public void onSuccess() {
                            try {
                                int color = Color.parseColor(hex);
                                if (viewAvatarCircle != null) {
                                    Drawable d = DrawableCompat.wrap(viewAvatarCircle.getBackground()).mutate();
                                    DrawableCompat.setTint(d, color);
                                    viewAvatarCircle.setBackground(d);
                                }
                            } catch (Exception ignored) {}
                        }
                        @Override public void onError(String msg) {
                            Toast.makeText(ProfileActivity.this, "Greška pri čuvanju boje", Toast.LENGTH_SHORT).show();
                        }
                    });
            })
            .show();
    }

    // ── Pomoćne metode ────────────────────────────────────────────────────────

    private int pct(int part, int total) {
        return total > 0 ? (int) (part * 100.0 / total) : 0;
    }

    private int leagueNumber(int stars) {
        // liga 1=100*, liga 2=200*, liga 3=400*, liga 4=800*, liga 5=1600*
        int[] thresholds = {100, 200, 400, 800, 1600};
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (stars >= thresholds[i]) return i + 1;
        }
        return 0;
    }
}
