# SignInApp

An Android app for signing in and registering users with Firebase.

## Features

- **Sign In** with email and password (Firebase Authentication)
- **Sign Up** with first name, last name, email, password, password confirmation and a profile photo
- Form validation with toast messages
- **Google Sign-In** (Credential Manager)
- Profile data (name, email, photo thumbnail) stored in Cloud Firestore
- Home screen showing the signed-in user's name and photo, with Sign Out
- Primary color `#FFD83D68`, custom app icon

## Tech stack

- Java, Android SDK (min SDK 26)
- Firebase Authentication
- Cloud Firestore (the profile photo is stored as a small Base64 JPEG thumbnail, which keeps the project on the free Spark plan)
- AndroidX Credential Manager for Google Sign-In

## Project structure

| File | Purpose |
|---|---|
| `MainActivity.java` | Sign In screen |
| `SignUpActivity.java` | Registration screen and Google Sign-In |
| `HomeActivity.java` | Shows the signed-in user's profile |

## Setup

`google-services.json` is not included in this repository because it contains project keys.

1. Create a Firebase project and add an Android app with package name `com.example.signinapp`.
2. Add your debug SHA-1 fingerprint (`./gradlew signingReport`).
3. Enable **Authentication > Sign-in method > Email/Password** and **Google**.
4. Create a **Firestore Database** and publish these rules:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

5. Download `google-services.json` and place it in the `app/` folder.
6. Open the project in Android Studio, sync Gradle, and run.
