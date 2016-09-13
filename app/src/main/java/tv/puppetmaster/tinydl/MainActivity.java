package tv.puppetmaster.tinydl;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
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
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends Activity implements TextView.OnEditorActionListener {

    private static final String TAG = "MainActivity";

    private static final String SHORTENING_SERVICE = "http://tinyurl.com/";
    private static final File DOWNLOADS_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);;
    private static final int REQUEST_READWRITE_STORAGE = 0;
    private static final ArrayList<String> FILES = new ArrayList<>();

    private static ArrayAdapter<String> FILE_ADAPTER;
    private static DownloadManager DOWNLOAD_MANAGER;

    private BroadcastReceiver mDownloadCompleteReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            Bundle extras = intent.getExtras();
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
            Cursor c = DOWNLOAD_MANAGER.query(q);

            if (c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (uri != null && uri.endsWith(".apk")) {
                        exec(Uri.parse(uri));
                        Toast.makeText(ctxt, R.string.info_download_complete, Toast.LENGTH_LONG).show();
                    } else if (uri != null) {
                        boolean success = new File(uri).delete();
                        Toast.makeText(ctxt, ctxt.getString(R.string.warning_invalid_tag) + ": " + (success ? "Removed" : "Unremoved"), Toast.LENGTH_LONG).show();
                    }
                } else {
                    int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                    Toast.makeText(ctxt, ctxt.getString(R.string.warning_invalid_tag) + ": " + reason, Toast.LENGTH_LONG).show();
                }
            }
            c.close();
            ls_ltr_apks();
            progressStop();
        }
    };
    private boolean mInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(mDownloadCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        DOWNLOAD_MANAGER = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        FILE_ADAPTER = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, FILES);

        ListView filesList = ((ListView) findViewById(R.id.listview));
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
                                new DeleteFile().execute(fileName);
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
                        exec(Uri.fromFile(f));
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

        chmod();

        Snackbar.make(findViewById(R.id.root_view), R.string.welcome, Snackbar.LENGTH_INDEFINITE).show();
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
        unregisterReceiver(mDownloadCompleteReceiver);
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
        if (!chmod()) {
            return;
        }
        progressStart();
        String tag = ((EditText) findViewById(R.id.tag)).getText().toString().trim();
        if (tag.isEmpty()) {
            progressStop();
            Toast.makeText(MainActivity.this, R.string.warning_invalid_tag, Toast.LENGTH_LONG).show();
        } else {
            new DownloadFile().execute(SHORTENING_SERVICE + tag);
        }
    }

    public boolean chmod() {
        int permissionCheck1 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck1 != PackageManager.PERMISSION_GRANTED || permissionCheck2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    REQUEST_READWRITE_STORAGE);
            return false;
        } else {
            return true;
        }
    }

    public void exec(Uri uri) {
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimetype)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Start activity failed: " + uri, ex);
            Toast.makeText(MainActivity.this, R.string.error_starting_intent, Toast.LENGTH_LONG).show();
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

    private class DownloadFile extends AsyncTask<String, Void, Integer> {

        @Override
        protected Integer doInBackground(String... urls) {
            String downloadUri = null;
            try {
                URLConnection con = new URL(urls[0]).openConnection();
                con.getHeaderFields();
                downloadUri = con.getURL().toString();
            } catch (Exception ex) {
                Log.e("DownloadFile", "Connection error", ex);
            }
            if (downloadUri == null) {
                return R.string.error_download_failed;
            }
            String fileName = downloadUri.substring(downloadUri.lastIndexOf("/") + 1);
            final String downloadedFileName = fileName.split("\\?")[0].trim();
            if (downloadedFileName.isEmpty() || !downloadedFileName.endsWith(".apk")) {
                return R.string.warning_invalid_tag;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUri));
            request.setTitle(getString(R.string.app_name) + ": " + downloadedFileName);
            request.setDescription(getString(R.string.directory) + ": " + DOWNLOADS_DIRECTORY.toString());
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, downloadedFileName);
            final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);

            return -1;
        }

        @Override
        protected void onPostExecute(Integer failureMessage) {
            if (failureMessage >= 0) {
                progressStop();
                Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
            }
        }
    }

    private class DeleteFile extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... filenames) {
            return new File(DOWNLOADS_DIRECTORY, filenames[0]).delete();
        }

        @Override
        protected void onPostExecute(Boolean deleted) {
            if (!deleted) {
                Toast.makeText(MainActivity.this, R.string.error_deleting_file, Toast.LENGTH_LONG).show();
            } else {
                ls_ltr_apks();
            }
        }
    }

    class Pair implements Comparable {
        public long t;
        public File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    };
}