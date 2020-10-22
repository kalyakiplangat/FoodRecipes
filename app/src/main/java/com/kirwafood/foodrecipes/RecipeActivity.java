package com.kirwafood.foodrecipes;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.kirwafood.foodrecipes.models.Recipe;
import com.kirwafood.foodrecipes.viewmodels.RecipeViewModel;

import java.util.ArrayList;
import java.util.List;

public class RecipeActivity extends BaseActivity implements PurchasesUpdatedListener {

    private static final String TAG = "RecipeActivity";

    // UI components
    private AppCompatImageView mRecipeImage;
    private TextView mRecipeTitle, mRecipeRank;
    private LinearLayout mRecipeIngredientsContainer;
    private ScrollView mScrollView;

    private BillingClient billingClient;
    private List skuList = new ArrayList();

    private String sku = "in_app_product";

    private Button buttonBuyProduct;

    private SkuDetails mSkuDetails;

    private RecipeViewModel mRecipeViewModel;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe);
        mRecipeImage = findViewById(R.id.recipe_image);
        mRecipeTitle = findViewById(R.id.recipe_title);
        mRecipeRank = findViewById(R.id.recipe_social_score);
        mRecipeIngredientsContainer = findViewById(R.id.ingredients_container);
        mScrollView = findViewById(R.id.parent);
        buttonBuyProduct = findViewById(R.id.btn_subscribe);

        mRecipeViewModel = ViewModelProviders.of(this).get(RecipeViewModel.class);

        showProgressBar(true);
        subscribeObservers();
        getIncomingIntent();
        setupBillingClient();
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener(){

            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is setup successfully
                    loadAllSKUs();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        });
    }

    private void loadAllSKUs() {
        Toast.makeText(RecipeActivity.this, "", Toast.LENGTH_SHORT).show();

        if (billingClient.isReady())
        {
            Toast.makeText(RecipeActivity.this, "billingclient ready", Toast.LENGTH_SHORT).show();
            SkuDetailsParams params = SkuDetailsParams.newBuilder()
                    .setSkusList(skuList)
                    .setType(BillingClient.SkuType.INAPP)
                    .build();

            billingClient.querySkuDetailsAsync(params, new SkuDetailsResponseListener() {
                @Override
                public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                    Toast.makeText(RecipeActivity.this, "inside query" + billingResult.getResponseCode(), Toast.LENGTH_SHORT).show();
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && !skuDetailsList.isEmpty())
                    {
                        for (Object skuDetailsObject : skuDetailsList) {
                            final SkuDetails skuDetails = (SkuDetails) skuDetailsObject;
                            Toast.makeText(RecipeActivity.this, "" + skuDetails.getSku(), Toast.LENGTH_SHORT).show();

                            if (skuDetails.getSku() == sku)
                                mSkuDetails = skuDetails;
                            buttonBuyProduct.setEnabled(true);

                            buttonBuyProduct.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    BillingFlowParams billingFlowParams = BillingFlowParams
                                            .newBuilder()
                                            .setSkuDetails(skuDetails)
                                            .build();
                                    billingClient.launchBillingFlow(RecipeActivity.this, billingFlowParams);

                                }
                            });
                        }
                    }
                }
            });
        }
        else
            Toast.makeText(RecipeActivity.this, "billingclient not ready", Toast.LENGTH_SHORT).show();
    }

    private void getIncomingIntent(){
        if(getIntent().hasExtra("recipe")){
            Recipe recipe = getIntent().getParcelableExtra("recipe");
            Log.d(TAG, "getIncomingIntent: " + recipe.getTitle());
            mRecipeViewModel.searchRecipeById(recipe.getRecipe_id());
        }
    }

    private void subscribeObservers(){
        mRecipeViewModel.getRecipe().observe(this, new Observer<Recipe>() {
            @Override
            public void onChanged(@Nullable Recipe recipe) {
                if(recipe != null){
                    if(recipe.getRecipe_id().equals(mRecipeViewModel.getRecipeId())){
                        setRecipeProperties(recipe);
                        mRecipeViewModel.setRetrievedRecipe(true);
                    }
                }
            }
        });

        mRecipeViewModel.isRecipeRequestTimedOut().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if(aBoolean && !mRecipeViewModel.didRetrieveRecipe()){
                    Log.d(TAG, "onChanged: timed out..");
                    displayErrorScreen("Error retrieving data. Check network connection.");
                }
            }
        });
    }

    private void displayErrorScreen(String errorMessage){
        mRecipeTitle.setText("Error retrieveing recipe...");
        mRecipeRank.setText("");
        TextView textView = new TextView(this);
        if(!errorMessage.equals("")){
            textView.setText(errorMessage);
        }
        else{
            textView.setText("Error");
        }
        textView.setTextSize(15);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mRecipeIngredientsContainer.addView(textView);

        RequestOptions requestOptions = new RequestOptions()
                .placeholder(R.drawable.ic_launcher_background);

        Glide.with(this)
                .setDefaultRequestOptions(requestOptions)
                .load(R.drawable.ic_launcher_background)
                .into(mRecipeImage);

        showParent();
        showProgressBar(false);
    }

    private void setRecipeProperties(Recipe recipe){
        if(recipe != null){
            RequestOptions requestOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_launcher_background);

            Glide.with(this)
                    .setDefaultRequestOptions(requestOptions)
                    .load(recipe.getImage_url())
                    .into(mRecipeImage);

            mRecipeTitle.setText(recipe.getTitle());
            mRecipeRank.setText(String.valueOf(Math.round(recipe.getSocial_rank())));

            mRecipeIngredientsContainer.removeAllViews();
            for(String ingredient: recipe.getIngredients()){
                TextView textView = new TextView(this);
                textView.setText(ingredient);
                textView.setTextSize(15);
                textView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                mRecipeIngredientsContainer.addView(textView);
            }
        }

        showParent();
        showProgressBar(false);
    }

    private void showParent(){
        mScrollView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
        else
        if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
            //Log.d(TAG, "User Canceled" + responseCode);
        }
        else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            ///mSharedPreferences.edit().putBoolean(getResources().getString(R.string.pref_remove_ads_key), true).commit();
            ///setAdFree(true);
//            setBoolInPref(this,"myPref",sku, true );
        }
        else {
            //Log.d(TAG, "Other code" + responseCode);
            // Handle any other error codes.
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getSku().equals(sku)) {
            ///mSharedPreferences.edit().putBoolean(getResources().getString(R.string.pref_remove_ads_key), true).commit();
            ///setAdFree(true);
//            setBoolInPref(this,"myPref",sku, true );
            Toast.makeText(this, "Purchase done. you are now a premium member.", Toast.LENGTH_SHORT).show();
        }
    }

    private Boolean getBoolFromPref(Context context, String prefName, String constantName) {
        SharedPreferences pref = context.getSharedPreferences(prefName, 0); // 0 - for private mode

        return pref.getBoolean(constantName, false);

    }

    private void setBoolInPref(Context context,String prefName, String constantName, Boolean val) {
        SharedPreferences pref = context.getSharedPreferences(prefName, 0); // 0 - for private mode

        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(constantName, val);
        editor.commit();
    }
}














