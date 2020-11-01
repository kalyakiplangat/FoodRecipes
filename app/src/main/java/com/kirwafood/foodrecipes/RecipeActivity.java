package com.kirwafood.foodrecipes;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.view.PointerIconCompat;
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
import com.android.billingclient.util.BillingHelper;
import com.android.vending.billing.IInAppBillingService;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.kirwafood.foodrecipes.models.Recipe;
import com.kirwafood.foodrecipes.viewmodels.RecipeViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class RecipeActivity extends BaseActivity {

    private static final String TAG = "RecipeActivity";

    // UI components
    private AppCompatImageView mRecipeImage;
    private TextView mRecipeTitle, mRecipeRank;
    private LinearLayout mRecipeIngredientsContainer;
    private ScrollView mScrollView;

    private BillingClient billingClient;

    private String sku = "product_one";

    private Button buttonBuyProduct;

    private RecipeViewModel mRecipeViewModel;

    private IInAppBillingService mService;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };


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
        Context context = getApplicationContext();

        buttonBuyProduct.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (NetworkUtil.hasNetwork(RecipeActivity.this)) {
                    Bundle bundle = null;
                    try {
                        bundle = RecipeActivity.this.mService.getBuyIntent(3, RecipeActivity.this.getPackageName(), "in_app_product", BillingClient.SkuType.SUBS, "bGoa+V7g/yqDXvKRqq+JTFn4uQZbPiQJo4pf9RzJ");
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    try {
                        RecipeActivity.this.startIntentSenderForResult(((PendingIntent) bundle.getParcelable(BillingHelper.RESPONSE_BUY_INTENT_KEY)).getIntentSender(), PointerIconCompat.TYPE_CONTEXT_MENU, new Intent(), Integer.valueOf(0).intValue(), Integer.valueOf(0).intValue(), Integer.valueOf(0).intValue());
                    } catch (IntentSender.SendIntentException e2) {
                        e2.printStackTrace();
                    }
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(RecipeActivity.this);
                    builder.setTitle((CharSequence) "No internet connection");
                    builder.setMessage((CharSequence) "Please make sure you have a working internet connection");
                    builder.show();
                }
            }
        });
//        this.mServiceConn = new ServiceConnection() {
//            public void onServiceDisconnected(ComponentName componentName) {
//                RecipeActivity.this.mService = null;
//            }
//
//            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//                RecipeActivity.this.mService = IInAppBillingService.Stub.asInterface(iBinder);
//            }
//        };
        Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        intent.setPackage("com.android.vending");
        bindService(intent, this.mServiceConn, Context.BIND_AUTO_CREATE);
        ArrayList arrayList2 = new ArrayList();
        arrayList2.add("in_app_product");
        final Bundle bundle2 = new Bundle();
        bundle2.putStringArrayList("ITEM_ID_LIST", arrayList2);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                try {
                    Bundle skuDetails = RecipeActivity.this.mService.getSkuDetails(3, RecipeActivity.this.getPackageName(), BillingClient.SkuType.SUBS, bundle2);
                    if (skuDetails.getInt(BillingHelper.RESPONSE_CODE) == 0) {
                        Iterator it = skuDetails.getStringArrayList(BillingHelper.RESPONSE_GET_SKU_DETAILS_LIST).iterator();
                        while (it.hasNext()) {
                            JSONObject jSONObject = null;
                            try {
                                jSONObject = new JSONObject((String) it.next());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            try {
                                jSONObject.getString("productId");
                            } catch (JSONException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e3) {
                    Log.e("trist", e3.toString());
                }
            }
        }, 3000);
    }

    private void getIncomingIntent() {
        if (getIntent().hasExtra("recipe")) {
            Recipe recipe = getIntent().getParcelableExtra("recipe");
            Log.d(TAG, "getIncomingIntent: " + recipe.getTitle());
            mRecipeViewModel.searchRecipeById(recipe.getRecipe_id());
        }
    }

    private void subscribeObservers() {
        mRecipeViewModel.getRecipe().observe(this, new Observer<Recipe>() {
            @Override
            public void onChanged(@Nullable Recipe recipe) {
                if (recipe != null) {
                    if (recipe.getRecipe_id().equals(mRecipeViewModel.getRecipeId())) {
                        setRecipeProperties(recipe);
                        mRecipeViewModel.setRetrievedRecipe(true);
                    }
                }
            }
        });

        mRecipeViewModel.isRecipeRequestTimedOut().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if (aBoolean && !mRecipeViewModel.didRetrieveRecipe()) {
                    Log.d(TAG, "onChanged: timed out..");
                    displayErrorScreen("Error retrieving data. Check network connection.");
                }
            }
        });
    }

    private void displayErrorScreen(String errorMessage) {
        mRecipeTitle.setText("Error retrieveing recipe...");
        mRecipeRank.setText("");
        TextView textView = new TextView(this);
        if (!errorMessage.equals("")) {
            textView.setText(errorMessage);
        } else {
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

    private void setRecipeProperties(Recipe recipe) {
        if (recipe != null) {
            RequestOptions requestOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_launcher_background);

            Glide.with(this)
                    .setDefaultRequestOptions(requestOptions)
                    .load(recipe.getImage_url())
                    .into(mRecipeImage);

            mRecipeTitle.setText(recipe.getTitle());
            mRecipeRank.setText(String.valueOf(Math.round(recipe.getSocial_rank())));

            mRecipeIngredientsContainer.removeAllViews();
            for (String ingredient : recipe.getIngredients()) {
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

    private void showParent() {
        mScrollView.setVisibility(View.VISIBLE);
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mService != null) {
            unbindService(this.mServiceConn);
        }
    }

    /* access modifiers changed from: protected */
    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == 1001) {
            intent.getIntExtra(BillingHelper.RESPONSE_CODE, 0);
            String stringExtra = intent.getStringExtra("INAPP_PURCHASE_DATA");
            intent.getStringExtra("INAPP_DATA_SIGNATURE");
            if (i2 == -1) {
                try {
                    new JSONObject(stringExtra);
                    Toast.makeText(this, "Thank you for subscribing", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Toast.makeText(this, "An error occurred", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }
    }
}














