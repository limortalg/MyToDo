package limor.tal.mytodo;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FirebaseAuthService {
    private static final String TAG = "FirebaseAuthService";
    public static final int RC_SIGN_IN = 9001;
    
    private Context context;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore db;
    private AuthCallback authCallback;

    public interface AuthCallback {
        void onAuthSuccess(User user);
        void onAuthError(String error);
    }

    public FirebaseAuthService(Context context) {
        this.context = context;
        this.mAuth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        setupGoogleSignIn();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(context, gso);
    }

    public Intent getSignInIntent() {
        return mGoogleSignInClient.getSignInIntent();
    }

    public void handleSignInResult(Intent data, AuthCallback callback) {
        this.authCallback = callback;
        
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            firebaseAuthWithGoogle(account);
        } catch (ApiException e) {
            Log.w(TAG, "Google sign in failed", e);
            if (authCallback != null) {
                authCallback.onAuthError("Google sign in failed: " + e.getMessage());
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                saveUserToFirestore(user);
                                createUserObject(user);
                            }
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            if (authCallback != null) {
                                authCallback.onAuthError("Firebase authentication failed: " + 
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                            }
                        }
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser firebaseUser) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", firebaseUser.getUid());
        userData.put("email", firebaseUser.getEmail());
        userData.put("displayName", firebaseUser.getDisplayName());
        userData.put("photoUrl", firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null);
        userData.put("lastLogin", new Date());

        db.collection("users").document(firebaseUser.getUid())
                .set(userData)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User data saved to Firestore");
                        } else {
                            Log.w(TAG, "Error saving user data to Firestore", task.getException());
                        }
                    }
                });
    }

    private void createUserObject(FirebaseUser firebaseUser) {
        User user = new User(
            firebaseUser.getUid(),
            firebaseUser.getEmail(),
            firebaseUser.getDisplayName(),
            firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null
        );

        if (authCallback != null) {
            authCallback.onAuthSuccess(user);
        }
    }

    public void signOut() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "User signed out");
            }
        });
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public boolean isUserSignedIn() {
        return mAuth.getCurrentUser() != null;
    }
}
