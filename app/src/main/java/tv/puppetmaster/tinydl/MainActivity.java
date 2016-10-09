package tv.puppetmaster.tinydl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements TextView.OnEditorActionListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private PackageInstaller mPackageInstaller;
    private static final File DOWNLOADS_DIRECTORY = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS);

    private static ArrayAdapter<String> FILE_ADAPTER;
    private static final List<String> FILES = new ArrayList<>();
    private static final String SHORTENING_SERVICE = "http://tinyurl.com/";
    private static boolean mInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView filesList = ((ListView) findViewById(R.id.listview));
        FILE_ADAPTER = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                android.R.id.text1, FILES);
        filesList.setAdapter(FILE_ADAPTER);

        filesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                final String fileName = adapterView.getItemAtPosition(i).toString();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.delete) + ": " + fileName)
                        .setMessage(R.string.delete_message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mPackageInstaller.deleteFile(new File(DOWNLOADS_DIRECTORY, fileName));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setIcon(android.R.drawable.ic_dialog_alert).show();
                return true;
            }
        });

        filesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final String fileName = adapterView.getItemAtPosition(i).toString();
                for (File f : DOWNLOADS_DIRECTORY.listFiles()) {
                    if (f.getName().equals(fileName)) {
                        mPackageInstaller.install(f);
                        return;
                    }
                }
                Toast.makeText(MainActivity.this, R.string.error_finding_file, Toast.LENGTH_LONG).show();
            }
        });

        ((EditText) findViewById(R.id.tag)).setFilters(new InputFilter[] { new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char character = source.charAt(i);
                    if (Character.isWhitespace(character)) {
                        return "";
                    } else if (character == '#') {
                        return "";
                    }
                }
                return null;
            }
        }});

        ((TextView) findViewById(R.id.directory)).setText(DOWNLOADS_DIRECTORY.toString());
        ((EditText) findViewById(R.id.tag)).setOnEditorActionListener(this);
        findViewById(R.id.download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wget();
            }
        });

        mPackageInstaller = PackageInstaller.initialize(this);
        mPackageInstaller.addListener(new PackageInstaller.DownloadListener() {
            @Override
            public void onApkDownloaded(File downloadedApkFile) {
                ls_ltr_apks();
                mPackageInstaller.install(downloadedApkFile);
            }

            @Override
            public void onApkDownloadedNougat(final File downloadedApkFile) {
                ls_ltr_apks();
                new Handler(Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                mPackageInstaller.install(downloadedApkFile);
                            }
                        }, 1000 * 3);
            }

            @Override
            public void onFileDeleted(File deletedApkFile, boolean wasSuccessful) {
                if (wasSuccessful) {
                    ls_ltr_apks();
                }
            }

            @Override
            public void onProgressStarted() {
                progressStart();
            }

            @Override
            public void onProgressEnded() {
                progressStop();
            }
        });

        Snackbar.make(findViewById(R.id.root_view), R.string.welcome,
                Snackbar.LENGTH_INDEFINITE).show();
    }

    @Override
    public void onResume() {
        ls_ltr_apks();
        super.onResume();
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_NONE ||
                (actionId == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
            wget();
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!mInProgress) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, R.string.warning_download_in_progress, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        mPackageInstaller.destroy();
        super.onDestroy();
    }

    /**
     * Update the directory listing in reverse chronological order i.e. latest at the top
     */
    public void ls_ltr_apks() {
        if (DOWNLOADS_DIRECTORY != null && DOWNLOADS_DIRECTORY.listFiles() != null) {
            FILES.clear();

            /* VIA SOF: http://stackoverflow.com/a/4248059/6716223 */

            // Obtain the array of (file, timestamp) pairs.
            File[] files = DOWNLOADS_DIRECTORY.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".apk");
                }
            });
            Pair[] pairs = new Pair[files.length];
            for (int i = 0; i < files.length; i++)
                pairs[i] = new Pair(files[i]);

            // Sort them by timestamp.
            Arrays.sort(pairs, Collections.reverseOrder());

            // Take the sorted pairs and extract only the file part, discarding the timestamp.
            for (int i = 0; i < files.length; i++)
                files[i] = pairs[i].f;

            /* END VIA SOF */

            for (File f : files) {
                FILES.add(f.getName());
            }
            FILE_ADAPTER.notifyDataSetChanged();
        }
    }

    public void wget() {
        if (!mPackageInstaller.chmod()) {
            return;
        }
        String tag = ((EditText) findViewById(R.id.tag)).getText().toString().trim();
        if (tag.isEmpty()) {
            Toast.makeText(MainActivity.this, R.string.warning_invalid_tag, Toast.LENGTH_LONG).show();
        } else {
            mPackageInstaller.wget(SHORTENING_SERVICE + tag);
        }
    }

    public void progressStart() {
        mInProgress = true;
        findViewById(R.id.form_thinking).setVisibility(View.VISIBLE);
        findViewById(R.id.tag).setEnabled(false);
        findViewById(R.id.download).setEnabled(false);
        findViewById(R.id.listview).setEnabled(false);
    }
    
    public void progressStop() {
        findViewById(R.id.form_thinking).setVisibility(View.GONE);
        findViewById(R.id.tag).setEnabled(true);
        findViewById(R.id.download).setEnabled(true);
        findViewById(R.id.listview).setEnabled(true);
        mInProgress = false;
    }

    class Pair implements Comparable {
        long t;
        File f;

        Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(@NonNull Object o) {
            long u = ((Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    }
}