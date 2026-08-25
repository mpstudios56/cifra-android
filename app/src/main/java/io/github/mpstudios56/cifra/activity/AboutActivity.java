/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package io.github.mpstudios56.cifra.activity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.LocalisedAsset;
import io.github.mpstudios56.cifra.utils.Utils;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 3/24/11 10:20 PM
 */
public class AboutActivity extends AppCompatActivity {

    protected WebView webView;
    protected OnBackPressedCallback onBackPressedCallback;

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle("Cifra " + getAppVersion(this));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // On the whole screen, not on the tabs. The tabs sit at the foot of it,
        // so padding their top by the height of the status bar left a gap in
        // the middle of the screen and let the page underneath slide up behind
        // the clock - and the tabs themselves under the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_base), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        TabLayout tabLayout = findViewById(R.id.tabs);
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        viewPager.setUserInputEnabled(false);

        viewPager.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                WebView webView = new WebView(parent.getContext());
                webView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // Dark before anything is loaded: a web view starts white, and
                // the flash of it was visible on every page turn.
                webView.setBackgroundColor(android.graphics.Color.parseColor("#141414"));
                return new ViewHolder(webView);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ViewHolder vh = (ViewHolder) holder;
                switch (position) {
                    case 0:
                        // Loaded as text rather than by address, so the version can be
                        // written into it: a page kept as a file cannot know it.
                        vh.webView.loadDataWithBaseURL("file:///android_asset/",
                                LocalisedAsset.readStyled(vh.webView.getContext(), "about.htm"),
                                "text/html", "UTF-8", null);
                        break;
                    case 1:
                        vh.webView.loadDataWithBaseURL("file:///android_asset/",
                                LocalisedAsset.readStyled(vh.webView.getContext(), "whatsnew.htm"),
                                "text/html", "UTF-8", null);
                        webView = vh.webView;
                        webView.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView view, String url) {
                                onBackPressedCallback.setEnabled(view.canGoBack());
                            }
                        });
                        break;
                    case 2:
                        vh.webView.loadUrl("file:///android_asset/gpl-2.0-standalone.htm");
                        break;
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 1 && webView != null) {
                    onBackPressedCallback.setEnabled(webView.canGoBack());
                }
                else {
                    onBackPressedCallback.setEnabled(false);
                }
            }
        });

        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                webView.goBack();
            }
        };

        getOnBackPressedDispatcher().addCallback(onBackPressedCallback);

        new TabLayoutMediator(tabLayout, viewPager, true, false,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(R.string.about);
                            break;
                        case 1:
                            tab.setText(R.string.whats_new);
                            break;
                        case 2:
                            tab.setText(R.string.license);
                            break;
                    }
                }).attach();
    }

    public static String getAppVersion(Context context) {
        try {
            PackageInfo info = Utils.getPackageInfo(context);
            return "v. "+info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final WebView webView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            webView = (WebView) itemView;
        }
    }

}
