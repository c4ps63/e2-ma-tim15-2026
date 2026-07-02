package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.LeagueUtil;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUsername, tvEmail, tvRegion;
    private TextView tvTokens, tvStars, tvLeague;
    private TextView tvTotalGames, tvWinsLosses, tvWinPercent;
    private TextView tvStatKoZnaZnaTacnih, tvStatKoZnaZnaPromasenih;
    private TextView tvStatSpojnice, tvStatAsocijacije;
    private TextView tvStatSkocko, tvStatKorak, tvStatMojBroj;
    private TextView tvAvatarInitial;
    private View     viewAvatarCircle;
    private ProgressBar pbStatKzz, pbStatSpojnice, pbStatSkocko, pbStatKorak, pbStatMojBroj;
    private String   currentUid;

    // Trenutna boja avatara (čuva se između poziva)
    private int currentAvatarColor = Color.parseColor("#5C85FF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvUsername               = findViewById(R.id.tvUsername);
        tvEmail                  = findViewById(R.id.tvEmail);
        tvRegion                 = findViewById(R.id.tvRegion);
        tvTokens                 = findViewById(R.id.tvTokens);
        tvStars                  = findViewById(R.id.tvStars);
        tvLeague                 = findViewById(R.id.tvLeague);
        tvTotalGames             = findViewById(R.id.tvTotalGames);
        tvWinsLosses             = findViewById(R.id.tvWinsLosses);
        tvWinPercent             = findViewById(R.id.tvWinPercent);
        tvStatKoZnaZnaTacnih     = findViewById(R.id.tvStatKoZnaZnaTacnih);
        tvStatKoZnaZnaPromasenih = findViewById(R.id.tvStatKoZnaZnaPromasenih);
        tvStatSpojnice           = findViewById(R.id.tvStatSpojnice);
        tvStatAsocijacije        = findViewById(R.id.tvStatAsocijacije);
        tvStatSkocko             = findViewById(R.id.tvStatSkocko);
        tvStatKorak              = findViewById(R.id.tvStatKorak);
        tvStatMojBroj            = findViewById(R.id.tvStatMojBroj);
        tvAvatarInitial          = findViewById(R.id.tvAvatarInitial);
        viewAvatarCircle         = findViewById(R.id.viewAvatarCircle);
        pbStatKzz                = findViewById(R.id.pbStatKzz);
        pbStatSpojnice           = findViewById(R.id.pbStatSpojnice);
        pbStatSkocko             = findViewById(R.id.pbStatSkocko);
        pbStatKorak              = findViewById(R.id.pbStatKorak);
        pbStatMojBroj            = findViewById(R.id.pbStatMojBroj);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        // Klik na avatar (i stari btnEditAvatar) → color picker
        View flAvatar = findViewById(R.id.flAvatar);
        if (flAvatar != null) flAvatar.setOnClickListener(v -> showColorPickerDialog());
        View btnEdit = findViewById(R.id.btnEditAvatar);
        if (btnEdit != null) btnEdit.setOnClickListener(v -> showColorPickerDialog());

        FirebaseUser current = AuthService.getInstance().getCurrentUser();
        if (current != null) currentUid = current.getUid();

        loadProfile();
    }

    // ── Profil ────────────────────────────────────────────────────────────────

    private void loadProfile() {
        FirebaseUser current = AuthService.getInstance().getCurrentUser();
        if (current == null) return;

        UserRepository.getInstance().loadProfile(current.getUid(),
                new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                tvUsername.setText(u.username != null ? u.username : "—");

                // Avatar inicijal
                if (tvAvatarInitial != null && u.username != null && !u.username.isEmpty())
                    tvAvatarInitial.setText(String.valueOf(Character.toUpperCase(u.username.charAt(0))));

                // Avatar boja
                if (u.avatarColor != null) {
                    try { currentAvatarColor = Color.parseColor(u.avatarColor); }
                    catch (Exception ignored) {}
                }
                applyAvatarColor(currentAvatarColor);

                // Region border
                applyRegionBorder(u.region);

                tvEmail.setText(u.email != null ? u.email : "—");
                tvRegion.setText(u.region != null
                        ? com.example.slagalicavpl.model.SerbiaRegions.displayNameFromId(u.region) : "—");
                tvTokens.setText(String.valueOf(u.tokens));
                tvStars.setText(String.valueOf(u.stars));
                int league = LeagueUtil.getLeague(u.stars);
                tvLeague.setText(LeagueUtil.getIcon(league));
                TextView tvLeagueDetail = findViewById(R.id.tvLeagueDetail);
                if (tvLeagueDetail != null) tvLeagueDetail.setText(LeagueUtil.getName(league));

                tvTotalGames.setText(u.gamesPlayed + " partija");
                int losses = u.gamesPlayed - u.gamesWon;
                tvWinsLosses.setText(u.gamesWon + " / " + losses);
                int winPct = u.gamesPlayed > 0 ? (int)(u.gamesWon * 100.0 / u.gamesPlayed) : 0;
                tvWinPercent.setText(winPct + "%");

                tvStatKoZnaZnaTacnih.setText(String.valueOf(u.kzzCorrect));
                tvStatKoZnaZnaPromasenih.setText(String.valueOf(u.kzzTotal - u.kzzCorrect));

                int pctKzz      = pct(u.kzzCorrect, u.kzzTotal);
                int pctSpojnice = pct(u.spojniceConnected, u.spojniceTotal);
                int pctSkocko   = pct(u.skockoCorrect, u.skockoTotal);
                int pctKorak    = pct(u.korakCorrect, u.korakTotal);
                int pctMojBroj  = pct(u.mojBrojCorrect, u.mojBrojTotal);

                tvStatSpojnice.setText(pctSpojnice + "%");
                tvStatAsocijacije.setText(u.asocijacijeSolved + "/" + u.asocijacijeTotal + " rešenih");
                tvStatSkocko.setText(pctSkocko + "%");
                tvStatKorak.setText(pctKorak + "%");
                tvStatMojBroj.setText(pctMojBroj + "% tačnih");

                if (pbStatKzz      != null) pbStatKzz.setProgress(pctKzz);
                if (pbStatSpojnice != null) pbStatSpojnice.setProgress(pctSpojnice);
                if (pbStatSkocko   != null) pbStatSkocko.setProgress(pctSkocko);
                if (pbStatKorak    != null) pbStatKorak.setProgress(pctKorak);
                if (pbStatMojBroj  != null) pbStatMojBroj.setProgress(pctMojBroj);

                // QR kod — generiši i prikaži
                if (u.uid != null) showQrCode(u.uid);
            }
            @Override public void onError(String message) {}
        });
    }

    // ── Color picker ──────────────────────────────────────────────────────────

    private void showColorPickerDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);

        View     previewCircle  = view.findViewById(R.id.viewColorPreview);
        TextView tvPreviewInit  = view.findViewById(R.id.tvColorPreviewInitial);
        TextView tvHex          = view.findViewById(R.id.tvColorHex);
        SeekBar  sbR            = view.findViewById(R.id.sbRed);
        SeekBar  sbG            = view.findViewById(R.id.sbGreen);
        SeekBar  sbB            = view.findViewById(R.id.sbBlue);
        TextView tvR            = view.findViewById(R.id.tvRed);
        TextView tvG            = view.findViewById(R.id.tvGreen);
        TextView tvB            = view.findViewById(R.id.tvBlue);

        // Postavi inicijal u preview
        if (tvAvatarInitial != null)
            tvPreviewInit.setText(tvAvatarInitial.getText());

        // Početne vrijednosti iz trenutne boje
        int startR = Color.red(currentAvatarColor);
        int startG = Color.green(currentAvatarColor);
        int startB = Color.blue(currentAvatarColor);
        sbR.setProgress(startR);
        sbG.setProgress(startG);
        sbB.setProgress(startB);
        tvR.setText(String.valueOf(startR));
        tvG.setText(String.valueOf(startG));
        tvB.setText(String.valueOf(startB));
        updateColorPreview(previewCircle, tvHex, startR, startG, startB);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int val, boolean fromUser) {
                int r = sbR.getProgress();
                int g = sbG.getProgress();
                int b = sbB.getProgress();
                tvR.setText(String.valueOf(r));
                tvG.setText(String.valueOf(g));
                tvB.setText(String.valueOf(b));
                updateColorPreview(previewCircle, tvHex, r, g, b);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        sbR.setOnSeekBarChangeListener(listener);
        sbG.setOnSeekBarChangeListener(listener);
        sbB.setOnSeekBarChangeListener(listener);

        new AlertDialog.Builder(this)
                .setTitle("Odaberi boju avatara")
                .setView(view)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    int r = sbR.getProgress();
                    int g = sbG.getProgress();
                    int b = sbB.getProgress();
                    String hex = String.format("#%02X%02X%02X", r, g, b);
                    saveAvatarColor(hex);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void updateColorPreview(View circle, TextView tvHex, int r, int g, int b) {
        int color = Color.rgb(r, g, b);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        circle.setBackground(d);
        tvHex.setText(String.format("#%02X%02X%02X", r, g, b));
        tvHex.setTextColor(color);
    }

    private void saveAvatarColor(String hex) {
        if (currentUid == null) return;
        UserRepository.getInstance().saveAvatarColor(currentUid, hex,
                new UserRepository.Callback() {
            @Override public void onSuccess() {
                try {
                    currentAvatarColor = Color.parseColor(hex);
                    applyAvatarColor(currentAvatarColor);
                } catch (Exception ignored) {}
            }
            @Override public void onError(String msg) {
                Toast.makeText(ProfileActivity.this, "Greška pri čuvanju boje", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyAvatarColor(int color) {
        if (viewAvatarCircle == null) return;
        Drawable d = DrawableCompat.wrap(viewAvatarCircle.getBackground()).mutate();
        DrawableCompat.setTint(d, color);
        viewAvatarCircle.setBackground(d);
    }

    // ── QR kod ────────────────────────────────────────────────────────────────

    private void showQrCode(String uid) {
        ImageView iv = findViewById(R.id.ivProfileQr);
        if (iv == null) return;
        Bitmap qr = generateQrBitmap(uid, 512);
        if (qr != null) iv.setImageBitmap(qr);
    }

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) { return null; }
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

    // ── Promjena lozinke ──────────────────────────────────────────────────────

    public void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText etOld     = dialogView.findViewById(R.id.etOldPassword);
        EditText etNew     = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirm = dialogView.findViewById(R.id.etNewPasswordConfirm);
        new AlertDialog.Builder(this)
            .setTitle("Promena lozinke")
            .setView(dialogView)
            .setPositiveButton("Promeni", (d, w) ->
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
                        }))
            .setNegativeButton("Otkaži", null)
            .show();
    }

    // ── Avatar border po rangu regiona ────────────────────────────────────────

    private void applyRegionBorder(String userRegion) {
        if (userRegion == null || userRegion.isEmpty()) return;
        UserRepository.getInstance().loadLastCycleResults((cycleId, gold, silver, bronze) -> {
            String borderHex = null;
            if      (userRegion.equals(gold))   borderHex = "#FFD700";
            else if (userRegion.equals(silver)) borderHex = "#C0C0C0";
            else if (userRegion.equals(bronze)) borderHex = "#CD7F32";

            View outerRing = findViewById(R.id.viewAvatarOuter);
            if (outerRing == null) return;
            if (borderHex == null) { outerRing.setVisibility(View.INVISIBLE); return; }
            outerRing.setVisibility(View.VISIBLE);
            try {
                int color = Color.parseColor(borderHex);
                Drawable d = DrawableCompat.wrap(outerRing.getBackground()).mutate();
                DrawableCompat.setTint(d, color);
                outerRing.setBackground(d);
            } catch (Exception ignored) {}
        });
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private int pct(int part, int total) {
        return total > 0 ? (int)(part * 100.0 / total) : 0;
    }
}
