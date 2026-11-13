package com.example.signinapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SignUpActivity handles new user registration.
 *
 * <p>Accounts are created with Firebase Authentication. The user's first name,
 * last name, email and a small Base64-encoded profile thumbnail are stored in the
 * Firestore "users" collection, in a document named after the user's UID.</p>
 */
public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private static final String GOOGLE_TAG = "GoogleSignIn";

    /** Longest side, in pixels, of the stored profile thumbnail. */
    private static final int MAX_IMAGE_SIZE_PX = 256;

    /** JPEG compression quality (0-100) for the stored thumbnail. */
    private static final int JPEG_QUALITY = 70;

    private EditText etFirstName, etLastName;
    private EditText etEmail, etPassword, etConfirmPassword;
    private Button btnRegister, btnGoogle;
    private TextView tvGoToSignIn;
    private ImageView ivProfile;
    private Uri selectedImageUri;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> imagePickerLauncher;

    /**
     * Initializes the Sign Up screen, Firebase services and its UI components.
     *
     * @param savedInstanceState previously saved activity state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Initialize Firebase services and Credential Manager
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        // Initialize profile image picker
        ivProfile = findViewById(R.id.ivProfile);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivProfile.setImageURI(uri);
                        ivProfile.setPadding(0, 0, 0, 0);
                        ivProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                }
        );

        // Open gallery when profile image is clicked
        ivProfile.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*")
        );

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnGoogle = findViewById(R.id.btnGoogle);
        tvGoToSignIn = findViewById(R.id.tvGoToSignIn);

        btnRegister.setOnClickListener(v -> registerUser());
        btnGoogle.setOnClickListener(v -> signInWithGoogle());

        tvGoToSignIn.setOnClickListener(v -> finish());
    }

    /**
     * Releases the background thread used for image processing.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    /**
     * Validates the registration form, creates the Firebase account, and then
     * saves the user's profile data.
     */
    private void registerUser() {

        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword =
                etConfirmPassword.getText().toString().trim();

        if (firstName.isEmpty() || lastName.isEmpty()
                || email.isEmpty() || password.isEmpty()
                || confirmPassword.isEmpty()) {

            Toast.makeText(this,
                    "Please fill in all fields",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this,
                    "Please enter a valid email address",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this,
                    "Passwords do not match",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Check profile picture
        if (selectedImageUri == null) {
            Toast.makeText(this,
                    "Please select a profile picture",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent double taps while the request is running
        btnRegister.setEnabled(false);

        // Create a new Firebase account
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null
                            && task.getResult().getUser() != null) {

                        saveProfile(task.getResult().getUser().getUid(),
                                firstName, lastName, email);

                    } else {
                        btnRegister.setEnabled(true);
                        String reason = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(this,
                                "Registration failed: " + reason,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Encodes the selected profile photo on a background thread and writes the
     * user's profile document to Firestore.
     *
     * @param uid       the Firebase UID of the newly created user
     * @param firstName the user's first name
     * @param lastName  the user's last name
     * @param email     the user's email address
     */
    private void saveProfile(String uid, String firstName,
                             String lastName, String email) {
        executor.execute(() -> {
            String photoBase64;
            try {
                photoBase64 = encodeImageToBase64(selectedImageUri);
            } catch (IOException e) {
                Log.e(TAG, "Image encoding failed", e);
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    Toast.makeText(this,
                            "Could not read the selected photo",
                            Toast.LENGTH_LONG).show();
                });
                return;
            }

            Map<String, Object> profile = new HashMap<>();
            profile.put("firstName", firstName);
            profile.put("lastName", lastName);
            profile.put("email", email);
            profile.put("photoBase64", photoBase64);
            profile.put("createdAt", FieldValue.serverTimestamp());

            db.collection("users").document(uid).set(profile)
                    .addOnSuccessListener(this, unused -> {
                        Toast.makeText(this,
                                "Registration successful!",
                                Toast.LENGTH_SHORT).show();

                        // Firebase signs the new user in automatically, so sign
                        // out and let them log in from the Sign In screen
                        mAuth.signOut();

                        // Return to Sign In screen
                        finish();
                    })
                    .addOnFailureListener(this, e -> {
                        Log.e(TAG, "Profile save failed", e);
                        btnRegister.setEnabled(true);
                        Toast.makeText(this,
                                "Account created, but saving the profile failed: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    /**
     * Reads an image from the given Uri, scales it down so its longest side is
     * at most {@link #MAX_IMAGE_SIZE_PX} pixels, compresses it as JPEG, and
     * returns the result as a Base64 string. The output is small enough to fit
     * comfortably inside a Firestore document (1 MiB limit).
     *
     * @param uri the content Uri of the selected image
     * @return the Base64-encoded JPEG thumbnail
     * @throws IOException if the image cannot be opened or decoded
     */
    private String encodeImageToBase64(Uri uri) throws IOException {
        // First pass: read only the image dimensions
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open image");
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }

        // Pick a sample size that avoids loading a huge bitmap into memory
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= MAX_IMAGE_SIZE_PX
                && bounds.outHeight / (sampleSize * 2) >= MAX_IMAGE_SIZE_PX) {
            sampleSize *= 2;
        }

        // Second pass: decode the downsampled bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open image");
            }
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Could not decode image");
        }

        // Scale so the longest side is at most MAX_IMAGE_SIZE_PX
        float scale = Math.min(1f, (float) MAX_IMAGE_SIZE_PX
                / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * Starts the Google sign-in flow using Credential Manager.
     */
    private void signInWithGoogle() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse,
                        GetCredentialException>() {

                    /**
                     * Called when the user picks a Google account.
                     *
                     * @param result the credential response from Credential Manager
                     */
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleCredential(result.getCredential());
                    }

                    /**
                     * Called when Credential Manager cannot return a credential.
                     *
                     * @param e the exception describing the failure
                     */
                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(GOOGLE_TAG, "Credential error", e);
                        Toast.makeText(SignUpActivity.this,
                                "Google sign-in failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Checks that the returned credential is a Google ID token and passes it on
     * to Firebase authentication.
     *
     * @param credential the credential returned by Credential Manager
     */
    private void handleCredential(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                .equals(credential.getType())) {
            GoogleIdTokenCredential googleCred = GoogleIdTokenCredential
                    .createFrom(((CustomCredential) credential).getData());
            firebaseAuthWithGoogle(googleCred);
        } else {
            Log.w(GOOGLE_TAG, "Unexpected credential type: " + credential.getType());
        }
    }

    /**
     * Signs in to Firebase with a Google ID token and creates a profile
     * document for the user if one does not exist yet.
     *
     * @param googleCred the Google credential containing the ID token and name
     */
    private void firebaseAuthWithGoogle(GoogleIdTokenCredential googleCred) {
        AuthCredential firebaseCred =
                GoogleAuthProvider.getCredential(googleCred.getIdToken(), null);

        mAuth.signInWithCredential(firebaseCred).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    createProfileIfMissing(user, googleCred);
                    Toast.makeText(this,
                            "Signed in as " + user.getEmail(),
                            Toast.LENGTH_SHORT).show();
                }
                // Go to the home screen and clear the sign-in screens
                Intent intent = new Intent(SignUpActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Log.e(GOOGLE_TAG, "Firebase auth failed", task.getException());
                Toast.makeText(this,
                        "Authentication failed",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Creates a Firestore profile for a Google user only if none exists, so an
     * existing email/password profile (with its photo) is never overwritten.
     *
     * @param user       the signed-in Firebase user
     * @param googleCred the Google credential holding the user's name
     */
    private void createProfileIfMissing(FirebaseUser user,
                                        GoogleIdTokenCredential googleCred) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        return;
                    }
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("firstName", googleCred.getGivenName());
                    profile.put("lastName", googleCred.getFamilyName());
                    profile.put("email", user.getEmail());
                    profile.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("users").document(user.getUid()).set(profile);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Profile check failed", e));
    }
}
