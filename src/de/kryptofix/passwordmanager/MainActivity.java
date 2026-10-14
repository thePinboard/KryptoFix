package de.kryptofix.passwordmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    static final int BG = 0xFF0D1020, SURF = 0xFF171B30, PRI = 0xFF6D4AFF, DEEP = 0xFF35206F, LILAC = 0xFFC7B8FF,
            BLUE = 0xFF3997FF, TXT = 0xFFF4F2FF, TXT2 = 0xFFB8B7D3, DANGER = 0xFFFF647C, OK = 0xFF47D7A0,
            WARN = 0xFFFFC66D, LINE = 0xFF2C3150;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable clipClear;

    private FrameLayout host;
    private SharedPreferences prefs;
    private String screen = "";
    private int tab = 0;
    private String searchQuery = "";
    private Vault.Entry editing;

    // ------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("kf", MODE_PRIVATE);
        host = new FrameLayout(this);
        host.setBackgroundColor(BG);
        host.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets i) {
                v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(),
                        i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom());
                return i;
            }
        });
        setContentView(host);
    }

    @Override
    protected void onStart() {
        super.onStart();
        route();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && Vault.unlocked()) {
            Vault.lock();
            editing = null;
            screen = "";
            host.removeAllViews();
        }
    }

    @Override
    public void onBackPressed() {
        if (screen.equals("editor") || screen.equals("changepw")) {
            editing = null;
            showMain(tab);
        } else if (screen.equals("main") && tab != 0) {
            showMain(0);
        } else {
            super.onBackPressed();
        }
    }

    private void route() {
        if (!Vault.exists(this)) {
            if (!screen.equals("setup")) showSetup();
        } else if (!Vault.unlocked()) {
            if (!screen.equals("unlock")) showUnlock();
        } else if (screen.isEmpty()) {
            showMain(tab);
        }
    }

    // ------------------------------------------------------------ UI-Helfer

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable box(int color, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    EditText input(String hint, boolean secret, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(TXT2);
        e.setTextColor(TXT);
        e.setTextSize(16);
        e.setBackground(box(SURF, 12, LINE));
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.setMinHeight(dp(52));
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return e;
    }

    Button btn(String label, int fill, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(52));
        b.setBackground(fill == 0 ? box(Color.TRANSPARENT, 14, LINE) : box(fill, 14, 0));
        b.setOnClickListener(l);
        return b;
    }

    Button primaryBtn(String label, View.OnClickListener l) {
        Button b = btn(label, PRI, Color.WHITE, l);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PRI, BLUE});
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        return b;
    }

    LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(top);
        return p;
    }

    LinearLayout column(int pad) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        return l;
    }

    void setScreen(String name, View v) {
        screen = name;
        host.removeAllViews();
        host.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    View shield(int sizeDp) {
        TextView t = text("\uD83D\uDEE1", 34, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{DEEP, PRI, BLUE});
        g.setShape(GradientDrawable.OVAL);
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return t;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    char[] chars(EditText e) {
        int n = e.length();
        char[] a = new char[n];
        e.getText().getChars(0, n, a, 0);
        return a;
    }

    String strengthLabel(int s) {
        switch (s) {
            case 0: return "\u26A0 Sehr schwach";
            case 1: return "\u26A0 Schwach";
            case 2: return "\u25CF Mittel";
            case 3: return "\u2714 Gut";
            default: return "\u2714 Stark";
        }
    }

    int strengthColor(int s) { return s <= 1 ? DANGER : s == 2 ? WARN : OK; }

    // ------------------------------------------------------------ Einrichtung / Entsperren

    void showSetup() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout l = column(24);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(l);
        l.addView(shield(88));
        TextView t = text("KryptoFix", 28, TXT, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t, lp(16));
        TextView s = text("Tresor erstellen", 16, LILAC, false);
        s.setGravity(Gravity.CENTER);
        l.addView(s, lp(2));
        TextView info = text("Lege ein Hauptpasswort fest. Es kann nicht wiederhergestellt werden \u2013 "
                + "ohne dieses Passwort sind deine Daten verloren.", 14, TXT2, false);
        info.setGravity(Gravity.CENTER);
        l.addView(info, lp(16));
        final EditText p1 = input("Hauptpasswort (mind. 10 Zeichen)", true, false);
        final EditText p2 = input("Hauptpasswort wiederholen", true, false);
        final TextView str = text("", 14, TXT2, false);
        final TextView err = text("", 14, DANGER, false);
        l.addView(p1, lp(24));
        l.addView(str, lp(6));
        l.addView(p2, lp(12));
        l.addView(err, lp(8));
        p1.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                int sc = Vault.strength(e.toString());
                str.setText(e.length() == 0 ? "" : "Stärke: " + strengthLabel(sc));
                str.setTextColor(strengthColor(sc));
            }
        });
        final Button go = primaryBtn("Tresor erstellen", null);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String a = p1.getText().toString(), b = p2.getText().toString();
                if (a.length() < 10) { err.setText("\u26A0 Das Hauptpasswort muss mindestens 10 Zeichen lang sein."); return; }
                if (!a.equals(b)) { err.setText("\u26A0 Die Passwörter stimmen nicht überein."); return; }
                err.setText("");
                go.setEnabled(false);
                go.setText("Schlüssel wird abgeleitet …");
                final char[] pw = chars(p1);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String problem = null;
                        try { Vault.create(MainActivity.this, pw); } catch (Exception ex) { problem = "Fehler: " + ex.getMessage(); }
                        java.util.Arrays.fill(pw, '\0');
                        final String pr = problem;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (pr != null) { err.setText(pr); go.setEnabled(true); go.setText("Tresor erstellen"); }
                                else { tab = 0; showMain(0); }
                            }
                        });
                    }
                }).start();
            }
        });
        l.addView(go, lp(8));
        setScreen("setup", sv);
    }

    void showUnlock() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout l = column(24);
        l.setGravity(Gravity.CENTER);
        sv.addView(l);
        l.addView(shield(96));
        TextView t = text("KryptoFix", 28, TXT, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t, lp(16));
        TextView s = text("\uD83D\uDD12 Tresor gesperrt", 16, LILAC, false);
        s.setGravity(Gravity.CENTER);
        l.addView(s, lp(2));
        final EditText pw = input("Hauptpasswort", true, false);
        final TextView err = text("", 14, DANGER, false);
        l.addView(pw, lp(28));
        l.addView(err, lp(8));
        final Button go = primaryBtn("Entsperren", null);
        View.OnClickListener unlock = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pw.length() == 0) return;
                go.setEnabled(false);
                go.setText("Wird geprüft …");
                err.setText("");
                final char[] c = chars(pw);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = Vault.unlock(MainActivity.this, c);
                        java.util.Arrays.fill(c, '\0');
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) { showMain(tab); }
                                else {
                                    err.setText("\u26A0 Falsches Hauptpasswort oder beschädigte Tresordatei.");
                                    pw.setText("");
                                    go.setEnabled(true);
                                    go.setText("Entsperren");
                                }
                            }
                        });
                    }
                }).start();
            }
        };
        go.setOnClickListener(unlock);
        l.addView(go, lp(8));
        setScreen("unlock", sv);
    }

    // ------------------------------------------------------------ Hauptbereich

    void showMain(int which) {
        tab = which;
        editing = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        switch (which) {
            case 0: buildVaultTab(content); break;
            case 1: buildSearchTab(content); break;
            case 2: buildCheckTab(content); break;
            default: buildSettingsTab(content);
        }
        root.addView(navBar());
        setScreen("main", root);
    }

    View navBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(SURF);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        String[] names = {"Tresor", "Suche", "Check", "Einstellungen"};
        String[] icons = {"\uD83D\uDD10", "\uD83D\uDD0D", "\uD83D\uDEE1", "\u2699"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(6), 0, dp(6));
            boolean sel = i == tab;
            if (sel) item.setBackground(box(DEEP, 16, 0));
            TextView ic = text(icons[i], 20, sel ? TXT : TXT2, false);
            ic.setGravity(Gravity.CENTER);
            TextView nm = text(names[i], 11, sel ? TXT : TXT2, sel);
            nm.setGravity(Gravity.CENTER);
            item.addView(ic);
            item.addView(nm);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { showMain(idx); }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            p.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(item, p);
        }
        return bar;
    }

    View header(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.addView(text(title, 26, TXT, true));
        if (sub != null) h.addView(text(sub, 14, TXT2, false), lp(2));
        return h;
    }

    View entryCard(final Vault.Entry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(box(SURF, 16, LINE));
        String label = e.title.isEmpty() ? "?" : e.title.substring(0, 1).toUpperCase(Locale.ROOT);
        TextView badge = text(label, 18, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRI, BLUE});
        g.setShape(GradientDrawable.OVAL);
        badge.setBackground(g);
        row.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(e.title.isEmpty() ? "(ohne Titel)" : e.title, 16, TXT, true));
        String sub = e.user.isEmpty() ? e.url : e.user;
        if (!sub.isEmpty()) tx.addView(text(sub, 13, TXT2, false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.leftMargin = dp(12);
        row.addView(tx, tp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showEditor(e); }
        });
        return row;
    }

    List<Vault.Entry> sorted(List<Vault.Entry> in) {
        List<Vault.Entry> l = new ArrayList<>(in);
        Collections.sort(l, new Comparator<Vault.Entry>() {
            @Override
            public int compare(Vault.Entry a, Vault.Entry b) { return a.title.compareToIgnoreCase(b.title); }
        });
        return l;
    }

    void buildVaultTab(FrameLayout content) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = column(16);
        sv.addView(l);
        l.addView(header("Tresor", Vault.entries.size() + (Vault.entries.size() == 1 ? " Eintrag" : " Einträge")
                + " \u00B7 lokal verschlüsselt"));
        if (Vault.entries.isEmpty()) {
            TextView empty = text("Noch keine Einträge.\nTippe auf \u201E+\u201C, um deinen ersten Zugang zu speichern.",
                    15, TXT2, false);
            empty.setGravity(Gravity.CENTER);
            l.addView(empty, lp(60));
        }
        for (Vault.Entry e : sorted(Vault.entries)) l.addView(entryCard(e), lp(10));
        View spacer = new View(this);
        l.addView(spacer, new LinearLayout.LayoutParams(MATCH, dp(90)));
        content.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        Button fab = primaryBtn("+", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showEditor(null); }
        });
        fab.setTextSize(26);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRI, BLUE});
        g.setShape(GradientDrawable.OVAL);
        fab.setBackground(g);
        fab.setContentDescription("Neuen Eintrag hinzufügen");
        fab.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(20), dp(20));
        content.addView(fab, fp);
    }

    void buildSearchTab(FrameLayout content) {
        LinearLayout l = column(16);
        l.addView(header("Suche", "Titel, Benutzer, URL und Notizen"));
        final EditText q = input("Suchen …", false, false);
        q.setText(searchQuery);
        l.addView(q, lp(12));
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(results);
        l.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        q.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                searchQuery = e.toString();
                renderResults(results);
            }
        });
        renderResults(results);
        content.addView(l, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    void renderResults(LinearLayout results) {
        results.removeAllViews();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            TextView t = text("Gib einen Suchbegriff ein.", 15, TXT2, false);
            t.setGravity(Gravity.CENTER);
            results.addView(t, lp(40));
            return;
        }
        int n = 0;
        for (Vault.Entry e : sorted(Vault.entries)) {
            String hay = (e.title + "\n" + e.user + "\n" + e.url + "\n" + e.notes).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) { results.addView(entryCard(e), lp(10)); n++; }
        }
        if (n == 0) {
            TextView t = text("Keine Treffer.", 15, TXT2, false);
            t.setGravity(Gravity.CENTER);
            results.addView(t, lp(40));
        }
    }

    void buildCheckTab(FrameLayout content) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = column(16);
        sv.addView(l);
        l.addView(header("Sicherheitscheck", "Lokale Analyse \u2013 nichts verlässt dein Gerät"));
        List<Vault.Entry> weak = new ArrayList<>(), empty = new ArrayList<>(), reused = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        for (Vault.Entry e : Vault.entries) if (!e.pw.isEmpty()) {
            Integer c = count.get(e.pw);
            count.put(e.pw, c == null ? 1 : c + 1);
        }
        for (Vault.Entry e : Vault.entries) {
            if (e.pw.isEmpty()) empty.add(e);
            else {
                if (Vault.strength(e.pw) <= 1) weak.add(e);
                if (count.get(e.pw) > 1) reused.add(e);
            }
        }
        int issues = weak.size() + empty.size() + reused.size();
        LinearLayout sum = column(16);
        sum.setBackground(box(SURF, 16, issues == 0 ? OK : WARN));
        sum.addView(text(issues == 0 ? "\u2714 Keine Auffälligkeiten" : "\u26A0 " + issues + " Hinweis(e)", 18,
                issues == 0 ? OK : WARN, true));
        sum.addView(text(Vault.entries.size() + " Einträge geprüft", 14, TXT2, false), lp(4));
        l.addView(sum, lp(16));
        addCheckGroup(l, "\u26A0 Schwache Passwörter", weak);
        addCheckGroup(l, "\u26A0 Mehrfach verwendete Passwörter", reused);
        addCheckGroup(l, "\u26A0 Einträge ohne Passwort", empty);
        content.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    void addCheckGroup(LinearLayout l, String title, List<Vault.Entry> list) {
        if (list.isEmpty()) return;
        l.addView(text(title + " (" + list.size() + ")", 16, LILAC, true), lp(20));
        for (Vault.Entry e : sorted(list)) l.addView(entryCard(e), lp(8));
    }

    void buildSettingsTab(FrameLayout content) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = column(16);
        sv.addView(l);
        l.addView(header("Einstellungen", null));
        int secs = prefs.getInt("clip", 30);
        l.addView(text("Zwischenablage", 14, LILAC, true), lp(20));
        final Button clipBtn = btn("Automatisch löschen nach " + secs + " s", 0, TXT, null);
        clipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = prefs.getInt("clip", 30);
                int nxt = cur == 15 ? 30 : cur == 30 ? 60 : cur == 60 ? 120 : 15;
                prefs.edit().putInt("clip", nxt).apply();
                clipBtn.setText("Automatisch löschen nach " + nxt + " s");
            }
        });
        l.addView(clipBtn, lp(8));
        l.addView(text("Sperre", 14, LILAC, true), lp(20));
        l.addView(text("Der Tresor sperrt sich sofort, wenn du die App verlässt.", 14, TXT2, false), lp(6));
        l.addView(primaryBtn("Jetzt sperren", new View.OnClickListener() {
            @Override
            public void onClick(View v) { Vault.lock(); screen = ""; showUnlock(); }
        }), lp(8));
        l.addView(text("Konto", 14, LILAC, true), lp(20));
        l.addView(btn("Hauptpasswort ändern", 0, TXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) { showChangePw(); }
        }), lp(8));
        l.addView(btn("Tresor unwiderruflich löschen", 0, DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) { confirmWipe(); }
        }), lp(8));
        l.addView(text("Über diese App", 14, LILAC, true), lp(20));
        LinearLayout info = column(14);
        info.setBackground(box(SURF, 16, LINE));
        info.addView(text("\u2022 Verschlüsselung: AES-256-GCM\n\u2022 Schlüsselableitung: PBKDF2-HMAC-SHA256, "
                + String.format(Locale.GERMANY, "%,d", Vault.ITERATIONS) + " Iterationen\n"
                + "\u2022 Speicherung ausschließlich lokal, keine Internet-Berechtigung\n"
                + "\u2022 Kein Cloud-Backup, keine Werbung, kein Tracking\n"
                + "\u2022 Bildschirmaufnahmen sind blockiert (FLAG_SECURE)\n"
                + "\u2022 Format: eigenes verschlüsseltes Format (KFX1), noch nicht KDBX-kompatibel\n"
                + "\u2022 Version 1.0.0", 13, TXT2, false));
        l.addView(info, lp(8));
        content.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    void confirmWipe() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Tresor löschen?")
                .setMessage("Alle Einträge werden unwiderruflich gelöscht. Dies kann nicht rückgängig gemacht werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Endgültig löschen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Vault.wipe(MainActivity.this);
                        screen = "";
                        showSetup();
                    }
                }).show();
    }

    void showChangePw() {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = column(20);
        sv.addView(l);
        l.addView(header("Hauptpasswort ändern", "Der Tresor wird mit neuem Salt neu verschlüsselt."));
        final EditText p1 = input("Neues Hauptpasswort (mind. 10 Zeichen)", true, false);
        final EditText p2 = input("Neues Hauptpasswort wiederholen", true, false);
        final TextView err = text("", 14, DANGER, false);
        l.addView(p1, lp(20));
        l.addView(p2, lp(12));
        l.addView(err, lp(8));
        final Button go = primaryBtn("Passwort ändern", null);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String a = p1.getText().toString(), b = p2.getText().toString();
                if (a.length() < 10) { err.setText("\u26A0 Mindestens 10 Zeichen erforderlich."); return; }
                if (!a.equals(b)) { err.setText("\u26A0 Die Passwörter stimmen nicht überein."); return; }
                go.setEnabled(false);
                go.setText("Wird neu verschlüsselt …");
                final char[] pw = chars(p1);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String problem = null;
                        try { Vault.rekey(MainActivity.this, pw); } catch (Exception ex) { problem = "Fehler: " + ex.getMessage(); }
                        java.util.Arrays.fill(pw, '\0');
                        final String pr = problem;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (pr != null) { err.setText(pr); go.setEnabled(true); go.setText("Passwort ändern"); }
                                else { toast("Hauptpasswort geändert"); showMain(3); }
                            }
                        });
                    }
                }).start();
            }
        });
        l.addView(go, lp(8));
        l.addView(btn("Abbrechen", 0, TXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) { showMain(3); }
        }), lp(8));
        setScreen("changepw", sv);
    }

    // ------------------------------------------------------------ Editor

    void showEditor(final Vault.Entry existing) {
        final boolean isNew = existing == null;
        final Vault.Entry e = isNew ? new Vault.Entry() : existing;
        editing = e;
        ScrollView sv = new ScrollView(this);
        LinearLayout l = column(20);
        sv.addView(l);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("\u2190", 26, TXT, true);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setContentDescription("Zurück");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showMain(tab); }
        });
        top.addView(back);
        top.addView(text(isNew ? "Neuer Eintrag" : "Eintrag bearbeiten", 22, TXT, true));
        l.addView(top);

        final EditText title = input("Titel", false, false);
        final EditText user = input("Benutzername / E-Mail", false, false);
        final EditText url = input("URL", false, false);
        final EditText pw = input("Passwort", true, false);
        final EditText notes = input("Notizen", false, true);
        title.setText(e.title);
        user.setText(e.user);
        url.setText(e.url);
        pw.setText(e.pw);
        notes.setText(e.notes);

        l.addView(label("Titel"), lp(16));
        l.addView(title, lp(4));
        l.addView(label("Benutzername"), lp(12));
        l.addView(user, lp(4));
        l.addView(label("URL"), lp(12));
        l.addView(url, lp(4));
        l.addView(label("Passwort"), lp(12));
        l.addView(pw, lp(4));
        final TextView str = text("", 13, TXT2, false);
        l.addView(str, lp(6));
        pw.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                int sc = Vault.strength(s.toString());
                str.setText(s.length() == 0 ? "" : "Stärke: " + strengthLabel(sc));
                str.setTextColor(strengthColor(sc));
            }
        });
        pw.setText(e.pw);

        LinearLayout pwRow = new LinearLayout(this);
        final boolean[] shown = {false};
        final Button show = btn("Anzeigen", 0, TXT, null);
        show.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shown[0] = !shown[0];
                pw.setInputType(InputType.TYPE_CLASS_TEXT | (shown[0]
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                pw.setSelection(pw.length());
                show.setText(shown[0] ? "Verbergen" : "Anzeigen");
            }
        });
        Button gen = btn("Generieren", 0, LILAC, new View.OnClickListener() {
            @Override
            public void onClick(View v) { pw.setText(Vault.generate(20)); }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, WRAP, 1f);
        half.rightMargin = dp(6);
        pwRow.addView(show, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        half2.leftMargin = dp(6);
        pwRow.addView(gen, half2);
        l.addView(pwRow, lp(6));

        l.addView(label("Notizen"), lp(12));
        l.addView(notes, lp(4));

        LinearLayout copyRow = new LinearLayout(this);
        Button cu = btn("Benutzer kopieren", 0, TXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) { copy("Benutzername", user.getText().toString(), false); }
        });
        Button cp = btn("Passwort kopieren", 0, TXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) { copy("Passwort", pw.getText().toString(), true); }
        });
        copyRow.addView(cu, half);
        copyRow.addView(cp, half2);
        l.addView(copyRow, lp(16));

        final TextView err = text("", 14, DANGER, false);
        l.addView(err, lp(8));
        l.addView(primaryBtn("Speichern", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = title.getText().toString().trim();
                if (t.isEmpty()) { err.setText("\u26A0 Bitte einen Titel angeben."); return; }
                e.title = t;
                e.user = user.getText().toString().trim();
                e.url = url.getText().toString().trim();
                e.pw = pw.getText().toString();
                e.notes = notes.getText().toString();
                e.modified = System.currentTimeMillis();
                if (isNew) Vault.entries.add(e);
                try {
                    Vault.save(MainActivity.this);
                    toast("Gespeichert");
                    showMain(tab);
                } catch (Exception ex) {
                    if (isNew) Vault.entries.remove(e);
                    err.setText("\u26A0 Speichern fehlgeschlagen: " + ex.getMessage());
                }
            }
        }), lp(4));
        if (!isNew) {
            l.addView(btn("Eintrag löschen", 0, DANGER, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Eintrag löschen?")
                            .setMessage("„" + e.title + "“ wird gelöscht.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Löschen", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    Vault.entries.remove(e);
                                    try { Vault.save(MainActivity.this); } catch (Exception ex) { toast("Fehler: " + ex.getMessage()); }
                                    showMain(tab);
                                }
                            }).show();
                }
            }), lp(8));
        }
        setScreen("editor", sv);
    }

    TextView label(String s) { return text(s, 13, LILAC, true); }

    // ------------------------------------------------------------ Zwischenablage

    void copy(String what, final String value, boolean sensitive) {
        if (value.isEmpty()) { toast("Nichts zu kopieren"); return; }
        final ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData cd = ClipData.newPlainText("KryptoFix", value);
        if (sensitive && Build.VERSION.SDK_INT >= 24) {
            PersistableBundle pb = new PersistableBundle();
            pb.putBoolean("android.content.extra.IS_SENSITIVE", true);
            cd.getDescription().setExtras(pb);
        }
        cm.setPrimaryClip(cd);
        final int secs = prefs.getInt("clip", 30);
        toast(what + " kopiert \u2013 wird in " + secs + " s gelöscht");
        if (clipClear != null) H.removeCallbacks(clipClear);
        final Context app = getApplicationContext();
        clipClear = new Runnable() {
            @Override
            public void run() {
                try {
                    ClipboardManager m = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData cur = m.getPrimaryClip();
                    boolean ours = cur != null && cur.getItemCount() > 0 && cur.getItemAt(0).getText() != null
                            && value.contentEquals(cur.getItemAt(0).getText());
                    if (ours) {
                        if (Build.VERSION.SDK_INT >= 28) m.clearPrimaryClip();
                        else m.setPrimaryClip(ClipData.newPlainText("", ""));
                    }
                } catch (Exception ignored) { }
            }
        };
        H.postDelayed(clipClear, secs * 1000L);
    }

    abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
    }
}
