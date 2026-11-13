package com.example.signinapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

/**
 * MainActivity handles the Sign In screen.
 *
 * <p>Users sign in with an email and password through Firebase Authentication.
 * On success they are taken to {@link HomeActivity}.</p>
 */
public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnSignIn;
    private TextView tvGoToSignUp;
    private FirebaseAuth mAuth;

    /**
     * Initializes the Sign In screen and its UI components.
     *
     * @param savedInstanceState previously saved activity state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Authentication
        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvGoToSignUp = findViewById(R.id.tvGoToSignUp);

        // Handle Sign In button click
        btnSignIn.setOnClickListener(v -> signInUser());

        // Navigate to Sign Up screen
        tvGoToSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this,
                    SignUpActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Validates the email and password fields, then signs the user in with
     * Firebase Authentication. Shows a toast if a field is empty, the email is
     * malformed, or the sign-in fails.
     */
    private void signInUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Please enter your email and password",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this,
                    "Please enter a valid email address",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent double taps while the request is running
        btnSignIn.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        openHome();
                    } else {
                        btnSignIn.setEnabled(true);
                        Toast.makeText(this,
                                describeError(task.getException()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Converts a sign-in exception into a short, user-friendly message.
     *
     * @param e the exception returned by Firebase, may be null
     * @return the message to show to the user
     */
    private String describeError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password";
        }
        return "Sign in failed: "
                + (e != null ? e.getMessage() : "Unknown error");
    }

    /**
     * Opens the home screen and closes the Sign In screen so the Back button
     * does not return to it.
     */
    private void openHome() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}