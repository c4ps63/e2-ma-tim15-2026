package com.example.slagalicavpl.service;

import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;

public class AuthService {

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    private static AuthService instance;
    private final UserRepository repo = UserRepository.getInstance();

    private AuthService() {}

    public static AuthService getInstance() {
        if (instance == null) instance = new AuthService();
        return instance;
    }

    public FirebaseUser getCurrentUser() {
        return repo.getCurrentUser();
    }

    public boolean isEmailVerified() {
        FirebaseUser u = repo.getCurrentUser();
        return u != null && u.isEmailVerified();
    }

    public void logout() {
        repo.logout();
    }

    // ── Registracija ─────────────────────────────────────────────────────────

    public void register(String email, String password, String confirmPassword,
                         String username, String region, Callback cb) {

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            cb.onError("Popuni sva obavezna polja");
            return;
        }
        if (username.length() < 3) {
            cb.onError("Korisničko ime mora imati najmanje 3 znaka");
            return;
        }
        if (!password.equals(confirmPassword)) {
            cb.onError("Lozinke se ne poklapaju");
            return;
        }
        if (password.length() < 6) {
            cb.onError("Lozinka mora imati najmanje 6 znakova");
            return;
        }

        // 1. kreiraj Firebase Auth nalog
        repo.createAuthUser(email, password, new UserRepository.Callback() {
            @Override
            public void onSuccess() {
                FirebaseUser u = repo.getCurrentUser();
                String uid = u.getUid();

                // 2. sačuvaj profil u Firestore
                User user = new User(uid, username, email, region);
                repo.saveUserProfile(user, new UserRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        // 3. pošalji verifikacioni email
                        repo.sendVerificationEmail(new UserRepository.Callback() {
                            @Override
                            public void onSuccess() {
                                cb.onSuccess("Nalog kreiran! Provjeri email i potvrdi registraciju.");
                            }
                            @Override
                            public void onError(String msg) {
                                // nalog kreiran, ali email nije poslat — nije fatalno
                                cb.onSuccess("Nalog kreiran! Provjeri email i potvrdi registraciju.");
                            }
                        });
                    }
                    @Override
                    public void onError(String msg) {
                        cb.onError("Greška pri čuvanju profila: " + msg);
                    }
                });
            }
            @Override
            public void onError(String msg) {
                cb.onError(translateAuthError(msg));
            }
        });
    }

    // ── Prijava ───────────────────────────────────────────────────────────────

    public void login(String emailOrUsername, String password, Callback cb) {
        if (emailOrUsername.isEmpty() || password.isEmpty()) {
            cb.onError("Popuni email/korisničko ime i lozinku");
            return;
        }

        if (emailOrUsername.contains("@")) {
            doSignIn(emailOrUsername, password, cb);
        } else {
            // korisničko ime → pronađi email pa se prijavi
            repo.getEmailByUsername(emailOrUsername, new UserRepository.StringCallback() {
                @Override
                public void onResult(String email) {
                    doSignIn(email, password, cb);
                }
                @Override
                public void onError(String msg) {
                    cb.onError("Korisničko ime nije pronađeno");
                }
            });
        }
    }

    private void doSignIn(String email, String password, Callback cb) {
        repo.signIn(email, password, new UserRepository.Callback() {
            @Override
            public void onSuccess() {
                FirebaseUser u = repo.getCurrentUser();
                if (u != null && !u.isEmailVerified()) {
                    repo.logout();
                    cb.onError("Email nije potvrđen. Provjeri inbox i klikni na link.");
                } else {
                    cb.onSuccess("Dobrodošao!");
                }
            }
            @Override
            public void onError(String msg) {
                cb.onError(translateAuthError(msg));
            }
        });
    }

    // ── Promena lozinke (stara + nova x2) ────────────────────────────────────

    public void changePassword(String oldPassword, String newPassword,
                               String confirmNew, Callback cb) {
        if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmNew.isEmpty()) {
            cb.onError("Popuni sva polja za promenu lozinke");
            return;
        }
        if (!newPassword.equals(confirmNew)) {
            cb.onError("Nove lozinke se ne poklapaju");
            return;
        }
        if (newPassword.length() < 6) {
            cb.onError("Nova lozinka mora imati najmanje 6 znakova");
            return;
        }
        if (newPassword.equals(oldPassword)) {
            cb.onError("Nova lozinka mora biti različita od stare");
            return;
        }

        FirebaseUser u = repo.getCurrentUser();
        if (u == null || u.getEmail() == null) {
            cb.onError("Nisi prijavljen");
            return;
        }

        repo.reauthenticate(u.getEmail(), oldPassword, new UserRepository.Callback() {
            @Override
            public void onSuccess() {
                repo.updatePassword(newPassword, new UserRepository.Callback() {
                    @Override public void onSuccess() { cb.onSuccess("Lozinka uspešno promenjena"); }
                    @Override public void onError(String msg) { cb.onError(msg); }
                });
            }
            @Override
            public void onError(String msg) {
                cb.onError(msg);
            }
        });
    }

    // ── Prijevod Firebase grešaka na srpski ──────────────────────────────────

    private String translateAuthError(String msg) {
        if (msg == null) return "Nepoznata greška";
        if (msg.contains("email address is already in use"))
            return "Email je već registrovan";
        if (msg.contains("badly formatted"))
            return "Neispravan format email adrese";
        if (msg.contains("password is invalid") || msg.contains("INVALID_LOGIN_CREDENTIALS"))
            return "Pogrešan email ili lozinka";
        if (msg.contains("no user record") || msg.contains("USER_NOT_FOUND"))
            return "Nalog sa tim email-om ne postoji";
        if (msg.contains("network"))
            return "Greška mreže — provjeri internet vezu";
        if (msg.contains("too many requests"))
            return "Previše pokušaja. Pokušaj ponovo za nekoliko minuta.";
        return msg;
    }
}
