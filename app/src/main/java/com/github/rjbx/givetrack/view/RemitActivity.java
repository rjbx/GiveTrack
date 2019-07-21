package com.github.rjbx.givetrack.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.rjbx.givetrack.R;
import com.google.android.gms.wallet.*;
import com.google.android.gms.common.api.*;
import com.google.android.gms.identity.intents.model.*;

import com.stripe.android.exception.StripeException;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.Token;

import java.util.Arrays;
import java.util.zip.DataFormatException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;


/**
 * Provides a UI for and manages payment confirmation and processing.
 */
public class RemitActivity extends AppCompatActivity {

    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 1;
    private float mPrice;
    private PaymentsClient mPaymentsClient;
    @BindView(R.id.remit_action_bar) Button mConfirmButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remit);
        ButterKnife.bind(this);

        mPaymentsClient
                = Wallet.getPaymentsClient(this, new Wallet
                        .WalletOptions.Builder()
                        .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                        .build()
        );

        isReadyToPay();
    }

    @OnClick(R.id.remit_action_bar) void confirmPayment() {
            PaymentDataRequest request = createPaymentDataRequest();
            if (request != null) {
                AutoResolveHelper.resolveTask(
                        mPaymentsClient.loadPaymentData(request),
                        this,
                        LOAD_PAYMENT_DATA_REQUEST_CODE);
            }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
                switch (resultCode) {

                    case RESULT_OK:
                        if (data == null) throw new DataFormatException();
                        PaymentData paymentData = PaymentData.getFromIntent(data);

                        if (paymentData == null) throw new DataFormatException();
                        PaymentMethodToken paymentMethodToken = paymentData.getPaymentMethodToken();

                        if (paymentMethodToken == null) throw new DataFormatException();
                        String rawToken = paymentMethodToken.getToken();

                        Token stripeToken = Token.fromString(rawToken);
                        if (stripeToken != null) {
                            chargeToken(stripeToken.getId());
                        }
                        break;

                    case RESULT_CANCELED:
                        Timber.d("Payment was cancelled");
                        break;

                    case AutoResolveHelper.RESULT_ERROR:
                        Status status = AutoResolveHelper.getStatusFromIntent(data);
                        String statusMessage
                                = status != null ? status.getStatusMessage() : "Status unavailable";
                        Timber.e(statusMessage);
                        break;

                    default: Timber.d("Payment could not be processed");
                }
            }
        } catch (DataFormatException e) {
            Timber.e("Payment data is unavailable");
        }
    }

    private void chargeToken(String tokenId) {

    }

    private void isReadyToPay() {
        IsReadyToPayRequest request = IsReadyToPayRequest.newBuilder()
                .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
                .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
                .build();
        mPaymentsClient.isReadyToPay(request).addOnCompleteListener((task) -> {
                        try {
                            final Boolean result = task.getResult(ApiException.class);
                            if (result != null && result) createPaymentDataRequest();
                        } catch (NullPointerException|ApiException e) {
                            Toast.makeText(
                                    this,
                                    R.string.payment_error_message,
                                    Toast.LENGTH_LONG)
                                .show();
                        }
                    });
    }

    private PaymentMethodTokenizationParameters createTokenizationParameters() {
        return PaymentMethodTokenizationParameters.newBuilder()
                .setPaymentMethodTokenizationType(
                        WalletConstants.PAYMENT_METHOD_TOKENIZATION_TYPE_PAYMENT_GATEWAY)
                .addParameter("gateway", "stripe")
                .addParameter("stripe:key", getString(R.string.sp_api_key))
                .addParameter("stripe:version", "2019-05-16") // Changelog: https://stripe.com/docs/upgrades
                .build();
    }

    private PaymentDataRequest createPaymentDataRequest() {
        return PaymentDataRequest.newBuilder()
                .setTransactionInfo(
                        TransactionInfo.newBuilder()
                                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                                .setTotalPrice(String.valueOf(mPrice))
                                .setCurrencyCode("USD")
                                .build())
                .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
                .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
                .setCardRequirements(
                        CardRequirements.newBuilder()
                                .addAllowedCardNetworks(Arrays.asList(
                                        WalletConstants.CARD_NETWORK_AMEX,
                                        WalletConstants.CARD_NETWORK_DISCOVER,
                                        WalletConstants.CARD_NETWORK_VISA,
                                        WalletConstants.CARD_NETWORK_MASTERCARD))
                                .build())
                .setPaymentMethodTokenizationParameters(createTokenizationParameters())
                .build();
    }
}


