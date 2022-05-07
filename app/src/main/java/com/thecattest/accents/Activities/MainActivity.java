package com.thecattest.accents.Activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.thecattest.accents.Data.ApiService;
import com.thecattest.accents.Data.Category;
import com.thecattest.accents.Data.Dictionary;
import com.thecattest.accents.Managers.JSONManager;
import com.thecattest.accents.Managers.TasksManager;
import com.thecattest.accents.R;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    public static final String SHARED_PREF_KEY = "ACCENTS_SHARED_PREF";
    public static final String THEME = "ACCENTS_SHARED_PREF_THEME";
    public static final int THEME_DARK = 1;
    public static final int THEME_LIGHT = 2;
    public static final String CATEGORY_TITLE = "CATEGORY_TITLE";

    private long lastTimeBackPressed = 0;

    private LinearLayout wordPlaceholder;
    private TextView commentPlaceholder;
    private ConstraintLayout root;
    private TabLayout categoriesNavigation;
    private MaterialToolbar toolbar;
    private boolean madeMistake;

    private Dictionary dictionary;
    private Category category;
    private JSONManager jsonManager;
    private Retrofit retrofit;
    private ApiService apiService;

    private final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        jsonManager = new JSONManager(this);
        try {
            dictionary = jsonManager.readObjectFromFile(Dictionary.FILENAME, new Dictionary());
        } catch (FileNotFoundException e) {
            // Toast.makeText(this, "dictionary not found", Toast.LENGTH_SHORT).show();
            dictionary = jsonManager.gson.fromJson(
                    jsonManager.filesManager.readFromRawResource(R.raw.dictionary),
                    Dictionary.class);
            jsonManager.writeObjectToFile(dictionary, Dictionary.FILENAME);
        } catch (IOException e) {
            Toast.makeText(this, R.string.ioexception, Toast.LENGTH_SHORT).show();
            dictionary = jsonManager.gson.fromJson(
                    jsonManager.filesManager.readFromRawResource(R.raw.dictionary),
                    Dictionary.class);
            jsonManager.writeObjectToFile(dictionary, Dictionary.FILENAME);
        }

        findViews();
        setListeners();
        initRetrofit();
        initCategoriesNavigation();

        SharedPreferences sharedPref = getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        int theme = sharedPref.getInt(THEME, THEME_LIGHT);
        if(theme == THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            toolbar.getMenu().getItem(2).setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_light, null));
        }
        else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            toolbar.getMenu().getItem(2).setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_dark, null));
        }

        next();
    }

    private void initCategoriesNavigation() {
        ArrayList<String> titles = dictionary.getCategoriesTitles();
        categoriesNavigation.removeAllTabs();
        for (String categoryTitle : titles)
            categoriesNavigation.addTab(categoriesNavigation.newTab().setText(categoryTitle));
        category = dictionary.categories.get(0);
    }

    private void initRetrofit() {
        retrofit = new Retrofit.Builder()
                .baseUrl(Dictionary.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);
    }

    public void findViews() {
        wordPlaceholder = findViewById(R.id.wordPlaceholder);
        commentPlaceholder = findViewById(R.id.extra);
        root = findViewById(R.id.root);
        categoriesNavigation = findViewById(R.id.categoriesNavigation);
        toolbar = findViewById(R.id.topAppBar);
    }

    public void setListeners() {
        findViewById(R.id.author).setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.author_url)));
            startActivity(browserIntent);
        });
        findViewById(R.id.dictionary).setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.dictionary_url)));
            startActivity(browserIntent);
        });

        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        categoriesNavigation.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String tabText = (String) tab.getText();
                category = dictionary.getCategory(tabText);
                next();
            }
            public void onTabUnselected(TabLayout.Tab tab) { }
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis() / 1000L;
        if (currentTime - lastTimeBackPressed <= 3) {
            super.onBackPressed();
        } else {
            lastTimeBackPressed = currentTime;
            Toast.makeText(this, R.string.ioexception, Toast.LENGTH_LONG).show();
        }
    }

    private void next() {
        String task;
        try {
            task = category.next(jsonManager);
        } catch (IOException e) {
            Toast.makeText(this, R.string.ioexception, Toast.LENGTH_LONG).show();
            return;
        }
        
        commentPlaceholder.setText(TasksManager.getComment(task, category.getTaskType()));
        wordPlaceholder.removeAllViews();
        madeMistake = false;
        
        for (TextView tv : TasksManager.getTextViews(this, task, category.getTaskType()))
            wordPlaceholder.addView(tv, layoutParams);
    }

    private void animateBackground(int colorId) {
        TransitionDrawable transition = (TransitionDrawable) ResourcesCompat.getDrawable(getResources(), colorId, null);
        if (transition != null) {
            root.setBackground(transition);
            transition.startTransition(200);
        }
    }

    private void vibrate(int vibrationDuration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(vibrationDuration);
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh) {
            Call<Dictionary> call = apiService.getDictionary();
            call.enqueue(new Callback<Dictionary>() {
                @Override
                public void onResponse(Call<Dictionary> call, Response<Dictionary> response) {
                    dictionary = response.body();
                    try {
                        dictionary.sync(jsonManager);
                        initCategoriesNavigation();
                        next();
                        Toast.makeText(MainActivity.this, R.string.synced, Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, R.string.ioexception, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Dictionary> call, Throwable t) {
                    Toast.makeText(MainActivity.this, R.string.sync_request_error, Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }
        if (itemId == R.id.theme) {
            SharedPreferences sharedPref = getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                editor.putInt(THEME, THEME_LIGHT);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                editor.putInt(THEME, THEME_DARK);
            }
            editor.apply();
        }
        if (itemId == R.id.words) {
            Intent i = new Intent(MainActivity.this, WordsListActivity.class);
            i.putExtra(CATEGORY_TITLE, category.getTitle());
            startActivity(i);
            return true;
        }
        return false;
    }

    public View.OnClickListener getTextViewClickListener(String task, boolean correct) {
        return view -> {
            //animateBackground(Character.isUpperCase(c) ? R.drawable.correct : R.drawable.incorrect);
            vibrate(correct ? 15 : 200);
            if (correct) {
                try {
                    category.saveAnswer(task, madeMistake, jsonManager);
                } catch (IOException e) {
                    Toast.makeText(this, R.string.ioexception, Toast.LENGTH_LONG).show();
                }
                next();
            }
            else
                madeMistake = true;
        };
    }

}