package cn.zjy.patchupdatedemo;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import cn.zjy.patchupdate.PatchUpdate;

public class MainActivity extends Activity implements View.OnClickListener {

    private EditText mEtCheckUrl;
    private TextView mTvCheckedNewVersion;
    private TextView mTvCheckedChannel;
    private TextView mTvCheckedNewVersionUrl;
    private TextView mTvCheckedPatchUrl;
    private Button mBtnUpdate;
    private String mCurrentVersion;
    private String mChannel;
    private CheckResult mCheckResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCurrentVersion = PatchUpdate.getVersionName(this);
        TextView tvCurrentVersion = (TextView) findViewById(R.id.tv_current_version);
        tvCurrentVersion.setText("version: " + mCurrentVersion);
        TextView tvChannel = (TextView) findViewById(R.id.tv_channel);
        try {
            mChannel = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData.getString("CHANNEL");
            tvChannel.setText("channel: " + mChannel);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mEtCheckUrl = (EditText) findViewById(R.id.et_check_url);
        mTvCheckedNewVersion = (TextView) findViewById(R.id.tv_checked_new_version);
        mTvCheckedChannel = (TextView) findViewById(R.id.tv_checked_channel);
        mTvCheckedNewVersionUrl = (TextView) findViewById(R.id.tv_checked_new_version_url);
        mTvCheckedPatchUrl = (TextView) findViewById(R.id.tv_checked_patch_url);

        Button btnCheck = (Button) findViewById(R.id.btn_check);
        btnCheck.setOnClickListener(this);
        mBtnUpdate = (Button) findViewById(R.id.btn_update);
        mBtnUpdate.setOnClickListener(this);
    }

    private void update() {
        if (mCheckResult == null) return;

        PatchUpdate.update(this, mCheckResult.mPatchUrl, mCheckResult.mNewVersionUrl,
                mCheckResult.mNewVersion, "-", new PatchUpdate.PatchUpdateListener() {
                    @Override
                    public void onSuccess(String newApk) {
                        PatchUpdate.install(MainActivity.this, newApk);
                    }

                    @Override
                    public void onFailure(final int error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "update failed: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void check() {
        String url = mEtCheckUrl.getText().toString();
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(mCurrentVersion) || TextUtils.isEmpty(mChannel))
            return;

        url = url + "/check?version=" + mCurrentVersion + "&channel=" + mChannel;
        new AsyncTask<String, Void, CheckResult>() {
            @Override
            protected CheckResult doInBackground(String... params) {
                CheckResult checkResult = null;
                try {
                    URL url = new URL(params[0]);
                    URLConnection conn = url.openConnection();
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    Log.d("result", sb.toString());
                    JSONObject json = new JSONObject(sb.toString());
                    String newVersion = json.getString("new_version");
                    String channel = json.getString("channel");
                    String newVersionUrl = json.getString("new_version_url");
                    String patchUrl = json.getString("patch_url");
                    checkResult = new CheckResult();
                    checkResult.mNewVersion = newVersion;
                    checkResult.mChannel = channel;
                    checkResult.mNewVersionUrl = newVersionUrl;
                    checkResult.mPatchUrl = patchUrl;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return checkResult;
            }

            @Override
            protected void onPostExecute(CheckResult checkResult) {
                if (checkResult == null) return;
                mCheckResult = checkResult;
                mTvCheckedNewVersion.setText("new version: " + checkResult.mNewVersion);
                mTvCheckedChannel.setText("channel: " + checkResult.mChannel);
                mTvCheckedNewVersionUrl.setText("new version url:" + checkResult.mNewVersionUrl);
                mTvCheckedPatchUrl.setText("patch url:" + checkResult.mPatchUrl);
                if (checkResult.mNewVersion.compareTo(mCurrentVersion) > 0) {
                    mBtnUpdate.setEnabled(true);
                }
            }
        }.execute(url);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_check:
                check();
                break;
            case R.id.btn_update:
                update();
                break;
        }
    }

    private static class CheckResult {
        String mNewVersion;
        String mChannel;
        String mNewVersionUrl;
        String mPatchUrl;
    }
}
