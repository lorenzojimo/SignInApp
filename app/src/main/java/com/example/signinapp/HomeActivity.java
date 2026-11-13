package com.example.signinapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * HomeActivity is shown after a successful sign in. It loads the user's
 * profile (name and photo) from Firestore and lets the user sign out.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private ImageView ivProfile;
    private TextView tvWelcome, tvEmail;
    private Button btnSignOut;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    /**
     * Initializes the home screen and loads the signed-in user's profile. If
     * nobody is signed in, the user is sent back to the Sign In screen.
     *
     * @param savedInstanceState previously saved activity state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ivProfile = findViewById(R.id.ivHomeProfile);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvEmail = findViewById(R.id.tvEmail);
        btnSignOut = findViewById(R.id.btnSignOut);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToSignIn();
            return;
        }

        tvEmail.setText(user.getEmail());
        loadProfile(user);

        btnSignOut.setOnClickListener(v -> signOut());
    }

    /**
     * Loads the user's profile document from Firestore and displays the name
     * and photo. If no profile exists (for example, an older test account),
     * falls back to the display name from the sign-in provider.
     *
     * @param user the currently signed-in Firebase user
     */
    private void loadProfile(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(this, doc -> showProfile(doc, user))
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Could not load profile", e);
                    Toast.makeText(this,
                            "Could not load your profile",
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Fills in the welcome text and profile photo from a Firestore document.
     *
     * @param doc  the user's profile document, which may not exist
     * @param user the currently signed-in Firebase user
     */
    private void showProfile(DocumentSnapshot doc, FirebaseUser user) {
        String firstName = doc.exists() ? doc.getString("firstName") : null;
        String lastName = doc.exists() ? doc.getString("lastName") : null;
        String photoBase64 = doc.exists() ? doc.getString("photoBase64") : null;

        String fullName = ((firstName != null ? firstName : "") + " "
                + (lastName != null ? lastName : "")).trim();
        if (fullName.isEmpty() && user.getDisplayName() != null) {
            fullName = user.getDisplayName();
        }

        tvWelcome.setText(fullName.isEmpty()
                ? "Welcome!"
                : "Welcome, " + fullName + "!");

        if (photoBase64 != null) {
            showPhoto(photoBase64);
        }
    }

    /**
     * Decodes a Base64-encoded image and shows it in the profile ImageView.
     *
     * @param photoBase64 the Base64 string stored in the user's profile
     */
    private void showPhoto(String photoBase64) {
        try {
            byte[] bytes = Base64.decode(photoBase64, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                ivProfile.setImageBitmap(bitmap);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid photo data", e);
        }
    }

    /**
     * Signs the user out of Firebase and returns to the Sign In screen.
     */
    private void signOut() {
        mAuth.signOut();
        goToSignIn();
    }

    /**
     * Opens the Sign In screen and clears the back stack so the Back button
     * cannot return to this screen.
     */
    private void goToSignIn() {
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
