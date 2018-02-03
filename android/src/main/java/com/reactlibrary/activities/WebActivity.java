package com.reactlibrary.activities;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.basecamp.turbolinks.TurbolinksAdapter;
import com.basecamp.turbolinks.TurbolinksSession;
import com.basecamp.turbolinks.TurbolinksView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter;
import com.reactlibrary.R;
import com.reactlibrary.react.ReactAppCompatActivity;
import com.reactlibrary.util.TurbolinksAction;
import com.reactlibrary.util.TurbolinksRoute;
import com.reactlibrary.util.TurbolinksUtil;

import java.net.MalformedURLException;
import java.net.URL;

import static com.reactlibrary.RNTurbolinksModule.INTENT_INITIAL_VISIT;
import static com.reactlibrary.RNTurbolinksModule.INTENT_MESSAGE_HANDLER;
import static com.reactlibrary.RNTurbolinksModule.INTENT_NAVIGATION_BAR_HIDDEN;
import static com.reactlibrary.RNTurbolinksModule.INTENT_USER_AGENT;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava;

public class WebActivity extends ReactAppCompatActivity implements TurbolinksAdapter, GenericActivity {

    private static final Integer HTTP_FAILURE = 0;
    private static final Integer NETWORK_FAILURE = 1;

    private TurbolinksRoute route;
    private String messageHandler;
    private String userAgent;
    private Boolean initialVisit;
    private Boolean navigationBarHidden;
    private TurbolinksView turbolinksView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        route = new TurbolinksRoute(getIntent());
        initialVisit = getIntent().getBooleanExtra(INTENT_INITIAL_VISIT, true);
        navigationBarHidden = getIntent().getBooleanExtra(INTENT_NAVIGATION_BAR_HIDDEN, false);
        messageHandler = getIntent().getStringExtra(INTENT_MESSAGE_HANDLER);
        userAgent = getIntent().getStringExtra(INTENT_USER_AGENT);

        setContentView(R.layout.activity_web);
        renderToolBar();

        turbolinksView = (TurbolinksView) findViewById(R.id.turbolinks_view);

        if (messageHandler != null) {
            TurbolinksSession.getDefault(this).addJavascriptInterface(this, messageHandler);
        }
        if (userAgent != null) {
            TurbolinksSession.getDefault(this).getWebView().getSettings().setUserAgentString(userAgent);
        }

        TurbolinksUtil.initFileChooser(TurbolinksSession.getDefault(this).getWebView(), this, getApplicationContext());
        TurbolinksSession.getDefault(this).activity(this).adapter(this).view(turbolinksView).visit(route.getUrl());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        TurbolinksSession.getDefault(this)
                .activity(this)
                .adapter(this)
                .restoreWithCachedSnapshot(true)
                .view(turbolinksView)
                .visit(route.getUrl());
    }

    @Override
    public void onReceivedError(int errorCode) {
        WritableMap params = Arguments.createMap();
        params.putInt("code", NETWORK_FAILURE);
        params.putInt("statusCode", 0);
        params.putString("description", "Network Failure.");
        WebViewClient wb = new WebViewClient();
        getEventEmitter().emit("turbolinksError", params);
    }

    @Override
    public void requestFailedWithStatusCode(int statusCode) {
        WritableMap params = Arguments.createMap();
        params.putInt("code", HTTP_FAILURE);
        params.putInt("statusCode", statusCode);
        params.putString("description", "HTTP Failure. Code:" + statusCode);
        getEventEmitter().emit("turbolinksError", params);
    }

    @Override
    public void visitProposedToLocationWithAction(String location, String action) {
        try {
            WritableMap params = Arguments.createMap();
            URL urlLocation = new URL(location);
            params.putString("component", null);
            params.putString("url", urlLocation.toString());
            params.putString("path", urlLocation.getPath());
            params.putString("action", action);
            getEventEmitter().emit("turbolinksVisit", params);
        } catch (MalformedURLException e) {
            Log.e(ReactConstants.TAG, "Error parsing URL. " + e.toString());
        }
    }

    @Override
    public void visitCompleted() {
        renderTitle();
        handleVisitCompleted();
    }

    @Override
    public void onPageFinished() {
    }

    @Override
    public void pageInvalidated() {
    }

    @Override
    public void onBackPressed() {
        if (initialVisit) {
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (route.getActions() == null) return true;
        getMenuInflater().inflate(R.menu.turbolinks_menu, menu);
        for (Bundle bundle : route.getActions()) {
            TurbolinksAction action = new TurbolinksAction(bundle);
            MenuItem menuItem = menu.add(Menu.NONE, action.getId(), Menu.NONE, action.getTitle());
            renderActionIcon(menu, menuItem, action.getIcon());
            if (action.getButton()) menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) return super.onOptionsItemSelected(item);
        getEventEmitter().emit("turbolinksActionPress", item.getItemId());
        return true;
    }

    @Override
    public void renderToolBar() {
        Toolbar turbolinksToolbar = (Toolbar) findViewById(R.id.turbolinks_toolbar);
        setSupportActionBar(turbolinksToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(!initialVisit);
        getSupportActionBar().setDisplayShowHomeEnabled(!initialVisit);
        getSupportActionBar().setTitle(null);
        handleTitlePress(turbolinksToolbar);
        if (navigationBarHidden) getSupportActionBar().hide();
    }

    @Override
    public void renderTitle() {
        WebView webView = TurbolinksSession.getDefault(this).getWebView();
        String title = route.getTitle() != null ? route.getTitle() : webView.getTitle();
        getSupportActionBar().setTitle(title);
        getSupportActionBar().setSubtitle(route.getSubtitle());
    }

    @Override
    @SuppressLint("RestrictedApi")
    public void renderActionIcon(Menu menu, MenuItem menuItem, Bundle icon) {
        if (icon == null) return;
        if (menu instanceof MenuBuilder) ((MenuBuilder) menu).setOptionalIconsVisible(true);
        Uri uri = Uri.parse(icon.getString("uri"));
        Drawable drawableIcon = Drawable.createFromPath(uri.getPath());
        menuItem.setIcon(drawableIcon);
    }

    @Override
    public RCTDeviceEventEmitter getEventEmitter() {
        return getReactInstanceManager().getCurrentReactContext().getJSModule(RCTDeviceEventEmitter.class);
    }

    @JavascriptInterface
    public void postMessage(String message) {
        getEventEmitter().emit("turbolinksMessage", message);
    }

    public void reloadSession() {
        TurbolinksSession.getDefault(this).getWebView().reload();
    }

    private void handleTitlePress(Toolbar toolbar) {
        final WebView webView = TurbolinksSession.getDefault(this).getWebView();
        toolbar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    WritableMap params = Arguments.createMap();
                    URL urlLocation = new URL(webView.getUrl());
                    params.putString("component", null);
                    params.putString("url", urlLocation.toString());
                    params.putString("path", urlLocation.getPath());
                    getEventEmitter().emit("turbolinksTitlePress", params);
                } catch (MalformedURLException e) {
                    Log.e(ReactConstants.TAG, "Error parsing URL. " + e.toString());
                }
            }
        });
    }

    private void handleVisitCompleted() {
        String javaScript = "document.documentElement.outerHTML";
        final WebView webView = TurbolinksSession.getDefault(this).getWebView();
        webView.evaluateJavascript(javaScript, new ValueCallback<String>() {
            public void onReceiveValue(String source) {
                try {
                    WritableMap params = Arguments.createMap();
                    URL urlLocation = new URL(webView.getUrl());
                    params.putString("url", urlLocation.toString());
                    params.putString("path", urlLocation.getPath());
                    params.putString("source", unescapeJava(source));
                    getEventEmitter().emit("turbolinksVisitCompleted", params);
                } catch (MalformedURLException e) {
                    Log.e(ReactConstants.TAG, "Error parsing URL. " + e.toString());
                }
            }
        });
    }

}
