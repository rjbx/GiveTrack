package com.github.rjbx.givetrack.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;

import com.github.rjbx.givetrack.BuildConfig;
import com.github.rjbx.givetrack.R;
import com.github.rjbx.givetrack.data.UserPreferences;
import com.github.rjbx.givetrack.data.UserProfile;
import com.github.rjbx.givetrack.data.DataService;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a login screen.
 */
public class AuthActivity extends AppCompatActivity {

    private static final int REQUEST_SIGN_IN = 0;

    public static final String ACTION_SIGN_IN = "com.github.rjbx.givetrack.ui.action.SIGN_IN";
    public static final String ACTION_SIGN_OUT = "com.github.rjbx.givetrack.ui.action.SIGN_OUT";
    public static final String ACTION_DELETE_ACCOUNT = "com.github.rjbx.givetrack.ui.action.DELETE_ACCOUNT";

    private FirebaseAuth mFirebaseAuth;
    private FirebaseDatabase mFirebaseDatabase;
    @BindView(R.id.auth_progress) ProgressBar mProgressbar;

    /**
     * Handles sign in, sign out, and account deletion launch Intent actions.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        ButterKnife.bind(this);

        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        String launchingAction = getIntent().getAction();
        final UserProfile user = UserPreferences.generateUserProfile(AuthActivity.this);

        if (launchingAction != null) {
            switch (launchingAction) {
                case ACTION_SIGN_OUT:
                    mFirebaseDatabase.getReference("users").child(user.getUid())
                            .updateChildren(user.toParameterMap())
                            .addOnCompleteListener(updatedChildrenTask ->
                                    AuthUI.getInstance().signOut(AuthActivity.this)
                                            .addOnCompleteListener(signedOutTask -> {
                                                finish();
                                                startActivity(new Intent(AuthActivity.this, AuthActivity.class).setAction(Intent.ACTION_MAIN));
                                                Toast.makeText(AuthActivity.this, getString(R.string.message_logout), Toast.LENGTH_SHORT).show();
                                            }));
                    break;
                case ACTION_DELETE_ACCOUNT:
                    DataService.startActionResetData(AuthActivity.this);
                    FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
                    if (firebaseUser == null || firebaseUser.getEmail() == null) return;
                    String userId = firebaseUser.getUid();
                    firebaseUser.reauthenticate(EmailAuthProvider.getCredential(firebaseUser.getEmail(), getString(R.string.message_password_request)))
                            .addOnCompleteListener(signedOutTask ->
                                    firebaseUser.delete().addOnCompleteListener(completedTask -> {
                                        if (completedTask.isSuccessful()) {
                                            mFirebaseDatabase.getReference("users").child(userId).removeValue()
                                                    .addOnCompleteListener(removedValueTask -> {
                                                        AuthUI.getInstance().signOut(this);
                                                        finish();
                                                        startActivity(new Intent(AuthActivity.this, AuthActivity.class).setAction(Intent.ACTION_MAIN));
                                                        Toast.makeText(AuthActivity.this, getString(R.string.message_data_erase), Toast.LENGTH_LONG).show();
                                                    });
                                        }
                                    })
                            );
                    break;
                case Intent.ACTION_MAIN:
                    if (mFirebaseAuth.getCurrentUser() == null) {

                        List<AuthUI.IdpConfig> providers = new ArrayList<>();
                        providers.add(new AuthUI.IdpConfig.GoogleBuilder().build());
                        providers.add(new AuthUI.IdpConfig.EmailBuilder().build());
                        providers.add(new AuthUI.IdpConfig.AnonymousBuilder().build());

                        Intent signIn = AuthUI.getInstance().createSignInIntentBuilder()
                                .setLogo(R.mipmap.ic_launcher_round)
                                .setTosAndPrivacyPolicyUrls("https://github.com/rjbx/Givetrack", "https://github.com/rjbx/Givetrack")
                                .setTheme(R.style.AppTheme_AuthOverlay)
                                .setIsSmartLockEnabled(false, true)
                                .setAvailableProviders(providers)
                                .build();
                        startActivityForResult(signIn, REQUEST_SIGN_IN);

                    } else {
                        finish();
                        Toast.makeText(this, getString(R.string.message_login), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class).setAction(AuthActivity.ACTION_SIGN_IN));
                    }
            }
        }
    }

    /**
     * Hides {@link ProgressBar} when launching AuthUI.
     */
    @Override
    protected void onStop() {
        mProgressbar.setVisibility(View.GONE);
        super.onStop();
    }

    /**
     * Defines behavior on user submission of login credentials.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                mFirebaseDatabase.getReference("users").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
                        if (firebaseUser == null) return;
                        Timber.v(firebaseUser.getUid());
                        UserProfile user = dataSnapshot.child(firebaseUser.getUid()).getValue(UserProfile.class);
                        if (user == null) user = UserPreferences.generateUserProfile(AuthActivity.this);
                        PreferenceManager.getDefaultSharedPreferences(AuthActivity.this)
                                .registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
                                finish();
                                    Toast.makeText(AuthActivity.this, getString(R.string.message_login), Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(AuthActivity.this, MainActivity.class).setAction(AuthActivity.ACTION_SIGN_IN));
                                });
                        UserPreferences.replaceSharedPreferences(AuthActivity.this, user);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError databaseError) { Timber.e(databaseError.getMessage()); }
                });
            } else {
                IdpResponse response = IdpResponse.fromResultIntent(data);
                mProgressbar.setVisibility(View.VISIBLE);
                String message;
                if (response == null) message = getString(R.string.network_error_message);
                else message = getString(R.string.provider_error_message, response.getProviderType());
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() { finish(); }
}